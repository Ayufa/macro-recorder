package jp.ayu.macro

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** ダイアログの中身（アプリ内のダイアログと、フローティング上の名前入力で共通の見た目） */
class DialogCard(val ui: Ui) {
    private fun dp(v: Int) = ui.dp(v)

    class Built(val root: LinearLayout, val confirm: TextView, val cancel: TextView?)

    fun build(
        title: String,
        message: String? = null,
        iconId: Int? = null,
        destructive: Boolean = false,
        content: View? = null,
        confirmLabel: String,
        cancelLabel: String?,
    ): Built {
        val ctx = ui.ctx
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ui.rounded(ui.surfaceContainerHigh, 20, ui.outlineVariant)
            // v0.10.1: padding は background の後に設定する（先だと背景に上書きされる）
            setPadding(dp(24), dp(24), dp(24), dp(16))
            elevation = dp(8).toFloat()
        }
        if (iconId != null) {
            root.addView(ImageView(ctx).apply {
                setImageDrawable(ui.icon(iconId, if (destructive) ui.error else ui.onSurface))
                // v0.7: 丸ではなく角丸のタイル + 細い線
                background = ui.rounded(if (destructive) ui.errorContainer else ui.secondaryContainer, 12,
                    if (destructive) ui.withAlpha(ui.error, 0.3f) else ui.outlineVariant)
                scaleType = ImageView.ScaleType.CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { bottomMargin = dp(16) })
        }
        // v0.9: M3 のダイアログの見出しは headline small（24sp）、本文は body medium（R4.1 / R5.12）
        root.addView(ui.text(title, Ui.Type.HEADLINE_SMALL, ui.onSurface, weight = Ui.Weight.MEDIUM).apply {
            if (android.os.Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
        })
        if (message != null) root.addView(ui.text(message, Ui.Type.BODY_MEDIUM, ui.onSurfaceVariant).apply { setPadding(0, dp(16), 0, 0) })
        if (content != null) root.addView(content, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(16) })
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        row.setPadding(0, dp(16), 0, 0)
        val cancel = cancelLabel?.let { ui.button(it, Ui.BtnStyle.TEXT) {} }
        if (cancel != null) row.addView(cancel)
        // v0.9: ダイアログのボタンは文字ボタン（確定は右、取り消しはその左。R5.12）
        val confirm = ui.button(confirmLabel, if (destructive) Ui.BtnStyle.DANGER_TEXT else Ui.BtnStyle.PRIMARY_TEXT) {}
        row.addView(confirm, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(8) })
        root.addView(row)
        return Built(root, confirm, cancel)
    }
}

