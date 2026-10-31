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
    /** v0.10: イージングは画面全体でこの 1 本だけ（unslop のトークン cubic-bezier(0.22, 1, 0.36, 1)） */
    val easeOut = PathInterpolator(0.22f, 1f, 0.36f, 1f)
    private val pressEase = easeOut

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
            // v0.10: 登場は控えめに。ずらすのは先頭の 5 つまで
            startDelay = BLUR_FADE_BASE_DELAY_MS + STAGGER_MS * index.coerceIn(0, 5)
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

    // v0.8: BlurFade（SikandarJODD/animations, MIT）の既定値を移植
    //   duration 0.4s / delay 0.04s + n / offset 6px / blur 6px
    // v0.10: ease は画面全体で 1 本に統一したため、BlurFade 専用の ease は使わない
    private const val BLUR_FADE_DURATION_MS = 400L
    private const val BLUR_FADE_BASE_DELAY_MS = 40L
    private const val BLUR_FADE_OFFSET_DP = 6f
    private const val BLUR_FADE_BLUR_DP = 6f
    private const val STAGGER_MS = 45L
    private val blurFadeEase = easeOut

    /** v0.10: 選んだときの合図。バウンス（オーバーシュート）はやめ、小さく沈んで easeOut で戻るだけにした */
    fun pop(v: View) {
        if (!enabled(v.context)) return
        v.scaleX = 0.96f; v.scaleY = 0.96f
        v.animate().scaleX(1f).scaleY(1f).setDuration(200)
            .setInterpolator(easeOut).start()
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

    /** 押している間だけ少し縮む（クリック処理には触れない）。v0.10: 縮みは 0.97、時間は 120 / 200ms に統一 */
    @SuppressLint("ClickableViewAccessibility")
    fun pressable(v: View, scale: Float = 0.97f) {
        v.setOnTouchListener { view, e ->
            if (!enabled(view.context)) return@setOnTouchListener false
            // 向きを反転しているボタン（scaleX = -1）でも反転を保つ
            val sx = if (view.scaleX < 0f) -1f else 1f
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.animate().scaleX(sx * scale).scaleY(scale)
                    .setDuration(120).setInterpolator(pressEase).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(sx).scaleY(1f)
                    .setDuration(200).setInterpolator(easeOut).start()
            }
            false
        }
    }
}
