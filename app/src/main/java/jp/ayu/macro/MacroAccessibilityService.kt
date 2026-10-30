package jp.ayu.macro

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.animation.ValueAnimator
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 記録と再生を担当するユーザー補助サービス。
 * - 画面内容 (AccessibilityNodeInfo) は取得しない (canRetrieveWindowContent=false)。
 * - アクセシビリティイベントは購読しない。
 * - オーバーレイは TYPE_ACCESSIBILITY_OVERLAY なので「他のアプリの上に表示」権限は不要。
 */
class MacroAccessibilityService : AccessibilityService() {

    enum class State { IDLE, RECORDING, PLAYING }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private lateinit var store: MacroStore

    var state = State.IDLE
        private set

    // フローティングパネル（展開: ピル型のコントロール / 折りたたみ: 48dp の丸いバブル）
    private var panel: DragFrame? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var collapsed = false
    private var bubble: BubbleView? = null
    private var statusText: TextView? = null
    private var statusDots: DotMatrixView? = null
    private var recButton: ImageButton? = null
    private var playButton: ImageButton? = null
    private var stopButton: ImageButton? = null
    private var lastUiMode = 0
    private var pill: PillViews? = null
    private var anim: ValueAnimator? = null

    // 記録
    private var capture: CaptureView? = null
    private var captureParams: WindowManager.LayoutParams? = null
    private val recordedSteps = ArrayList<Step>()
    private var recordStartUptime = 0L
    private var lastDeliveredStart = 0L
    private var lastDeliveredDuration = 0L
    private var recScreen = Point()

    // 再生
    private var playing: Macro? = null
    private var loopIndex = 0
    private var stepIndex = 0
    private var playToken = 0
    private var cancelledCount = 0

    private var nameDialog: View? = null
    private var lastLocales: android.os.LocaleList? = null