/** アプリ内のダイアログ（角丸・細い線・ピル型ボタン） */
object Dialogs {
    fun show(
        activity: Activity,
        title: String,
        message: String? = null,
        iconId: Int? = null,
        destructive: Boolean = false,
        content: View? = null,
        confirmLabel: String,
        cancelLabel: String? = activity.getString(R.string.cancel),
        showKeyboard: Boolean = false,
        onConfirm: () -> Boolean,
    ): Dialog {
        val ui = Ui(activity)
        val b = DialogCard(ui).build(title, message, iconId, destructive, content, confirmLabel, cancelLabel)
        val d = Dialog(activity, R.style.AppDialogWindow)
        val frame = FrameLayout(activity).apply {
            setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8)); clipToPadding = false
            addView(b.root, FrameLayout.LayoutParams(MATCH, WRAP))
        }
        d.setContentView(frame)
        b.cancel?.setOnClickListener { d.dismiss() }
        b.confirm.setOnClickListener { if (onConfirm()) d.dismiss() }
        val animate = Motion.enabled(activity)
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val w = minOf(activity.resources.displayMetrics.widthPixels - ui.dp(32), ui.dp(420))
            setLayout(w, ViewGroup.LayoutParams.WRAP_CONTENT)
            setDimAmount(0.32f)
            if (showKeyboard) setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
            // v0.7: 標準のウィンドウアニメーションの代わりに、少し拡大しながらフェードイン
            if (animate) setWindowAnimations(0)
        }
        d.show()
        if (animate) {
            b.root.alpha = 0f; b.root.scaleX = 0.96f; b.root.scaleY = 0.96f; b.root.translationY = ui.dp(8).toFloat()
            b.root.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                .setDuration(280).setInterpolator(Motion.easeOut).start()
        }
        return d
    }

    /** 選択肢の一覧（言語の選択など）。選択中にチェック */
    fun choice(activity: Activity, title: String, labels: List<String>, selected: Int,
               iconId: Int = R.drawable.ic_language, onPick: (Int) -> Unit) {
        val ui = Ui(activity)
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        lateinit var dlg: Dialog
        labels.forEachIndexed { i, l ->
            val on = i == selected
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                minimumHeight = ui.dp(48)
                background = ui.ripple(if (on) ui.rounded(ui.secondaryContainer, 12) else null, ui.rounded(Color.WHITE, 12))
                // v0.10.1: padding は background の後に設定する
                setPadding(ui.dp(16), 0, ui.dp(12), 0)
                isClickable = true; isFocusable = true
                setOnClickListener { dlg.dismiss(); if (i != selected) onPick(i) }
            }
            row.addView(ui.text(l, Ui.Type.BODY_LARGE, ui.onSurface, medium = on), LinearLayout.LayoutParams(0, WRAP, 1f))
            if (on) row.addView(ImageView(activity).apply {
                setImageDrawable(ui.icon(R.drawable.ic_check, ui.primary))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(ui.dp(20), ui.dp(20)))
            row.isSelected = on
            // v0.9: 単一選択の項目として読み上げる（「選択済み」/「未選択」）（R6.1）
            row.accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = android.widget.RadioButton::class.java.name
                    info.isCheckable = true; info.isChecked = on
                }
            }
            list.addView(row, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = ui.dp(2) })
        }
        dlg = show(activity, title, content = list, iconId = iconId,
            confirmLabel = activity.getString(R.string.close), cancelLabel = null) { true }
    }
}

/** 下から出るシート（designeer の drawer イージング cubic-bezier(.32,.72,0,1) でスライド） */
class BottomSheet(private val activity: Activity) : Dialog(activity, R.style.AppSheetWindow) {
    val ui = Ui(activity)
    private val ease = PathInterpolator(0.32f, 0.72f, 0f, 1f)
    private var closing = false
    private lateinit var sheet: View

    fun attach(v: View) {
        sheet = v
        setContentView(v)
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            // v0.9: 大きい画面では幅 640dp までにして中央に（R3.4: M3 のボトムシートの最大幅）
            val sw = activity.resources.displayMetrics.widthPixels
            setLayout(if (sw > ui.dp(640)) ui.dp(640) else ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            setDimAmount(0.32f)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            setWindowAnimations(0)
        }
    }

    private fun animated() = android.animation.ValueAnimator.areAnimatorsEnabled()

