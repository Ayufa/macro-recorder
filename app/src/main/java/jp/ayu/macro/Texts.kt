package jp.ayu.macro

import android.content.Context
import java.text.NumberFormat

/** 画面に出す数値・要約の組み立て（文言はすべて strings.xml） */
object Texts {
    private fun locale(ctx: Context) = ctx.resources.configuration.locales[0]

    /** 1, 1.5, 0.75 のように、不要な 0 を付けずに表示 */
    fun num(ctx: Context, v: Double): String = NumberFormat.getNumberInstance(locale(ctx)).apply {
        maximumFractionDigits = 2; minimumFractionDigits = 0; isGroupingUsed = false
    }.format(v)

    fun seconds(ctx: Context, ms: Long) = ctx.getString(R.string.value_seconds, num(ctx, ms / 1000.0))
    fun speed(ctx: Context, s: Float) = ctx.getString(R.string.value_speed, num(ctx, s.toDouble()))
    fun steps(ctx: Context, n: Int) = ctx.resources.getQuantityString(R.plurals.steps_count, n, n)

    fun repeat(ctx: Context, count: Int) =
        if (count == 0) ctx.getString(R.string.repeat_infinite) else ctx.resources.getQuantityString(R.plurals.repeat_times, count, count)

    /** 「タップ3・スワイプ1」のような内訳 */
    fun kinds(ctx: Context, steps: List<Step>): String {
        val r = ctx.resources
        return steps.groupingBy { it.kind() to (if (it.kind() == Step.Kind.MULTI) it.strokes.size else 0) }
            .eachCount().entries.joinToString(ctx.getString(R.string.list_separator)) { (k, n) ->
                when (k.first) {
                    Step.Kind.TAP -> r.getQuantityString(R.plurals.kind_tap, n, n)
                    Step.Kind.LONG_PRESS -> r.getQuantityString(R.plurals.kind_long_press, n, n)
                    Step.Kind.SWIPE -> r.getQuantityString(R.plurals.kind_swipe, n, n)
                    Step.Kind.MULTI -> r.getQuantityString(R.plurals.kind_multi, n, k.second, n)
                    Step.Kind.EMPTY -> r.getQuantityString(R.plurals.kind_empty, n, n)
                }
            }
    }
}