    // Android 12 以下: アプリ内で選んだ言語で文言を出す（13+ はシステムが反映）
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppLocales.wrap(newBase))
    }

    /** Android 12 以下でアプリ内の言語を変えたとき: 次に作るボタンから新しい言語に */
    fun onAppLocaleChanged() {
        Handler(Looper.getMainLooper()).post { if (panel != null) buildPanelContent() }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        store = MacroStore(this)
        instance = this
        // フローティングは自動で出さない（アプリの更新・再起動・サービスの再接続のときも）。
        // アプリの「フローティングボタン > 表示」を押したときだけ showPanel() で出す。
        // 表示中かどうかは保存せず、サービスが接続し直したら常に非表示から始まる。
        notifyChanged()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* 使わない */ }

    override fun onInterrupt() { stopAll() }

    override fun onDestroy() {
        stopAll()
        removeView(panel); panel = null
        removeView(nameDialog); nameDialog = null
        if (instance === this) instance = null
        notifyChanged()
        super.onDestroy()
    }

    // ---------------- 画面サイズ ----------------
    private fun screenSize(): Point {
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.currentWindowMetrics.bounds
            Point(b.width(), b.height())
        } else {
            val p = Point()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealSize(p)
            p
        }
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun overlayParams(w: Int, h: Int, flags: Int) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT,
    ).apply {
        if (Build.VERSION.SDK_INT >= 30) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= 28) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun removeView(v: View?) {
        if (v != null && v.isAttachedToWindow) runCatching { wm.removeView(v) }
    }

    // ---------------- フローティングパネル ----------------
    private val prefs by lazy { getSharedPreferences("panel", MODE_PRIVATE) }
    private val uiDefault by lazy { Ui(this) }
    /** v0.9: Android 11 以下でアプリ内のテーマを切り替えたとき、パネルの色を作り直すための差し替え */
    private var uiOverride: Ui? = null
    private val ui: Ui get() = uiOverride ?: uiDefault

    @SuppressLint("SetTextI18n")
    fun showPanel() {
        if (panel != null) { bringPanelToFront(); return }
        lastUiMode = resources.configuration.uiMode
        lastLocales = resources.configuration.locales
        collapsed = prefs.getBoolean("collapsed", false)
        val lp = overlayParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("x", dp(16)); y = prefs.getInt("y", dp(160))
        }
        var startX = 0; var startY = 0
        lateinit var frame: DragFrame
        frame = DragFrame(this, onMove = { dx, dy, start ->
            if (anim != null) Unit  // 開閉アニメーション中はドラッグしない
            else if (start) { startX = lp.x; startY = lp.y } else {
                val s = screenSize()
                lp.x = PanelMath.clamp(startX + dx, frame.width, s.x)
                lp.y = PanelMath.clamp(startY + dy, frame.height, s.y)
                runCatching { wm.updateViewLayout(frame, lp) }
            }
        }, onEnd = { savePanelPos() })
        // 影（elevation）が切れないよう外側に余白を取る
        frame.setPadding(dp(8), dp(8), dp(8), dp(8)); frame.clipToPadding = false
        runCatching { wm.addView(frame, lp) }.onFailure {
            toast(getString(R.string.toast_panel_failed, it.message ?: ""))
            return
        }
        panel = frame; panelParams = lp
        buildPanelContent()
    }

    private fun savePanelPos() {
        val lp = panelParams ?: return
        prefs.edit().putInt("x", lp.x).putInt("y", lp.y).putBoolean("collapsed", collapsed).apply()
    }

    /** 展開/折りたたみに合わせて中身を作り直す（アニメーションなし） */
    private fun buildPanelContent() {
        val frame = panel ?: return
        if (Build.VERSION.SDK_INT < 31) uiOverride = Ui(AppLocales.wrap(applicationContext))
        anim?.cancel(); anim = null
        frame.removeAllViews()
        clearPanelRefs()
        if (collapsed) {
            frame.addView(buildBubble())
        } else {
            val box = buildPill(leftward = false)
            frame.addView(box)
            pill?.let { it.reveal.full = true }
        }
        updatePanel()
        // サイズが変わったら画面内に収める
        frame.post { clampPanel() }
    }

    private fun clearPanelRefs() {
        bubble = null; statusText = null; statusDots = null; recButton = null; playButton = null; stopButton = null; pill = null
    }

    private fun buildBubble(): BubbleView =
        BubbleView(this, ui).apply { setOnClickListener { setCollapsed(false) } }.also { bubble = it }

    private fun clampPanel() {
        if (anim != null) return
        val frame = panel ?: return; val lp = panelParams ?: return
        val s = screenSize()
        val nx = PanelMath.clamp(lp.x, frame.width, s.x); val ny = PanelMath.clamp(lp.y, frame.height, s.y)
        if (nx != lp.x || ny != lp.y) { lp.x = nx; lp.y = ny; runCatching { wm.updateViewLayout(frame, lp) } }
    }

    private fun animationsEnabled(): Boolean =
        ValueAnimator.areAnimatorsEnabled() &&
            Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f

    /**
     * 開閉アニメーション。
     * ウィンドウは展開後の大きさに1回だけ合わせ、アニメーション中は WindowManager を再レイアウトしない。
     * ピルの輪郭（outline）を毎フレーム変えて、48dp の円 ⇄ ピル型 に切り抜く（影も輪郭に追従）。
     * 開く: バブルの左端を基準に右へ広がる。画面の右端に近くて収まらないときは、バブルの右端を基準に左へ広がる。
     * 閉じる: 広がった向きと逆に縮み、元のバブルの位置に戻る。
     */
    fun setCollapsed(c: Boolean) {
        if (anim != null || c == collapsed) return
        val frame = panel ?: return; val lp = panelParams ?: return
        val pad = frame.paddingLeft + frame.paddingRight
        val bubbleW = dp(48)
        if (!c) {
            // ---- 開く ----
            val sw = screenSize().x
            val probe = buildPill(leftward = false)
            probe.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.makeMeasureSpec(dp(48), View.MeasureSpec.EXACTLY))
            val pillW = probe.measuredWidth
            val leftward = PanelMath.expandLeftward(lp.x, bubbleW + pad, pillW + pad, sw)
            collapsed = false
            frame.removeAllViews(); clearPanelRefs()
            val box = if (leftward) buildPill(leftward = true) else probe.also { rebindPill(it) }
            val p = pill ?: return
            lp.x = PanelMath.expandedX(lp.x, bubbleW + pad, pillW + pad, leftward)
            frame.addView(box)
            updatePanel()
            runCatching { wm.updateViewLayout(frame, lp) }
            savePanelPos()
            if (!animationsEnabled()) { p.reveal.full = true; p.box.invalidateOutline(); p.applyChildren(1f); return }
            runPillAnim(p, from = 0f, to = 1f, durationMs = 250, interp = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)) {}
        } else {
            // ---- 閉じる ----
            val p = pill
            collapsed = true
            savePanelPos()
            if (p == null || !animationsEnabled()) { finishCollapse(p); return }
            runPillAnim(p, from = 1f, to = 0f, durationMs = 200, interp = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)) {
                finishCollapse(p)
            }
        }
    }

    private fun finishCollapse(p: PillViews?) {
        val frame = panel ?: return; val lp = panelParams ?: return
        if (p != null) lp.x = PanelMath.collapsedX(lp.x, dp(48), p.box.width, p.leftward)
        frame.removeAllViews(); clearPanelRefs()
        frame.addView(buildBubble())
        updatePanel()
        runCatching { wm.updateViewLayout(frame, lp) }
        savePanelPos()
        frame.post { clampPanel() }
    }

    private fun runPillAnim(p: PillViews, from: Float, to: Float, durationMs: Long, interp: android.view.animation.Interpolator, end: () -> Unit) {
        p.reveal.full = false
        p.setFraction(from)
        anim = ValueAnimator.ofFloat(from, to).apply {
            duration = durationMs; interpolator = interp
            addUpdateListener { p.setFraction(it.animatedValue as Float) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: android.animation.Animator) { cancelled = true }
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    anim = null
                    if (to >= 1f) { p.reveal.full = true; p.box.invalidateOutline(); p.applyChildren(1f) }
                    if (!cancelled) end()
                }
            })
            start()
        }
    }

    /** 展開パネルを構成するビューと、開閉の見た目を作る処理 */
    private inner class PillViews(
        val box: android.widget.FrameLayout,
        val row: LinearLayout,
        val ghost: android.widget.ImageView,
        val leftward: Boolean,
    ) {
        val reveal = RevealOutline(leftward)

        /** f: 0 = 48dp の円（バブル）, 1 = 全部開いた状態 */
        fun setFraction(f: Float) {
            reveal.fraction = f
            box.invalidateOutline(); box.invalidate()
            // バブルのアイコンは前半で消える / 閉じるときは後半で現れる
            ghost.alpha = (1f - f / 0.35f).coerceIn(0f, 1f)
            applyChildren(f)
        }

        /** ボタンを順番にフェード＋スライドで出す（広がる方向に合わせて） */
        fun applyChildren(f: Float) {
            val n = row.childCount
            for (i in 0 until n) {
                val idx = if (leftward) n - 1 - i else i
                val start = 0.15f + 0.4f * idx / n.coerceAtLeast(1)
                val t = ((f - start) / 0.45f).coerceIn(0f, 1f)
                val v = row.getChildAt(i)
                v.alpha = t
                v.translationX = (1f - t) * dp(12) * (if (leftward) 1 else -1)
            }
        }
    }

    /** 輪郭: 左（または右）端を固定し、幅を 48dp〜全幅で変える角丸四角形 */
    private inner class RevealOutline(val leftward: Boolean) : android.view.ViewOutlineProvider() {
        var fraction = 1f
        var full = true
        override fun getOutline(view: View, outline: android.graphics.Outline) {
            val w = view.width; val h = view.height
            val f = if (full) 1f else fraction
            val rw = (h + (w - h) * f).toInt().coerceIn(h.coerceAtMost(w), w)
            if (leftward) outline.setRoundRect(w - rw, 0, w, h, h / 2f)
            else outline.setRoundRect(0, 0, rw, h, h / 2f)
        }
    }

    private fun rebindPill(box: View) { pill = box.tag as? PillViews; pill?.let { bindRefs(it) } }

    private fun bindRefs(p: PillViews) {
        val r = p.row
        recButton = r.findViewWithTag("rec"); playButton = r.findViewWithTag("play")
        stopButton = r.findViewWithTag("stop"); statusText = r.findViewWithTag("status")
        statusDots = r.findViewWithTag("dots")
    }

    private fun buildPill(leftward: Boolean): View {
        val widthDp = (screenSize().x / resources.displayMetrics.density).toInt()
        val size = PanelMath.buttonSizeDp(widthDp)
        val h = dp(48)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(4), 0)
        }
        // v0.9: ボタンは縦 48dp（パネルの高さいっぱい）を押せる。丸の見た目は size のまま（R6.5 / R6.6）
        val m = if (size >= 48) 0 else 1
        fun add(v: View, w: Int = dp(size), hh: Int = h) =
            row.addView(v, LinearLayout.LayoutParams(w, hh).apply { marginStart = dp(m); marginEnd = dp(m) })
        fun btn(icon: Int, desc: Int, onClick: () -> Unit) = ui.iconButton(icon, getString(desc), size, 48, onClick)

        add(btn(R.drawable.ic_record, R.string.desc_record) {
            if (state == State.RECORDING) stopRecording() else startRecording()
        }.apply { tag = "rec" })
        add(btn(R.drawable.ic_play, R.string.desc_play) { playSelected() }.apply {
            tag = "play"
            setOnLongClickListener {
                val n = store.selectedId?.let { store.get(it)?.name } ?: store.list().firstOrNull()?.name
                toast(n?.let { getString(R.string.toast_play_target, it) } ?: getString(R.string.toast_no_macros_short)); true
            }
        })
        add(btn(R.drawable.ic_stop, R.string.desc_stop) { stopAll() }.apply { tag = "stop" })
        // v0.7: 状態の横に小さなドットマトリクス（記録=赤の脈動、再生=アクセントの波）
        row.addView(DotMatrixView(this, 3, 3, dp(3).toFloat(), ui.dp(1.5f).toFloat()).apply {
            tag = "dots"; visibility = View.GONE
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(4)
        })
        val st = ui.monoText("", Ui.Type.LABEL_MEDIUM, ui.onSurfaceVariant, Ui.Weight.MEDIUM).apply {
            tag = "status"
            maxWidth = dp(64); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(4), 0, dp(6), 0)
        }
        row.addView(st, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        // 区切り線
        add(View(this).apply { setBackgroundColor(ui.outline) }, dp(1), dp(size - 16))
        add(btn(R.drawable.ic_settings, R.string.desc_open_app) { openApp() })
        add(btn(R.drawable.ic_collapse, R.string.desc_collapse) { setCollapsed(true) }.apply {
            // 左へ広がったときは、縮む向き（右）に矢印を向ける
            if (leftward) scaleX = -1f
        })
        add(btn(R.drawable.ic_close, R.string.desc_hide_panel) { hidePanel() })

        val box = android.widget.FrameLayout(this).apply {
            background = ui.rounded(ui.surfaceContainerHigh, 24, ui.outlineVariant)
            elevation = dp(6).toFloat()
            minimumHeight = h
        }
        box.addView(row, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, h))
        // 開閉中だけ見える「バブル」の顔（48dp の円とアイコン）
        val ghost = android.widget.ImageView(this).apply {
            setImageDrawable(ui.icon(R.drawable.ic_touch, ui.onPrimaryContainer))
            scaleType = android.widget.ImageView.ScaleType.CENTER
            background = ui.oval(ui.primaryContainer)
            alpha = 0f; isClickable = false; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        box.addView(ghost, android.widget.FrameLayout.LayoutParams(h, h,
            (if (leftward) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL))
        val p = PillViews(box, row, ghost, leftward)
        box.outlineProvider = p.reveal
        box.clipToOutline = true
        box.tag = p
        pill = p; bindRefs(p)
        return box
    }

    /** 「設定」: アプリのメイン画面に戻る。
     *  ユーザー補助サービスはシステムにバインドされているので、サービスの Context から
     *  バックグラウンドでもアクティビティを起動できる（BAL の例外）。 */
    fun openApp() {
        if (state == State.PLAYING) stopPlayback()
        if (state == State.RECORDING) stopRecording()
        val intent = Intent(this, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
        )
        val ok = runCatching { startActivity(intent) }.isSuccess
        if (!ok) {
            // 念のための予備: PendingIntent に BAL 許可を付けて送る
            runCatching {
                val pi = PendingIntent.getActivity(this, 0, intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                if (Build.VERSION.SDK_INT >= 34) {
                    val opts = ActivityOptions.makeBasic().apply {
                        @Suppress("DEPRECATION")
                        setPendingIntentBackgroundActivityStartMode(
                            if (Build.VERSION.SDK_INT >= 36) ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                            else ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    }
                    pi.send(this, 0, null, null, null, null, opts.toBundle())
                } else pi.send()
            }.onFailure { toast(getString(R.string.toast_open_failed)) }
        }
    }

    fun hidePanel() {
        anim?.cancel(); anim = null; pill = null
        if (state == State.RECORDING) stopRecording()
        removeView(panel); panel = null
        bubble = null; statusText = null; statusDots = null; recButton = null; playButton = null; stopButton = null
        notifyChanged()
        toast(getString(R.string.toast_panel_hidden))
    }

    val isPanelShown: Boolean get() = panel != null

    /** 記録用オーバーレイより上にパネルを出すため、付け直す */
    private fun bringPanelToFront() {
        val p = panel ?: return
        val lp = panelParams ?: return
        removeView(p)
        runCatching { wm.addView(p, lp) }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // ダーク/ライト切替や画面回転に合わせて作り直す
        if (panel != null) {
            if (newConfig.uiMode != lastUiMode || newConfig.locales != lastLocales) {
                lastUiMode = newConfig.uiMode
                lastLocales = newConfig.locales
                buildPanelContent()
            } else panel?.post { clampPanel() }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updatePanel() {
        bubble?.let { b ->
            b.mode = when (state) {
                State.IDLE -> BubbleView.Mode.IDLE
                State.RECORDING -> BubbleView.Mode.RECORDING
                State.PLAYING -> BubbleView.Mode.PLAYING
            }
            b.progress = playing?.let { m -> if (m.steps.isEmpty()) 0f else stepIndex.toFloat() / m.steps.size } ?: 0f
        }
        val rec = recButton ?: return
        when (state) {
            State.RECORDING -> {
                ui.styleIconButton(rec, R.drawable.ic_stop, ui.onErrorContainer, ui.errorContainer)
                rec.contentDescription = getString(R.string.desc_stop_recording)
            }
            else -> {
                ui.styleIconButton(rec, R.drawable.ic_record, ui.record, null)
                rec.contentDescription = getString(R.string.desc_record)
            }
        }
        playButton?.let { p ->
            if (state == State.PLAYING) ui.styleIconButton(p, R.drawable.ic_play, ui.onPrimary, ui.primary)
            else ui.styleIconButton(p, R.drawable.ic_play, ui.onPrimaryContainer, ui.primaryContainer)
        }
        // 幅を抑えるため、使わないボタンは隠す
        rec.visibility = if (state == State.PLAYING) View.GONE else View.VISIBLE
        playButton?.visibility = if (state == State.RECORDING) View.GONE else View.VISIBLE
        stopButton?.visibility = if (state == State.IDLE) View.GONE else View.VISIBLE
        statusText?.let { t ->
            t.text = when (state) {
                State.IDLE -> ""
                State.RECORDING -> Texts.steps(this, recordedSteps.size)
                State.PLAYING -> playing?.let {
                    val total = if (it.repeatCount == 0) "∞" else it.repeatCount.toString()
                    "${loopIndex + 1}/$total"
                } ?: ""
            }
            t.visibility = if (state == State.IDLE) View.GONE else View.VISIBLE
        }
        statusDots?.let { d ->
            when (state) {
                State.RECORDING -> { d.color = ui.record; d.pattern = DotMatrixView.Pattern.PULSE }
                State.PLAYING -> { d.color = ui.primary; d.pattern = DotMatrixView.Pattern.WAVE }
                State.IDLE -> {}
            }
            d.visibility = if (state == State.IDLE) View.GONE else View.VISIBLE
        }
        panel?.post { clampPanel() }
    }

    // ---------------- 記録 ----------------
    fun startRecording() {
        if (state != State.IDLE) return
        // 無料枠（monetized フレーバーのみ。standard は無制限）
        val limit = jp.ayu.macro.monetization.Monetization.entitlements(this).maxSavedMacros
        if (!jp.ayu.macro.monetization.FreeTier.canSaveNew(store.list().size, limit)) {
            val l = limit ?: 0
            toast(resources.getQuantityString(R.plurals.toast_free_limit, l, l))
            return
        }
        removeView(nameDialog); nameDialog = null
        recordedSteps.clear()
        recScreen = screenSize()
        recordStartUptime = SystemClock.uptimeMillis()
        lastDeliveredStart = 0L; lastDeliveredDuration = 0L
        val cv = CaptureView(this) { down, up, strokes -> onCaptured(down, up, strokes) }
        val lp = overlayParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        )
        runCatching { wm.addView(cv, lp) }.onFailure {
            toast(getString(R.string.toast_rec_failed, it.message ?: "")); return
        }
        capture = cv; captureParams = lp
        state = State.RECORDING
        if (panel == null) showPanel() else bringPanelToFront()
        updatePanel()
        toast(getString(R.string.toast_rec_started))
    }

    private fun onCaptured(downUptime: Long, upUptime: Long, strokes: List<Stroke>) {
        val step0 = Step(0, strokes)
        val duration = step0.totalDurationMs
        // 実際に下のアプリへ届く時刻 ≒ 指を離した時刻。前の操作が届き終わってからの待ち時間を記録する。
        val deliveredStart = upUptime
        val delay = if (recordedSteps.isEmpty()) 300L
            else (deliveredStart - (lastDeliveredStart + lastDeliveredDuration)).coerceAtLeast(0L)
        val step = Step(delay, strokes)
        recordedSteps.add(step)
        lastDeliveredStart = deliveredStart; lastDeliveredDuration = duration
        updatePanel()
        passThrough(step)
    }

    /** 記録したジェスチャーを下のアプリへ届ける: オーバーレイを一時的にタッチ透過にして dispatchGesture */
    private fun passThrough(step: Step) {
        val cv = capture ?: return
        val lp = captureParams ?: return
        cv.passThrough = true
        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        runCatching { wm.updateViewLayout(cv, lp) }
        val restore = Runnable {
            val c = capture ?: return@Runnable
            val p = captureParams ?: return@Runnable
            p.flags = p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            runCatching { wm.updateViewLayout(c, p) }
            c.passThrough = false
            c.clearTrail()
        }
        // ウィンドウ設定の反映を少し待ってから送る
        handler.postDelayed({
            if (capture == null) return@postDelayed
            val s = screenSize()
            val g = GestureFactory.build(step, 1f, recScreen.x, recScreen.y, s.x, s.y)
            if (g == null) { restore.run(); return@postDelayed }
            val ok = dispatchGesture(g, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) { handler.postDelayed(restore, 60) }
                override fun onCancelled(gestureDescription: GestureDescription?) { handler.postDelayed(restore, 60) }
            }, handler)
            if (!ok) restore.run()
        }, PASS_THROUGH_DELAY_MS)
    }

    fun stopRecording() {
        if (state != State.RECORDING) return
        removeView(capture); capture = null; captureParams = null
        state = State.IDLE
        updatePanel()
        if (recordedSteps.isEmpty()) { toast(getString(R.string.toast_nothing_recorded)); return }
        val loc = resources.configuration.locales[0]
        val defaultName = getString(R.string.default_macro_name,
            SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(loc, "MdHHmm"), loc).format(Date()))
        val macro = Macro(
            id = MacroStore.newId(), name = defaultName, createdAt = System.currentTimeMillis(),
            screenWidth = recScreen.x, screenHeight = recScreen.y, steps = recordedSteps.toList(),
        )
        store.save(macro)
        store.selectedId = macro.id
        recordedSteps.clear()
        updatePanel()
        notifyChanged()
        showNameDialog(macro)
    }

    /** 保存後に名前を付けるための小さなダイアログ（オーバーレイ）。見た目はアプリ内のダイアログと同じ */
    private fun showNameDialog(macro: Macro) {
        removeView(nameDialog)
        val edit = ui.field(macro.name, getString(R.string.name_hint)).apply { setSelection(text.length) }
        val card = DialogCard(ui).build(
            title = resources.getQuantityString(R.plurals.saved_title, macro.steps.size, macro.steps.size),
            message = getString(R.string.saved_message), iconId = R.drawable.ic_check_circle,
            content = edit, confirmLabel = getString(R.string.save), cancelLabel = getString(R.string.keep_name),
        )
        val root = card.root
        card.cancel?.setOnClickListener { removeView(root); nameDialog = null }
        card.confirm.setOnClickListener {
            val n = edit.text.toString().trim()
            if (n.isNotEmpty()) store.get(macro.id)?.let { store.save(it.copy(name = n)) }
            removeView(root); nameDialog = null
            updatePanel(); notifyChanged()
        }
        val lp = overlayParams(
            minOf((screenSize().x * 0.88).toInt(), dp(400)), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.FLAG_DIM_BEHIND,
        ).apply {
            gravity = Gravity.CENTER; dimAmount = 0.4f
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        }
        runCatching { wm.addView(root, lp); nameDialog = root; edit.requestFocus() }
    }

    // ---------------- 再生 ----------------
    fun playSelected() {
        val id = store.selectedId
        val m = id?.let { store.get(it) } ?: store.list().firstOrNull()
        if (m == null) { toast(getString(R.string.toast_no_macros)); return }
        play(m, 400L)
    }

    fun play(macro: Macro, initialDelayMs: Long = 0L) {
        if (state == State.RECORDING) stopRecording()
        if (state == State.PLAYING) stopPlayback()
        if (macro.steps.isEmpty()) { toast(getString(R.string.toast_empty_macro)); return }
        removeView(nameDialog); nameDialog = null
        playing = macro; loopIndex = 0; stepIndex = 0; cancelledCount = 0
        state = State.PLAYING
        val token = ++playToken
        updatePanel()
        handler.postDelayed({ runStep(token) }, initialDelayMs)
    }

    private fun runStep(token: Int) {
        if (token != playToken || state != State.PLAYING) return
        val m = playing ?: return
        if (stepIndex >= m.steps.size) {
            loopIndex++
            if (m.repeatCount in 1..loopIndex) { finishPlayback(); return }
            stepIndex = 0
            updatePanel()
            handler.postDelayed({ runStep(token) }, ReplayMath.scaleTime(m.loopIntervalMs, m.speed))
            return
        }
        val step = m.steps[stepIndex]
        handler.postDelayed({
            if (token != playToken || state != State.PLAYING) return@postDelayed
            val s = screenSize()
            val g = GestureFactory.build(step, m.speed, m.screenWidth, m.screenHeight, s.x, s.y)
            if (g == null) { stepIndex++; runStep(token); return@postDelayed }
            val ok = dispatchGesture(g, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    stepIndex++; bubble?.let { updatePanel() }; runStep(token)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    // 画面に触れた等で中断された場合も、次の操作へ進む
                    cancelledCount++
                    stepIndex++; runStep(token)
                }
            }, handler)
            if (!ok) { toast(getString(R.string.toast_dispatch_failed)); stopPlayback() }
        }, ReplayMath.scaleTime(step.delayMs, m.speed))
    }

    private fun finishPlayback() {
        val c = cancelledCount
        stopPlayback()
        toast(if (c > 0) resources.getQuantityString(R.plurals.toast_finished_cancelled, c, c) else getString(R.string.toast_finished))
    }

    fun stopPlayback() {
        playToken++
        handler.removeCallbacksAndMessages(null)
        playing = null
        if (state == State.PLAYING) state = State.IDLE
        updatePanel()
    }

    fun stopAll() {
        if (state == State.RECORDING) stopRecording()
        if (state == State.PLAYING) stopPlayback()
        updatePanel()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        private const val PASS_THROUGH_DELAY_MS = 40L

        @Volatile
        var instance: MacroAccessibilityService? = null
            private set

        /** MainActivity が一覧を更新するためのコールバック */
        var onChanged: (() -> Unit)? = null

        fun notifyChanged() { Handler(Looper.getMainLooper()).post { onChanged?.invoke() } }
    }
}
