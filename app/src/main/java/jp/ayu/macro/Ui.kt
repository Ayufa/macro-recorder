package jp.ayu.macro

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageButton
import android.widget.TextView

/** Material 3 風の見た目を、AndroidX なし（フレームワークのみ）で組み立てるための小さなヘルパー */
class Ui(val ctx: Context) {
    fun dp(v: Number): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()

    fun c(id: Int): Int = ctx.getColor(id)

    val primary get() = c(R.color.md_primary)
    val onPrimary get() = c(R.color.md_on_primary)
    val primaryContainer get() = c(R.color.md_primary_container)
    val onPrimaryContainer get() = c(R.color.md_on_primary_container)
    val secondaryContainer get() = c(R.color.md_secondary_container)
    val onSecondaryContainer get() = c(R.color.md_on_secondary_container)
    val surface get() = c(R.color.md_surface)
    val surfaceContainer get() = c(R.color.md_surface_container)
    val surfaceContainerHigh get() = c(R.color.md_surface_container_high)
    val onSurface get() = c(R.color.md_on_surface)
    val onSurfaceVariant get() = c(R.color.md_on_surface_variant)
    val outlineVariant get() = c(R.color.md_outline_variant)
    val error get() = c(R.color.md_error)
    val errorContainer get() = c(R.color.md_error_container)
    val onErrorContainer get() = c(R.color.md_on_error_container)
    val record get() = c(R.color.md_record)

    fun rounded(color: Int, radiusDp: Number, strokeColor: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(radiusDp).toFloat(); setColor(color)
            if (strokeColor != null) setStroke(dp(1), strokeColor)
        }

    fun oval(color: Int): GradientDrawable = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }

    /** 押したときに波紋が出る背景 */
    fun ripple(content: Drawable?, mask: Drawable, rippleBase: Int = onSurface): RippleDrawable =
        RippleDrawable(ColorStateList.valueOf(withAlpha(rippleBase, 0.14f)), content, mask)

    fun withAlpha(color: Int, a: Float) = Color.argb((255 * a).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    fun icon(id: Int, tint: Int): Drawable = ctx.getDrawable(id)!!.mutate().apply { setTint(tint) }

    /** 丸いアイコンボタン（M3 Icon button / Filled icon button） */
    fun iconButton(iconId: Int, desc: String, sizeDp: Int, onClick: () -> Unit): ImageButton =
        ImageButton(ctx).apply {
            contentDescription = desc
            tooltipText = desc
            scaleType = android.widget.ImageView.ScaleType.CENTER
            setImageDrawable(icon(iconId, onSurfaceVariant))
            val pad = dp((sizeDp - 24) / 2f)
            setPadding(pad, pad, pad, pad)
            background = ripple(null, oval(Color.WHITE))
            setOnClickListener { onClick() }
        }

    fun styleIconButton(b: ImageButton, iconId: Int, fg: Int, bg: Int?) {
        b.setImageDrawable(icon(iconId, fg))
        b.background = ripple(bg?.let { oval(it) }, oval(Color.WHITE), if (bg != null) fg else onSurface)
    }

    /** M3 のボタン（filled / tonal / text） */
    enum class BtnStyle { FILLED, TONAL, TEXT }

    fun button(label: String, style: BtnStyle, iconId: Int? = null, onClick: () -> Unit): TextView =
        TextView(ctx).apply {
            text = label; textSize = 14f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER; minHeight = dp(40); isClickable = true; isFocusable = true
            setPadding(dp(if (iconId != null) 16 else 24), 0, dp(24), 0)
            val (bg, fg) = when (style) {
                BtnStyle.FILLED -> primary to onPrimary
                BtnStyle.TONAL -> secondaryContainer to onSecondaryContainer
                BtnStyle.TEXT -> null to primary
            }
            if (style == BtnStyle.TEXT) setPadding(dp(12), 0, dp(12), 0)
            setTextColor(fg)
            background = ripple(bg?.let { rounded(it, 20) }, rounded(Color.WHITE, 20), fg)
            if (iconId != null) {
                val d = icon(iconId, fg).apply { setBounds(0, 0, dp(18), dp(18)) }
                setCompoundDrawablesRelative(d, null, null, null); compoundDrawablePadding = dp(8)
            }
            setOnClickListener { onClick() }
        }

    fun text(s: CharSequence, sizeSp: Float, color: Int, medium: Boolean = false): TextView = TextView(ctx).apply {
        text = s; textSize = sizeSp; setTextColor(color)
        if (medium) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setLineSpacing(0f, 1.15f)
    }

    /** 小さなラベル（チップ風） */
    fun chip(s: String): TextView = text(s, 12f, onSurfaceVariant).apply {
        setPadding(dp(10), dp(4), dp(10), dp(4))
        background = rounded(Color.TRANSPARENT, 8, outlineVariant)
    }
}
