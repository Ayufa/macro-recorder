package jp.ayu.macro

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max

/**
 * v0.7: ドットマトリクスの小さなインジケーター。
 * sv-matrix（Svelte Dot Matrix Loaders）の「点の格子が波のように明滅する」雰囲気を参考に、Canvas でゼロから描いています
 * （元のコードやパターンデータは使っていません）。
 * アニメーションを削除する設定のときは、止まった模様を表示します。
 */
class DotMatrixView(
    context: Context,
    private val cols: Int = 3,
    private val rows: Int = 3,
    private val dotPx: Float,
    private val gapPx: Float,
) : View(context) {
    /** SNAKE は sv-matrix の "path-wave"（蛇行の順で波が流れる）を移植したもの */
    enum class Pattern { WAVE, PULSE, SNAKE }

    var color: Int = 0xFF000000.toInt()
        set(v) { field = v; invalidate() }
    var pattern = Pattern.WAVE
        set(v) { field = v; invalidate() }

    private var phase = 0.3f
    private var anim: ValueAnimator? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = cols * dotPx + (cols - 1) * gapPx
        val h = rows * dotPx + (rows - 1) * gapPx
        setMeasuredDimension(resolveSize(w.toInt() + 1, widthMeasureSpec), resolveSize(h.toInt() + 1, heightMeasureSpec))
    }

    private fun intensity(c: Int, r: Int): Float {
        val two = 2.0 * PI
        return when (pattern) {
            // 左上から右下へ流れる波
            Pattern.WAVE -> {
                val k = (c + r).toFloat() / max(1, cols + rows - 2)
                (0.5 - 0.5 * cos(two * (phase - k * 0.8))).toFloat()
            }
            // 中心から外へ広がる輪
            Pattern.SNAKE -> snakeNorm(c, r)
            Pattern.PULSE -> {
                val cx = (cols - 1) / 2f; val cy = (rows - 1) / 2f
                val maxD = hypot(cx, cy).coerceAtLeast(1f)
                val d = hypot(c - cx, r - cy) / maxD
                (0.5 - 0.5 * cos(two * (phase - d * 0.6))).toFloat()
            }
        }
    }

    /** sv-matrix (MIT): 蛇行順（偶数行は左→右、奇数行は右→左）を 0..1 に正規化 */
    private fun snakeNorm(c: Int, r: Int): Float {
        val order = r * cols + (if (r % 2 == 0) c else cols - 1 - c)
        return order.toFloat() / (cols * rows - 1).coerceAtLeast(1)
    }

    /**
     * sv-matrix (MIT) の dmx-ripple キーフレームを移植:
     * 0%/100% = base 0.16, 50% = peak 1.0, ease-in-out cubic-bezier(0.42, 0, 0.58, 1),
     * 各点の遅れ = path × 0.2333 × cycle。アニメーションなしのときは 0.12 + path × 0.72 の静止画。
     */
    private fun snakeAlpha(c: Int, r: Int): Float {
        val path = snakeNorm(c, r)
        if (anim == null) return 0.12f + path * 0.72f
        var t = (phase - path * 0.2333f) % 1f; if (t < 0f) t += 1f
        val half = if (t < 0.5f) t * 2f else (1f - t) * 2f
        return 0.16f + (1f - 0.16f) * rippleEase.getInterpolation(half)
    }
    private val rippleEase = android.view.animation.PathInterpolator(0.42f, 0f, 0.58f, 1f)

    override fun onDraw(canvas: Canvas) {
        val gw = cols * dotPx + (cols - 1) * gapPx; val gh = rows * dotPx + (rows - 1) * gapPx
        val ox = (width - gw) / 2f; val oy = (height - gh) / 2f
        val rad = dotPx / 2f
        val baseA = android.graphics.Color.alpha(color) / 255f
        for (r in 0 until rows) for (c in 0 until cols) {
            val a = if (pattern == Pattern.SNAKE) snakeAlpha(c, r) else 0.16f + 0.84f * intensity(c, r)
            paint.color = color; paint.alpha = (255 * a * baseA).toInt()
            canvas.drawCircle(ox + c * (dotPx + gapPx) + rad, oy + r * (dotPx + gapPx) + rad, rad, paint)
        }
    }

    private fun start() {
        anim?.cancel(); anim = null
        if (!isAttachedToWindow || visibility != VISIBLE || !Motion.enabled(context)) { invalidate(); return }
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
            addUpdateListener { phase = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); start() }
    override fun onDetachedFromWindow() { anim?.cancel(); anim = null; super.onDetachedFromWindow() }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (isAttachedToWindow) { if (isShown) start() else { anim?.cancel(); anim = null } }
    }
}
