package jp.ayu.macro

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * v0.9: アプリ内のスナックバー（android-design-guidelines R5.14 / R5.16: 取り消せる操作の通知はトーストではなくスナックバー、
 * 削除は確認ダイアログではなく「元に戻す」付きで）。AndroidX を使わないので最小限を自前で。
 *
 * - 色は inverseSurface / inverseOnSurface、操作ボタンは inversePrimary
 * - 高さ 48dp 以上、操作ボタンも 48dp（R6.5）
 * - 表示時間はユーザー補助の「操作を行うまでの時間」設定に従う（getRecommendedTimeoutMillis, R6.12）
 * - 読み上げ: ライブ リージョン（R6.1）。アニメーションを削除している端末では動かさない
 * - 画面下のシステムバーの上に出す（host 側で inset 分の余白をとる）
 */
class Snackbar private constructor(private val host: FrameLayout, private val ui: Ui) {
    private var view: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hide() }

    fun show(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        handler.removeCallbacks(hideRunnable)
        view?.let { host.removeView(it) }
        val ctx = host.context
        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = ui.dp(48)
            setPadding(ui.dp(16), ui.dp(4), ui.dp(8), ui.dp(4))
            background = ui.rounded(ui.inverseSurface, 8)
            elevation = ui.dp(6).toFloat()
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        bar.addView(ui.text(message, Ui.Type.BODY_MEDIUM, ui.inverseOnSurface).apply {
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, ui.dp(10), ui.dp(8), ui.dp(10))
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (actionLabel != null && onAction != null) {
            bar.addView(TextView(ctx).apply {
                text = actionLabel; textSize = Ui.Type.LABEL_LARGE; typeface = ui.font(Ui.Weight.MEDIUM)
                setTextColor(ui.inversePrimary); gravity = Gravity.CENTER
                minHeight = ui.dp(48); minWidth = ui.dp(48)
                setPadding(ui.dp(12), 0, ui.dp(12), 0)
                isClickable = true; isFocusable = true
                background = ui.ripple(null, ui.rounded(android.graphics.Color.WHITE, 999), ui.inverseOnSurface)
                setOnClickListener { onAction(); hide() }
            })
        }
        val maxW = ui.dp(600)
        val lp = FrameLayout.LayoutParams(
            if (host.width > maxW + ui.dp(32)) maxW else FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
        ).apply { setMargins(ui.dp(16), 0, ui.dp(16), ui.dp(16)) }
        host.addView(bar, lp)
        view = bar
        if (Motion.enabled(ctx)) {
            bar.alpha = 0f; bar.translationY = ui.dp(16).toFloat()
            bar.animate().alpha(1f).translationY(0f).setDuration(220).setInterpolator(Motion.easeOut).start()
        }
        handler.postDelayed(hideRunnable, timeout(actionLabel != null))
    }

    private fun timeout(hasAction: Boolean): Long {
        val base = if (hasAction) 6000 else 3000
        if (Build.VERSION.SDK_INT < 29) return base.toLong()
        val am = host.context.getSystemService(AccessibilityManager::class.java) ?: return base.toLong()
        val flags = AccessibilityManager.FLAG_CONTENT_TEXT or (if (hasAction) AccessibilityManager.FLAG_CONTENT_CONTROLS else 0)
        return am.getRecommendedTimeoutMillis(base, flags).toLong()
    }

    fun hide() {
        handler.removeCallbacks(hideRunnable)
        val v = view ?: return
        view = null
        if (Motion.enabled(host.context)) {
            v.animate().alpha(0f).translationY(ui.dp(16).toFloat()).setDuration(160)
                .withEndAction { host.removeView(v) }.start()
        } else host.removeView(v)
    }

    companion object {
        /** host（画面全体を覆う FrameLayout）ごとに 1 つ */
        fun of(host: FrameLayout, ui: Ui): Snackbar =
            (host.getTag(R.id.snackbar_host) as? Snackbar) ?: Snackbar(host, ui).also { host.setTag(R.id.snackbar_host, it) }
    }
}
