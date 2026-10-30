package jp.ayu.macro

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * v0.7: 見出しの背景に敷く、端に向かって消えていく細い方眼。
 * sv-animations のトップにある方眼の背景の雰囲気を参考に、Canvas で描いています（素材の流用なし）。
 */
class GridDrawable(private val lineColor: Int, private val cellPx: Float, private val linePx: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = linePx; style = Paint.Style.STROKE }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        val w = bounds.width().toFloat(); val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return
        // 中央上寄りを濃く、外側ほど透明に
        paint.shader = RadialGradient(bounds.left + w * 0.5f, bounds.top + h * 0.35f, maxOf(w, h) * 0.65f,
            intArrayOf(lineColor, lineColor and 0x00FFFFFF), floatArrayOf(0.15f, 1f), Shader.TileMode.CLAMP)
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        var x = b.left + (b.width() % cellPx) / 2f
        while (x <= b.right) { canvas.drawLine(x, b.top.toFloat(), x, b.bottom.toFloat(), paint); x += cellPx }
        var y = b.top + cellPx / 2f
        while (y <= b.bottom) { canvas.drawLine(b.left.toFloat(), y, b.right.toFloat(), y, paint); y += cellPx }
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
