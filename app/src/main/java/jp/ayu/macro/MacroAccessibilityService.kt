package jp.ayu.macro

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
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

    // フローティングパネル
    private var panel: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var statusText: TextView? = null
    private var recButton: Button? = null
    private var playButton: Button? = null

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
    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    fun showPanel() {
        if (panel != null) { bringPanelToFront(); return }
        val ctx = ContextThemeWrapper(this, android.R.style.Theme_Material_Light)
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat(); setColor(Color.argb(230, 33, 33, 33))
            }
        }
        val handle = TextView(ctx).apply {
            text = "⠿"; setTextColor(Color.WHITE); textSize = 22f
            setPadding(dp(8), 0, dp(6), 0)
            contentDescription = "移動"
        }
        fun mk(label: String, desc: String, onClick: () -> Unit) = Button(ctx).apply {
            text = label; textSize = 13f; isAllCaps = false; minWidth = 0; minimumWidth = 0
            setPadding(dp(10), 0, dp(10), 0); contentDescription = desc
            setOnClickListener { onClick() }
        }
        recButton = mk("● 記録", "記録を開始") { if (state == State.RECORDING) stopRecording() else startRecording() }
        playButton = mk("▶ 再生", "選択中のマクロを再生") { playSelected() }
        val stopButton = mk("■ 停止", "停止") { stopAll() }
        val hideButton = mk("✕", "フローティングボタンを隠す") { hidePanel() }
        statusText = TextView(ctx).apply {
            setTextColor(Color.WHITE); textSize = 11f; setPadding(dp(6), 0, dp(4), 0)
        }
        root.addView(handle)
        root.addView(recButton)
        root.addView(playButton)
        root.addView(stopButton)
        root.addView(statusText)
        root.addView(hideButton)

        val prefs = getSharedPreferences("panel", MODE_PRIVATE)
        val lp = overlayParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("x", dp(16)); y = prefs.getInt("y", dp(160))
        }

        // ドラッグ移動
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0
        handle.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y; true }
                MotionEvent.ACTION_MOVE -> {
                    val s = screenSize()
                    lp.x = (startX + (e.rawX - downX).toInt()).coerceIn(0, (s.x - dp(48)).coerceAtLeast(0))
                    lp.y = (startY + (e.rawY - downY).toInt()).coerceIn(0, (s.y - dp(48)).coerceAtLeast(0))
                    runCatching { wm.updateViewLayout(root, lp) }; true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    prefs.edit().putInt("x", lp.x).putInt("y", lp.y).apply(); true
                }
                else -> false
            }
        }
        runCatching { wm.addView(root, lp) }.onFailure {
            toast("フローティングボタンを表示できませんでした: ${it.message}")
            return
        }
        panel = root; panelParams = lp
        updatePanel()
    }

    fun hidePanel() {
        if (state == State.RECORDING) stopRecording()
        removeView(panel); panel = null
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

    @SuppressLint("SetTextI18n")
    private fun updatePanel() {
        recButton?.text = if (state == State.RECORDING) "■ 記録終了" else "● 記録"
        recButton?.isEnabled = state != State.PLAYING
        playButton?.isEnabled = state == State.IDLE
        statusText?.text = when (state) {
            State.IDLE -> store.selectedId?.let { store.get(it)?.name }?.let { "選択: ${it.take(10)}" } ?: "待機中"
            State.RECORDING -> "記録中 ${recordedSteps.size}操作"
            State.PLAYING -> playing?.let {
                val total = if (it.repeatCount == 0) "∞" else it.repeatCount.toString()
                "再生 ${loopIndex + 1}/$total"
            } ?: "再生中"
        }
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
        val ctx = ContextThemeWrapper(this, android.R.style.Theme_Material_Light_Dialog)
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(12))
            background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(Color.WHITE) }
            elevation = dp(8).toFloat()
        }
        root.addView(TextView(ctx).apply {
            text = "保存しました（${macro.steps.size}操作）。名前を付けてください"
            setTextColor(Color.BLACK); textSize = 15f
        })
        val edit = EditText(ctx).apply {
            setText(macro.name); setSelection(text.length)
            inputType = InputType.TYPE_CLASS_TEXT; setSingleLine()
        }
        root.addView(edit)
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        row.addView(Button(ctx).apply {
            text = "このまま"; setOnClickListener { removeView(root); nameDialog = null }
        })
        row.addView(Button(ctx).apply {
            text = "保存"
            setOnClickListener {
                val n = edit.text.toString().trim()
                if (n.isNotEmpty()) store.get(macro.id)?.let { store.save(it.copy(name = n)) }
                removeView(root); nameDialog = null
                updatePanel(); notifyChanged()
            }
        })
        root.addView(row)
        val lp = overlayParams(
            (screenSize().x * 0.85).toInt(), WindowManager.LayoutParams.WRAP_CONTENT,
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
                    stepIndex++; runStep(token)
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
