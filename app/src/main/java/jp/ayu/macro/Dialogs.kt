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
            setPadding(dp(24), dp(24), dp(24), dp(16))
            background = ui.rounded(ui.surfaceContainerHigh, 24, ui.outlineVariant)
            elevation = dp(8).toFloat()
        }
        if (iconId != null) {
            root.addView(ImageView(ctx).apply {
                setImageDrawable(ui.icon(iconId, if (destructive) ui.error else ui.onSurface))
                background = ui.oval(if (destructive) ui.errorContainer else ui.secondaryContainer)
                scaleType = ImageView.ScaleType.CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { bottomMargin = dp(16) })
        }
        root.addView(ui.text(title, 20f, ui.onSurface, weight = Ui.Weight.SEMIBOLD))
        if (message != null) root.addView(ui.text(message, 14f, ui.onSurfaceVariant).apply { setPadding(0, dp(8), 0, 0) })
        if (content != null) root.addView(content, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(16) })
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        row.setPadding(0, dp(20), 0, 0)
        val cancel = cancelLabel?.let { ui.button(it, Ui.BtnStyle.TEXT) {} }
        if (cancel != null) row.addView(cancel)
        val confirm = ui.button(confirmLabel, if (destructive) Ui.BtnStyle.DANGER else Ui.BtnStyle.FILLED) {}
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
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val w = minOf(activity.resources.displayMetrics.widthPixels - ui.dp(32), ui.dp(420))
            setLayout(w, ViewGroup.LayoutParams.WRAP_CONTENT)
            setDimAmount(0.32f)
            if (showKeyboard) setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        }
        d.show()
        return d
    }

    /** 選択肢の一覧（言語の選択など）。選択中にチェック */
    fun choice(activity: Activity, title: String, labels: List<String>, selected: Int, onPick: (Int) -> Unit) {
        val ui = Ui(activity)
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        lateinit var dlg: Dialog
        labels.forEachIndexed { i, l ->
            val on = i == selected
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                minimumHeight = ui.dp(48); setPadding(ui.dp(14), 0, ui.dp(10), 0)
                background = ui.ripple(if (on) ui.rounded(ui.secondaryContainer, 12) else null, ui.rounded(Color.WHITE, 12))
                isClickable = true; isFocusable = true
                setOnClickListener { dlg.dismiss(); if (i != selected) onPick(i) }
            }
            row.addView(ui.text(l, 15f, ui.onSurface, medium = on), LinearLayout.LayoutParams(0, WRAP, 1f))
            if (on) row.addView(ImageView(activity).apply { setImageDrawable(ui.icon(R.drawable.ic_check, ui.primary)) },
                LinearLayout.LayoutParams(ui.dp(20), ui.dp(20)))
            row.isSelected = on
            list.addView(row, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = ui.dp(2) })
        }
        dlg = show(activity, title, content = list, iconId = R.drawable.ic_language,
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
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setDimAmount(0.32f)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            setWindowAnimations(0)
        }
    }

    private fun animated() = android.animation.ValueAnimator.areAnimatorsEnabled()

    override fun show() {
        super.show()
        if (!animated()) return
        sheet.translationY = activity.resources.displayMetrics.heightPixels.toFloat()
        sheet.post {
            sheet.translationY = sheet.height.toFloat()
            sheet.animate().translationY(0f).setDuration(380).setInterpolator(ease).start()
        }
    }

    override fun dismiss() {
        if (closing) return
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
