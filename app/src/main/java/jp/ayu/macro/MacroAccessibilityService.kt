package jp.ayu.macro

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
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
    private var recButton: ImageButton? = null
    private var playButton: ImageButton? = null
    private var stopButton: ImageButton? = null
    private var lastUiMode = 0

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

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        store = MacroStore(this)
        instance = this
        showPanel()
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
    private val ui by lazy { Ui(this) }

    @SuppressLint("SetTextI18n")
    fun showPanel() {
        if (panel != null) { bringPanelToFront(); return }
        lastUiMode = resources.configuration.uiMode
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
            if (start) { startX = lp.x; startY = lp.y } else {
                val s = screenSize()
                lp.x = PanelMath.clamp(startX + dx, frame.width, s.x)
                lp.y = PanelMath.clamp(startY + dy, frame.height, s.y)
                runCatching { wm.updateViewLayout(frame, lp) }
            }
        }, onEnd = { savePanelPos() })
        // 影（elevation）が切れないよう外側に余白を取る
        frame.setPadding(dp(8), dp(8), dp(8), dp(8)); frame.clipToPadding = false
        runCatching { wm.addView(frame, lp) }.onFailure {
            toast("フローティングボタンを表示できませんでした: ${it.message}")
            return
        }
        panel = frame; panelParams = lp
        buildPanelContent()
    }

    private fun savePanelPos() {
        val lp = panelParams ?: return
        prefs.edit().putInt("x", lp.x).putInt("y", lp.y).putBoolean("collapsed", collapsed).apply()
    }

    /** 展開/折りたたみに合わせて中身を作り直す */
    private fun buildPanelContent() {
        val frame = panel ?: return
        frame.removeAllViews()
        bubble = null; statusText = null; recButton = null; playButton = null; stopButton = null
        if (collapsed) {
            val b = BubbleView(this, ui).apply { setOnClickListener { setCollapsed(false) } }
            frame.addView(b); bubble = b
        } else {
            frame.addView(buildPill())
        }
        updatePanel()
        // サイズが変わったら画面内に収める
        frame.post { clampPanel() }
    }

    private fun clampPanel() {
        val frame = panel ?: return; val lp = panelParams ?: return
        val s = screenSize()
        val nx = PanelMath.clamp(lp.x, frame.width, s.x); val ny = PanelMath.clamp(lp.y, frame.height, s.y)
        if (nx != lp.x || ny != lp.y) { lp.x = nx; lp.y = ny; runCatching { wm.updateViewLayout(frame, lp) } }
    }

    fun setCollapsed(c: Boolean) {
        collapsed = c
        savePanelPos()
        buildPanelContent()
    }

    private fun buildPill(): View {
        val widthDp = (screenSize().x / resources.displayMetrics.density).toInt()
        val size = PanelMath.buttonSizeDp(widthDp)
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
            background = ui.rounded(ui.surfaceContainerHigh, (size + 8) / 2f)
            elevation = dp(6).toFloat()
        }
        fun add(v: View, w: Int = dp(size), h: Int = dp(size)) =
            pill.addView(v, LinearLayout.LayoutParams(w, h).apply { marginStart = dp(1); marginEnd = dp(1) })

        recButton = ui.iconButton(R.drawable.ic_record, "記録", size) {
            if (state == State.RECORDING) stopRecording() else startRecording()
        }.also { add(it) }
        playButton = ui.iconButton(R.drawable.ic_play, "再生", size) { playSelected() }.also { add(it) }
        playButton?.setOnLongClickListener {
            val n = store.selectedId?.let { store.get(it)?.name } ?: store.list().firstOrNull()?.name
            toast(n?.let { "再生するマクロ: $it" } ?: "マクロがありません"); true
        }
        stopButton = ui.iconButton(R.drawable.ic_stop, "停止", size) { stopAll() }.also { add(it) }
        statusText = ui.text("", 12f, ui.onSurfaceVariant, medium = true).apply {
            maxWidth = dp(64); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(4), 0, dp(6), 0)
        }
        pill.addView(statusText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        // 区切り線
        add(View(this).apply { setBackgroundColor(ui.outlineVariant) }, dp(1), dp(size - 16))
        add(ui.iconButton(R.drawable.ic_settings, "設定（アプリを開く）", size) { openApp() })
        add(ui.iconButton(R.drawable.ic_collapse, "小さくする", size) { setCollapsed(true) })
        add(ui.iconButton(R.drawable.ic_close, "フローティングボタンを隠す", size) { hidePanel() })
        return pill
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
            }.onFailure { toast("アプリを開けませんでした") }
        }
    }

    fun hidePanel() {
        if (state == State.RECORDING) stopRecording()
        removeView(panel); panel = null
        bubble = null; statusText = null; recButton = null; playButton = null; stopButton = null
        notifyChanged()
        toast("アプリの画面から再表示できます")
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
            if (newConfig.uiMode != lastUiMode) {
                lastUiMode = newConfig.uiMode
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
                rec.contentDescription = "記録を終了して保存"
            }
            else -> {
                ui.styleIconButton(rec, R.drawable.ic_record, ui.record, null)
                rec.contentDescription = "記録"
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
                State.RECORDING -> "${recordedSteps.size}操作"
                State.PLAYING -> playing?.let {
                    val total = if (it.repeatCount == 0) "∞" else it.repeatCount.toString()
                    "${loopIndex + 1}/$total"
                } ?: ""
            }
            t.visibility = if (state == State.IDLE) View.GONE else View.VISIBLE
        }
        panel?.post { clampPanel() }
    }

    // ---------------- 記録 ----------------
    fun startRecording() {
        if (state != State.IDLE) return
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
            toast("記録を開始できませんでした: ${it.message}"); return
        }
        capture = cv; captureParams = lp
        state = State.RECORDING
        if (panel == null) showPanel() else bringPanelToFront()
        updatePanel()
        toast("記録を開始しました。ふつうに操作してください（1操作ずつ、ゆっくりめに）")
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
        if (recordedSteps.isEmpty()) { toast("操作が記録されていません"); return }
        val defaultName = "マクロ " + SimpleDateFormat("M/d HH:mm", Locale.JAPAN).format(Date())
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

    /** 保存後に名前を付けるための小さなダイアログ（オーバーレイ） */
    @SuppressLint("SetTextI18n")
    private fun showNameDialog(macro: Macro) {
        removeView(nameDialog)
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val ctx = ContextThemeWrapper(this, if (night) android.R.style.Theme_Material_Dialog else android.R.style.Theme_Material_Light_Dialog)
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(12))
            background = ui.rounded(ui.surfaceContainerHigh, 28)
            elevation = dp(6).toFloat()
        }
        root.addView(TextView(ctx).apply {
            text = "保存しました（${macro.steps.size}操作）\n名前を付けてください"
            setTextColor(ui.onSurface); textSize = 17f; setLineSpacing(0f, 1.15f)
        })
        val edit = EditText(ctx).apply {
            setText(macro.name); setSelection(text.length)
            inputType = InputType.TYPE_CLASS_TEXT; setSingleLine(); setTextColor(ui.onSurface)
        }
        root.addView(edit)
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        row.setPadding(0, dp(12), 0, 0)
        row.addView(ui.button("このまま", Ui.BtnStyle.TEXT) { removeView(root); nameDialog = null })
        row.addView(ui.button("保存", Ui.BtnStyle.FILLED) {
            val n = edit.text.toString().trim()
            if (n.isNotEmpty()) store.get(macro.id)?.let { store.save(it.copy(name = n)) }
            removeView(root); nameDialog = null
            updatePanel(); notifyChanged()
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) })
        root.addView(row)
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
        if (m == null) { toast("マクロがありません。まず記録してください"); return }
        play(m, 400L)
    }

    fun play(macro: Macro, initialDelayMs: Long = 0L) {
        if (state == State.RECORDING) stopRecording()
        if (state == State.PLAYING) stopPlayback()
        if (macro.steps.isEmpty()) { toast("このマクロには操作がありません"); return }
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
            if (!ok) { toast("操作を実行できませんでした。ユーザー補助の設定を確認してください"); stopPlayback() }
        }, ReplayMath.scaleTime(step.delayMs, m.speed))
    }

    private fun finishPlayback() {
        val c = cancelledCount
        stopPlayback()
        toast(if (c > 0) "再生が終わりました（中断された操作: $c）" else "再生が終わりました")
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
