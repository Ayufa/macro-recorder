package jp.ayu.macro

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View

/**
 * 記録中に全画面へ重ねる透明ビュー。
 * 自分自身へのタッチ（座標と時間）だけを受け取り、1ジェスチャー分を [onGesture] で渡す。
 * 他アプリの画面内容は一切読み取らない。
 */
@SuppressLint("ViewConstructor")
class CaptureView(
    context: Context,
    private val onGesture: (gestureDownUptime: Long, gestureUpUptime: Long, strokes: List<Stroke>) -> Unit,
) : View(context) {

    private class Builder(val startOffset: Long, val startTime: Long) {
        val points = ArrayList<TouchPoint>()
    }

    private val active = HashMap<Int, Builder>()
    private val finished = ArrayList<Stroke>()
    private var downTime = 0L
    private val loc = IntArray(2)
    private val trail = Path()
    var passThrough = false
        set(v) { field = v; invalidate() }

    private val borderPaint = Paint().apply {
        style = Paint.Style.STROKE; strokeWidth = 8f; color = Color.argb(200, 229, 57, 53)
    }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 10f; color = Color.argb(160, 229, 57, 53)
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(if (passThrough) Color.argb(18, 255, 193, 7) else Color.argb(14, 229, 57, 53))
        borderPaint.color = if (passThrough) Color.argb(200, 255, 160, 0) else Color.argb(200, 229, 57, 53)
        canvas.drawRect(4f, 4f, width - 4f, height - 4f, borderPaint)
        canvas.drawPath(trail, trailPaint)
    }

    private fun add(b: Builder, ev: MotionEvent, idx: Int, x: Float, y: Float, time: Long) {
        val sx = x + loc[0]
        val sy = y + loc[1]
        b.points.add(TouchPoint(sx, sy, (time - b.startTime).coerceAtLeast(0)))
        if (b.points.size == 1) trail.moveTo(x, y) else trail.lineTo(x, y)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (passThrough) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                getLocationOnScreen(loc)
                active.clear(); finished.clear(); trail.reset()
                downTime = ev.eventTime
                val b = Builder(0, ev.eventTime)
                active[ev.getPointerId(0)] = b
                add(b, ev, 0, ev.getX(0), ev.getY(0), ev.eventTime)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val i = ev.actionIndex
                val b = Builder(ev.eventTime - downTime, ev.eventTime)
                active[ev.getPointerId(i)] = b
                add(b, ev, i, ev.getX(i), ev.getY(i), ev.eventTime)
            }
            MotionEvent.ACTION_MOVE -> {
                for (h in 0 until ev.historySize) {
                    for (i in 0 until ev.pointerCount) {
                        val b = active[ev.getPointerId(i)] ?: continue
                        add(b, ev, i, ev.getHistoricalX(i, h), ev.getHistoricalY(i, h), ev.getHistoricalEventTime(h))
                    }
                }
                for (i in 0 until ev.pointerCount) {
                    val b = active[ev.getPointerId(i)] ?: continue
                    add(b, ev, i, ev.getX(i), ev.getY(i), ev.eventTime)
                }
                invalidate()
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val i = ev.actionIndex
                active.remove(ev.getPointerId(i))?.let { b ->
                    add(b, ev, i, ev.getX(i), ev.getY(i), ev.eventTime)
                    finished.add(Stroke(b.startOffset, ReplayMath.simplify(b.points)))
                }
            }
            MotionEvent.ACTION_UP -> {
                active.remove(ev.getPointerId(0))?.let { b ->
                    add(b, ev, 0, ev.getX(0), ev.getY(0), ev.eventTime)
                    finished.add(Stroke(b.startOffset, ReplayMath.simplify(b.points)))
                }
                active.values.forEach { b -> finished.add(Stroke(b.startOffset, ReplayMath.simplify(b.points))) }
                active.clear()
                val strokes = finished.toList()
                finished.clear()
                invalidate()
                if (strokes.isNotEmpty()) onGesture(downTime, ev.eventTime, strokes)
            }
            MotionEvent.ACTION_CANCEL -> {
                active.clear(); finished.clear(); trail.reset(); invalidate()
            }
        }
        return true
    }

    fun clearTrail() { trail.reset(); invalidate() }
}