    /**
     * v0.9: つまみ。見た目は 36×4dp、押せる範囲は 48dp（R6.5）。
     * TalkBack ではボタンとして読み上げ、ダブルタップで閉じる（ドラッグできない人の代わりの操作: R7.4 / R6.12）。
     */
    fun handle(): View = FrameLayout(activity).apply {
        addView(View(activity).apply { background = ui.rounded(ui.outline, 999); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO },
            FrameLayout.LayoutParams(ui.dp(36), ui.dp(4), Gravity.CENTER))
        contentDescription = activity.getString(R.string.desc_drag_handle)
        isFocusable = true; isClickable = true
        setOnClickListener { dismiss() }
        accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = android.widget.Button::class.java.name
            }
        }
    }

    /** v0.9: ナビゲーションバー分の下の余白（非推奨の systemWindowInsetBottom をやめる） */
    fun padForNavigationBar(v: View) {
        v.setOnApplyWindowInsetsListener { view, insets ->
            val bottom = if (android.os.Build.VERSION.SDK_INT >= 30)
                insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
            else @Suppress("DEPRECATION") insets.systemWindowInsetBottom.coerceAtMost(ui.dp(48))
            view.setPadding(0, 0, 0, bottom)
            insets
        }
    }

    private var backCallback: Any? = null

    /**
     * v0.9: 予測型「戻る」（Android 14+）。戻るジェスチャーの進み具合に合わせてシートを少し縮めて下げ、
     * 指を離したら閉じる / やめたら戻す（R2.5）。アニメーションを削除している端末では標準の動作（すぐ閉じる）。
     */
    private fun registerPredictiveBack() {
        if (android.os.Build.VERSION.SDK_INT < 34 || !animated()) return
        val cb = object : android.window.OnBackAnimationCallback {
            override fun onBackStarted(e: android.window.BackEvent) { sheet.animate().cancel() }
            override fun onBackProgressed(e: android.window.BackEvent) {
                val p = ease.getInterpolation(e.progress)
                sheet.pivotX = sheet.width / 2f; sheet.pivotY = sheet.height.toFloat()
                val sc = 1f - 0.06f * p
                sheet.scaleX = sc; sheet.scaleY = sc
                sheet.translationY = ui.dp(24) * p
            }
            override fun onBackCancelled() {
                sheet.animate().scaleX(1f).scaleY(1f).translationY(0f).setDuration(200).setInterpolator(ease).start()
            }
            override fun onBackInvoked() { dismiss() }
        }
        onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb)
        backCallback = cb
    }

    private fun unregisterPredictiveBack() {
        if (android.os.Build.VERSION.SDK_INT < 34) return
        (backCallback as? android.window.OnBackInvokedCallback)?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
        backCallback = null
    }

    override fun show() {
        super.show()
        registerPredictiveBack()
        if (!animated()) return
        sheet.translationY = activity.resources.displayMetrics.heightPixels.toFloat()
        sheet.post {
            sheet.translationY = sheet.height.toFloat()
            sheet.animate().translationY(0f).setDuration(380).setInterpolator(ease).start()
        }
    }

    /**
     * v0.8: つまみ・見出しを下へドラッグして閉じる（60fps.design の Bottom Sheet で見た操作感）。
     * 高さの 25% を越えるか、速く下へはじいたら閉じる。足りなければ元の位置へ戻す。
     */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    fun dragToDismiss(vararg handles: View) {
        var downY = 0f; var lastY = 0f; var lastT = 0L; var vel = 0f; var dragging = false
        val slop = android.view.ViewConfiguration.get(activity).scaledTouchSlop
        val listener = View.OnTouchListener { _, e ->
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    downY = e.rawY; lastY = e.rawY; lastT = e.eventTime; vel = 0f; dragging = false
                    sheet.animate().cancel(); true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dy = e.rawY - downY
                    if (!dragging && dy > slop) dragging = true
                    if (dragging) {
                        // 上へは少しだけ（ゴムのような抵抗）
                        sheet.translationY = if (dy >= 0) dy else dy / 6f
                        val dt = (e.eventTime - lastT).coerceAtLeast(1)
                        vel = (e.rawY - lastY) / dt * 1000f
                        lastY = e.rawY; lastT = e.eventTime
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        val far = sheet.translationY > sheet.height * 0.25f
                        val fling = vel > 1200f * activity.resources.displayMetrics.density / 2.625f
                        if (far || fling) dismiss()
                        else if (animated()) sheet.animate().translationY(0f).setDuration(260).setInterpolator(ease).start()
                        else sheet.translationY = 0f
                    }
                    dragging = false; true
                }
                else -> false
            }
        }
        handles.forEach { it.setOnTouchListener(listener) }
    }

    override fun dismiss() {
        if (closing) return
        unregisterPredictiveBack()
        if (!animated() || !isShowing) { super.dismiss(); return }
        closing = true
        sheet.animate().translationY(sheet.height.toFloat()).setDuration(240)
            .setInterpolator(PathInterpolator(0.32f, 0f, 0.67f, 0f))
            .withEndAction { runCatching { super.dismiss() } }.start()
    }
}

/** 高さの上限つき LinearLayout（シートが画面いっぱいにならないように） */
@android.annotation.SuppressLint("ViewConstructor")
class MaxHeightLayout(ctx: android.content.Context, private val maxFraction: Float) : LinearLayout(ctx) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val max = (resources.displayMetrics.heightPixels * maxFraction).toInt()
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST))
    }
}

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
