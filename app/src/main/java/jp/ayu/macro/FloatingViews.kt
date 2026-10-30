package jp.ayu.macro

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.hypot

/**
 * パネル全体をドラッグで動かせるようにするコンテナ。
 * 指が少し動いたら（タッチスロップ超え）子ボタンのクリックを奪ってドラッグとして扱う。
 */
@SuppressLint("ViewConstructor")
class DragFrame(
    context: Context,
    private val onMove: (dx: Int, dy: Int, start: Boolean) -> Unit,
    private val onEnd: () -> Unit,
) : FrameLayout(context) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var dragging = false

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; dragging = false; onMove(0, 0, true) }
            MotionEvent.ACTION_MOVE -> if (!dragging && hypot(e.rawX - downX, e.rawY - downY) > slop) {
                dragging = true; return true
            }
        }
        return dragging
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; dragging = false; onMove(0, 0, true) }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && hypot(e.rawX - downX, e.rawY - downY) > slop) dragging = true
                if (dragging) onMove((e.rawX - downX).toInt(), (e.rawY - downY).toInt(), false)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { if (dragging) onEnd(); dragging = false }
        }
        return true
    }
}

/**
 * 折りたたみ時の丸いバブル（チャットヘッド風, 48dp）。
 * 記録中: 赤いリングが脈打ち、右上に赤い点。 再生中: 進捗リング。
 */
@SuppressLint("ViewConstructor")
class BubbleView(context: Context, private val ui: Ui) : View(context) {
    enum class Mode { IDLE, RECORDING, PLAYING }

    var mode = Mode.IDLE
        set(v) { if (field != v) { field = v; updateAnim(); refreshIcon(); contentDescriptionUpdate() }; invalidate() }

    /** 0..1。再生中の進み具合 */
    var progress = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }

    private var pulse = 0f
    private var anim: ValueAnimator? = null
    private var iconDrawable: Drawable? = null
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val oval = RectF()

    init {
        isClickable = true; isFocusable = true
        elevation = ui.dp(6).toFloat()
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        background = ui.ripple(null, ui.oval(android.graphics.Color.WHITE))
        refreshIcon(); contentDescriptionUpdate()
    }

    private fun contentDescriptionUpdate() {
        contentDescription = when (mode) {
            Mode.IDLE -> "操作マクロ（タップで開く）"
            Mode.RECORDING -> "記録中（タップで開く）"
            Mode.PLAYING -> "再生中（タップで開く）"
        }
    }

    private fun refreshIcon() {
        val id = if (mode == Mode.PLAYING) R.drawable.ic_play else R.drawable.ic_touch
        iconDrawable = ui.icon(id, ui.onPrimaryContainer)
    }

    private fun updateAnim() {
        anim?.cancel(); anim = null; pulse = 0f
        if (mode == Mode.RECORDING) {
            anim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1100; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE
                addUpdateListener { pulse = it.animatedValue as Float; invalidate() }
                start()
            }
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); updateAnim() }
    override fun onDetachedFromWindow() { anim?.cancel(); anim = null; super.onDetachedFromWindow() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val s = ui.dp(48); setMeasuredDimension(s, s)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val cx = w / 2; val cy = h / 2
        fill.color = ui.primaryContainer
        canvas.drawCircle(cx, cy, w / 2, fill)
        val sw = ui.dp(3).toFloat()
        oval.set(sw / 2 + ui.dp(1), sw / 2 + ui.dp(1), w - sw / 2 - ui.dp(1), h - sw / 2 - ui.dp(1))
        ring.strokeWidth = sw
        when (mode) {
            Mode.RECORDING -> {
                ring.color = ui.withAlpha(ui.record, 0.45f + 0.55f * pulse)
                canvas.drawOval(oval, ring)
            }
            Mode.PLAYING -> {
                ring.color = ui.withAlpha(ui.onPrimaryContainer, 0.18f)
                canvas.drawOval(oval, ring)
                ring.color = ui.primary
                canvas.drawArc(oval, -90f, 360f * progress.coerceAtLeast(0.02f), false, ring)
            }
            Mode.IDLE -> {}
        }
        iconDrawable?.let {
            val s = ui.dp(24); val l = (cx - s / 2).toInt(); val t = (cy - s / 2).toInt()
            it.setBounds(l, t, l + s, t + s); it.draw(canvas)
        }
        if (mode == Mode.RECORDING) {
            val r = ui.dp(6).toFloat(); val bx = w - r - ui.dp(1); val by = r + ui.dp(1)
            fill.color = ui.surface; canvas.drawCircle(bx, by, r + ui.dp(1.5f), fill)
            fill.color = ui.record; canvas.drawCircle(bx, by, r, fill)
        }
    }

}
