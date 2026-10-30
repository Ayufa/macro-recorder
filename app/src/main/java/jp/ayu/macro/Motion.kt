package jp.ayu.macro

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator

/**
 * v0.7: 控えめな動き（Motion SV / sv-animations の "blur fade" や押し込みの感じを参考に、ネイティブで作り直し）。
 * すべて端末の「アニメーションを削除」/ アニメーション スケール 0 のときは何もしない。
 */
object Motion {
    /** 速く出て、ゆっくり止まる */
    val easeOut = PathInterpolator(0.16f, 1f, 0.3f, 1f)
    private val pressEase = PathInterpolator(0.2f, 0f, 0f, 1f)

    fun enabled(ctx: Context): Boolean =
        ValueAnimator.areAnimatorsEnabled() &&
            Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f

    /**
     * 出てくるときの動き: 下から少し上がりながらフェードイン。Android 12+ ではぼかしも同時にほどく。
     * index ごとに少しずつ遅らせて、順番に現れるようにする。
     */
    fun enter(v: View, index: Int = 0, distanceDp: Float = BLUR_FADE_OFFSET_DP) {
        if (!enabled(v.context)) return
        val d = distanceDp * v.resources.displayMetrics.density
        val blurMax = BLUR_FADE_BLUR_DP * v.resources.displayMetrics.density
        v.alpha = 0f; v.translationY = d
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = BLUR_FADE_DURATION_MS
            startDelay = BLUR_FADE_BASE_DELAY_MS + STAGGER_MS * index.coerceIn(0, 10)
            interpolator = blurFadeEase
            addUpdateListener {
                val t = it.animatedValue as Float
                v.alpha = t.coerceIn(0f, 1f); v.translationY = (1f - t) * d
                if (Build.VERSION.SDK_INT >= 31) {
                    val r = (1f - t) * blurMax
                    v.setRenderEffect(if (r < 0.5f) null else RenderEffect.createBlurEffect(r, r, Shader.TileMode.DECAL))
                }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    v.alpha = 1f; v.translationY = 0f
                    if (Build.VERSION.SDK_INT >= 31) v.setRenderEffect(null)
                }
            })
            start()
        }
    }

    // v0.8: BlurFade（SikandarJODD/animations, MIT）の既定値をそのまま移植
    //   duration 0.4s / delay 0.04s + n / offset 6px / blur 6px / ease "easeOut" = cubic-bezier(0, 0, 0.58, 1)
    private const val BLUR_FADE_DURATION_MS = 400L
    private const val BLUR_FADE_BASE_DELAY_MS = 40L
    private const val BLUR_FADE_OFFSET_DP = 6f
    private const val BLUR_FADE_BLUR_DP = 6f
    private const val STAGGER_MS = 45L
    private val blurFadeEase = PathInterpolator(0f, 0f, 0.58f, 1f)

    /** v0.8: 選んだ・切り替えたときの小さな「ポン」（60fps.design で見たマイクロインタラクションの再現） */
    fun pop(v: View, peak: Float = 1.22f) {
        if (!enabled(v.context)) return
        v.scaleX = 0.82f; v.scaleY = 0.82f
        v.animate().scaleX(1f).scaleY(1f).setDuration(420)
            .setInterpolator(android.view.animation.OvershootInterpolator(peak * 3f)).start()
    }

    /**
     * v0.8: 触覚フィードバック。View.performHapticFeedback なので VIBRATE 権限は不要で、
     * 端末の「タッチ操作時のバイブ」設定に従う（オフなら鳴らない）。
     */
    enum class Haptic { TICK, CONFIRM, LONG_PRESS, REJECT }

    fun haptic(v: View, kind: Haptic) {
        val c = when (kind) {
            Haptic.TICK -> if (Build.VERSION.SDK_INT >= 27) android.view.HapticFeedbackConstants.KEYBOARD_RELEASE
                else android.view.HapticFeedbackConstants.KEYBOARD_TAP
            Haptic.CONFIRM -> if (Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.CONFIRM
                else android.view.HapticFeedbackConstants.VIRTUAL_KEY
            Haptic.LONG_PRESS -> android.view.HapticFeedbackConstants.LONG_PRESS
            Haptic.REJECT -> if (Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.REJECT
                else android.view.HapticFeedbackConstants.LONG_PRESS
        }
        v.performHapticFeedback(c)
    }

    /** 押している間だけ少し縮む（クリック処理には触れない） */
    @SuppressLint("ClickableViewAccessibility")
    fun pressable(v: View, scale: Float = 0.95f) {
        v.setOnTouchListener { view, e ->
            if (!enabled(view.context)) return@setOnTouchListener false
            // 向きを反転しているボタン（scaleX = -1）でも反転を保つ
            val sx = if (view.scaleX < 0f) -1f else 1f
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.animate().scaleX(sx * scale).scaleY(scale)
                    .setDuration(110).setInterpolator(pressEase).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(sx).scaleY(1f)
                    .setDuration(260).setInterpolator(easeOut).start()
            }
            false
        }
    }
}
