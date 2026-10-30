package jp.ayu.macro

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

/**
 * 見た目を AndroidX なし（フレームワークのみ）で組み立てるための小さなヘルパー。
 * v0.6: designeer.xyz の見た目を参考にしたデザイン
 *  - ニュートラルな面（薄いグレーの画面 + 白いカード + 細い線）、角丸 20dp のカード / 24dp のシート / 14dp のトレイ
 *  - ボタン・チップは丸いピル型。主ボタンは墨色（ink）、アクセント色は選択状態やスライダーに使う
 *  - 書体は Funnel Sans（SIL OFL 1.1。assets/fonts に同梱）。漢字・かな・ハングルは端末のフォントで表示
 * v0.7: sv-table / sv-blocks / sv-agentation などの見た目を参考に洗練（コードや素材は流用せず、ネイティブで作り直し）
 *  - 数値・ラベル・バッジは等幅の Geist Mono（SIL OFL 1.1）。セクション見出しは小さな大文字＋字間広め＋細い線
 *  - ボーダーは半透明ではなく実線の細い線（#E4E4E7 / ダーク #232327）、一覧は表のように区切り線で並べる
 *  - 押したときに少し縮むフィードバック、出てくるときのぼかし＋フェード＋スライド（Motion.kt）
 */
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
    /** v0.9: 「高コントラストのテキスト」がオンのときは、補助的な文字色も onSurface にそろえる（R6.9） */
    val onSurfaceVariant get() = if (highContrast) onSurface else c(R.color.md_on_surface_variant)
    val ink3 get() = if (highContrast) onSurface else c(R.color.md_ink_3)
    val inversePrimary get() = c(R.color.md_inverse_primary)
    val highContrast: Boolean get() = Build.VERSION.SDK_INT >= 36 && runCatching {
        ctx.getSystemService(android.view.accessibility.AccessibilityManager::class.java)?.isHighContrastTextEnabled == true
    }.getOrDefault(false)
    val outline get() = c(R.color.md_outline)
    val outlineVariant get() = c(R.color.md_outline_variant)
    val inverseSurface get() = c(R.color.md_inverse_surface)
    val inverseOnSurface get() = c(R.color.md_inverse_on_surface)
    val error get() = c(R.color.md_error)
    val onError get() = c(R.color.md_on_error)
    val errorContainer get() = c(R.color.md_error_container)
    val onErrorContainer get() = c(R.color.md_on_error_container)
    val record get() = c(R.color.md_record)
    val success get() = c(R.color.md_success)
    val successContainer get() = c(R.color.md_success_container)
    val grid get() = c(R.color.md_grid)

    // ---------- 書体 ----------
    enum class Weight(val w: Int) { REGULAR(400), MEDIUM(500), SEMIBOLD(600) }

    /**
     * v0.9: 文字サイズは Material 3 の型の役割（type scale, sp）にそろえる（android-design-guidelines R4.1–R4.3）。
     * 本文は 12sp 以上、11sp はラベル（バッジ・小見出し）だけ（R4.2）。
     * 間隔は 4dp の倍数（4 / 8 / 12 / 16 / 24）を使う。
     */
    object Type {
        const val HEADLINE_SMALL = 24f
        const val TITLE_LARGE = 22f
        const val TITLE_MEDIUM = 16f
        const val BODY_LARGE = 16f
        const val BODY_MEDIUM = 14f
        const val BODY_SMALL = 12f
        const val LABEL_LARGE = 14f
        const val LABEL_MEDIUM = 12f
        const val LABEL_SMALL = 11f
    }

    /** v0.9: 「太字テキスト」設定（Android 12+ の fontWeightAdjustment）を書体の太さに足す（R6.9） */
    private fun adjusted(w: Int): Int {
        if (Build.VERSION.SDK_INT < 31) return w
        val adj = ctx.resources.configuration.fontWeightAdjustment
        if (adj == android.content.res.Configuration.FONT_WEIGHT_ADJUSTMENT_UNDEFINED || adj == 0) return w
        return (w + adj).coerceIn(1, 1000)
    }

    fun font(weight: Weight): Typeface = adjusted(weight.w).let { aw -> fonts.getOrPut(aw) {
        runCatching {
            Typeface.Builder(ctx.assets, "fonts/FunnelSans-wght.ttf")
                .setFontVariationSettings("'wght' $aw")
                .setWeight(aw)
                .setFallback("sans-serif")
                .build()
        }.getOrNull() ?: Typeface.create(if (aw < 500) "sans-serif" else "sans-serif-medium", Typeface.NORMAL)
    } }

    /** v0.7: 等幅（Geist Mono）。数字・バッジ・小さな見出し用。CJK は端末のフォントで表示 */
    fun mono(weight: Weight = Weight.REGULAR): Typeface = adjusted(weight.w).let { aw -> monoFonts.getOrPut(aw) {
        runCatching {
            Typeface.Builder(ctx.assets, "fonts/GeistMono-wght.ttf")
                .setFontVariationSettings("'wght' $aw")
                .setWeight(aw)
                .setFallback("sans-serif")
                .build()
        }.getOrNull() ?: Typeface.MONOSPACE
    } }

    fun monoText(s: CharSequence, sizeSp: Float, color: Int, weight: Weight = Weight.REGULAR): TextView = TextView(ctx).apply {
        text = s; textSize = sizeSp; setTextColor(color); typeface = mono(weight); includeFontPadding = false
        setLineSpacing(0f, 1.2f)
    }

    // ---------- 形 ----------
    fun rounded(color: Int, radiusDp: Number, strokeColor: Int? = null, strokeDp: Number = 1): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(radiusDp).toFloat(); setColor(color)
            if (strokeColor != null) setStroke(dp(strokeDp).coerceAtLeast(1), strokeColor)
        }

    /** 点線の枠（空の状態などに使う。sv-agentation の破線ボックスを参考） */
    fun dashed(color: Int, radiusDp: Number, strokeColor: Int = outline): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(radiusDp).toFloat(); setColor(color)
            setStroke(dp(1).coerceAtLeast(1), withAlpha(strokeColor, 0.6f), dp(5).toFloat(), dp(4).toFloat())
        }

    fun oval(color: Int): GradientDrawable = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }

    /**
     * v0.9: 見た目は小さく、押せる範囲は 48dp のまま（R6.5 / R6.6）。
     * 縦方向に insetDp ずつ余白をとった背景（ボタン 40dp・チップ 32dp の見た目 + 48dp のタッチ領域）。
     */
    fun insetV(d: Drawable, insetDp: Number): Drawable = if (dp(insetDp) <= 0) d else InsetDrawable(d, 0, dp(insetDp), 0, dp(insetDp))

    /** カード: 白い面 + 細い線（designeer の "raised" に相当）。選択中はアクセントの淡い面 + アクセントの線 */
    fun card(selected: Boolean = false, radiusDp: Int = 20): GradientDrawable =
        if (selected) rounded(primaryContainer, radiusDp, withAlpha(primary, 0.55f))
        else rounded(surfaceContainer, radiusDp, outlineVariant)

    /** 上だけ角丸のシート */
    fun sheetBackground(): GradientDrawable = GradientDrawable().apply {
        val r = dp(24).toFloat()
        cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        setColor(surface)
        setStroke(dp(1).coerceAtLeast(1), outlineVariant)
    }

    /** 押したときに波紋が出る背景 */
    fun ripple(content: Drawable?, mask: Drawable, rippleBase: Int = onSurface): RippleDrawable =
        RippleDrawable(ColorStateList.valueOf(withAlpha(rippleBase, 0.12f)), content, mask)

    fun withAlpha(color: Int, a: Float) = Color.argb((Color.alpha(color) * a).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    fun icon(id: Int, tint: Int): Drawable = ctx.getDrawable(id)!!.mutate().apply { setTint(tint) }

    /**
     * 丸いアイコンボタン。v0.9: 既定で 48dp（R6.5）。
     * touchHeightDp を大きくすると、丸の見た目は sizeDp のまま、縦のタッチ領域だけ広げる（フローティングの細い列用）。
     */
    fun iconButton(iconId: Int, desc: String, sizeDp: Int = 48, touchHeightDp: Int = sizeDp, onClick: () -> Unit): ImageButton =
        ImageButton(ctx).apply {
            contentDescription = desc
            tooltipText = desc
            scaleType = android.widget.ImageView.ScaleType.CENTER
            setImageDrawable(icon(iconId, onSurfaceVariant))
            setPadding(0, 0, 0, 0)
            val inset = ((touchHeightDp - sizeDp) / 2f).coerceAtLeast(0f)
            setTag(R.id.tag_icon_inset, inset)
            background = ripple(null, insetV(oval(Color.WHITE), inset))
            setOnClickListener { onClick() }
            Motion.pressable(this)
        }

    fun styleIconButton(b: ImageButton, iconId: Int, fg: Int, bg: Int?) {
        val inset = (b.getTag(R.id.tag_icon_inset) as? Float) ?: 0f
        b.setImageDrawable(icon(iconId, fg))
        b.background = ripple(bg?.let { insetV(oval(it), inset) }, insetV(oval(Color.WHITE), inset), if (bg != null) fg else onSurface)
    }

    /**
     * ピル型のボタン。
     * FILLED = 墨色の主ボタン / TONAL = 淡いグレー / TEXT = 文字だけ / ACCENT = アクセント色 / DANGER = 赤（削除など）/ DANGER_TEXT = 赤文字
     * PRIMARY_TEXT = アクセント色の文字だけ（v0.9: ダイアログの確定ボタン。R5.12）
     * v0.9: 見た目の高さは 40dp のまま、押せる範囲は 48dp（上下 4dp の透明な余白。R6.5）
     */
    enum class BtnStyle { FILLED, TONAL, TEXT, ACCENT, DANGER, DANGER_TEXT, PRIMARY_TEXT }

    fun button(label: String, style: BtnStyle, iconId: Int? = null, onClick: () -> Unit): TextView =
        TextView(ctx).apply {
            text = label; textSize = Type.LABEL_LARGE; typeface = font(Weight.MEDIUM)
            gravity = Gravity.CENTER; minHeight = dp(48); isClickable = true; isFocusable = true
            setPadding(dp(if (iconId != null) 14 else 20), 0, dp(20), 0)
            val (bg, fg) = when (style) {
                BtnStyle.FILLED -> Pair<Int?, Int>(inverseSurface, inverseOnSurface)
                BtnStyle.TONAL -> secondaryContainer to onSecondaryContainer
                BtnStyle.TEXT -> Pair<Int?, Int>(null, onSurface)
                BtnStyle.ACCENT -> primary to onPrimary
                BtnStyle.DANGER -> this@Ui.error to onError
                BtnStyle.DANGER_TEXT -> Pair<Int?, Int>(null, this@Ui.error)
                BtnStyle.PRIMARY_TEXT -> Pair<Int?, Int>(null, primary)
            }
            if (style == BtnStyle.TEXT || style == BtnStyle.DANGER_TEXT || style == BtnStyle.PRIMARY_TEXT) setPadding(dp(if (iconId != null) 10 else 14), 0, dp(14), 0)
            setTextColor(fg)
            background = ripple(bg?.let { insetV(rounded(it, 999), 4) }, insetV(rounded(Color.WHITE, 999), 4), fg)
            if (iconId != null) {
                val d = icon(iconId, fg).apply { setBounds(0, 0, dp(18), dp(18)) }
                setCompoundDrawablesRelative(d, null, null, null); compoundDrawablePadding = dp(8)
            }
            setOnClickListener { onClick() }
            Motion.pressable(this)
        }

    fun text(s: CharSequence, sizeSp: Float, color: Int, medium: Boolean = false, weight: Weight? = null): TextView = TextView(ctx).apply {
        text = s; textSize = sizeSp; setTextColor(color)
        typeface = font(weight ?: if (medium) Weight.MEDIUM else Weight.REGULAR)
        // 大きな見出しは字間を少し詰める（designeer の tracking-tight）
        if (sizeSp >= 20f) letterSpacing = -0.02f
        setLineSpacing(0f, 1.2f)
    }

    /** セクション見出し（v0.7: 等幅・大文字・字間広め。sv-agentation の "TRY IT" 風） */
    fun sectionLabel(s: String): TextView = monoText(s, Type.LABEL_SMALL, ink3, Weight.MEDIUM).apply {
        isAllCaps = true; letterSpacing = 0.12f
        setPadding(dp(4), dp(28), 0, dp(10))
    }

    /** 見出し + 件数バッジ + 右へ伸びる細い線（sv-blocks のサイドバーの件数表示を参考） */
    fun sectionHeader(label: String, count: TextView? = null): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4), dp(28), 0, dp(10))
        addView(sectionLabel(label).apply {
            setPadding(0, 0, 0, 0)
            // v0.9: TalkBack の見出し移動で飛べるように
            if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
        })
        if (count != null) addView(count, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) })
        addView(divider(), LinearLayout.LayoutParams(0, dp(1).coerceAtLeast(1), 1f).apply { marginStart = dp(10) })
    }

    /** バッジの色合い */
    enum class Tone { NEUTRAL, ACCENT, SUCCESS, RECORD }

    /** 小さな等幅バッジ（v0.7: "v0.3.0" や "New" のようなピル） */
    fun badge(s: String, tone: Tone = Tone.NEUTRAL): TextView {
        val (bg, fg, line) = when (tone) {
            Tone.NEUTRAL -> Triple(secondaryContainer, onSurfaceVariant, outlineVariant)
            Tone.ACCENT -> Triple(primaryContainer, onPrimaryContainer, withAlpha(primary, 0.35f))
            Tone.SUCCESS -> Triple(successContainer, success, withAlpha(success, 0.35f))
            Tone.RECORD -> Triple(errorContainer, onErrorContainer, withAlpha(record, 0.35f))
        }
        return monoText(s, Type.LABEL_SMALL, fg, Weight.MEDIUM).apply {
            setPadding(dp(8), dp(3), dp(8), dp(3)); gravity = Gravity.CENTER
            background = rounded(bg, 999, line)
        }
    }

    /** キーキャップ風の値表示（設定シートの現在値など） */
    fun kbd(s: String = ""): TextView = monoText(s, Type.LABEL_LARGE, onSurface, Weight.SEMIBOLD).apply {
        setPadding(dp(10), dp(5), dp(10), dp(5)); gravity = Gravity.CENTER
        background = rounded(secondaryContainer, 8, outlineVariant)
    }

    /** 小さなラベル（読み取り専用。v0.7: 表のセルのような等幅タグ） */
    fun chip(s: String): TextView = monoText(s, Type.LABEL_MEDIUM, onSurfaceVariant).apply {
        setPadding(dp(8), dp(4), dp(8), dp(4))
        background = rounded(secondaryContainer, 6, outlineVariant)
    }

    /**
     * 選べるチップ（ピル型）。選択中は墨色、未選択は淡いグレー。
     * v0.9: 見た目は M3 のチップと同じ 32dp、押せる範囲は 48dp（R6.5）。選択中はチェックの印も付ける（色だけに頼らない: R6.8）
     */
    inner class FilterChip(private val label: String, onClick: () -> Unit) {
        val view: TextView = text(label, Type.LABEL_LARGE, onSurface, weight = Weight.MEDIUM).apply {
            gravity = Gravity.CENTER; minHeight = dp(48); minWidth = dp(48)
            setPadding(dp(14), 0, dp(14), 0); isClickable = true; isFocusable = true
            setOnClickListener { Motion.haptic(this, Motion.Haptic.TICK); onClick() }
            Motion.pressable(this)
        }
        var selected = false
            set(v) {
                field = v
                view.isSelected = v
                val bg = if (v) inverseSurface else secondaryContainer
                val fg = if (v) inverseOnSurface else onSurface
                view.setTextColor(fg)
                view.background = ripple(insetV(rounded(bg, 999), 8), insetV(rounded(Color.WHITE, 999), 8), fg)
                val check = if (v) icon(R.drawable.ic_check, fg).apply { setBounds(0, 0, dp(16), dp(16)) } else null
                view.setCompoundDrawablesRelative(check, null, null, null)
                view.compoundDrawablePadding = dp(4)
                view.setPadding(dp(if (v) 10 else 14), 0, dp(14), 0)
            }
        init { selected = false }
    }

    /**
     * セグメント（トレイの中に並んだ選択肢）。選択中の項目は白く浮いた面になる。
     * designeer の「トレイ（角丸 14dp）＋ 中の項目（角丸 10dp）」の形を参考にしています。
     */
    inner class Segmented(labels: List<String>, descs: List<String>? = null, val onSelect: (Int) -> Unit) {
        val view = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(secondaryContainer, 14)
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        private val items = labels.mapIndexed { i, l ->
            text(l, Type.LABEL_LARGE, onSurfaceVariant, weight = Weight.MEDIUM).apply {
                // v0.9: 48dp のタッチ領域（R6.5）
                gravity = Gravity.CENTER; minHeight = dp(48); isClickable = true; isFocusable = true
                descs?.getOrNull(i)?.let { contentDescription = it }
                setOnClickListener { Motion.haptic(this, Motion.Haptic.TICK); select(i); onSelect(i) }
            }.also { view.addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)) }
        }
        var index = -1
            private set

        fun select(i: Int) {
            index = i
            items.forEachIndexed { j, t ->
                val on = j == i
                t.isSelected = on
                t.setTextColor(if (on) onSurface else onSurfaceVariant)
                // v0.9: 選択中の項目にも波紋（R7.5）
                t.background = if (on) ripple(rounded(surfaceContainerHigh, 11, outlineVariant), rounded(Color.WHITE, 11))
                    else ripple(null, rounded(Color.WHITE, 11))
                t.elevation = if (on) dp(1).toFloat() else 0f
            }
        }
    }

    /** 枠付きの入力欄（フォーカス時はアクセント色の線） */
    fun field(initial: String, hintText: String? = null, inputType: Int = InputType.TYPE_CLASS_TEXT): EditText =
        EditText(ctx).apply {
            setText(initial); setSingleLine(); this.inputType = inputType
            hint = hintText; setHintTextColor(ink3)
            setTextColor(onSurface); textSize = Type.BODY_LARGE; typeface = font(Weight.REGULAR)
            setPadding(dp(14), dp(12), dp(14), dp(12)); minHeight = dp(48)
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), rounded(surfaceContainer, 12, primary, 2))
                addState(intArrayOf(), rounded(surfaceContainer, 12, outline))
            }
            if (Build.VERSION.SDK_INT >= 29) {
                textCursorDrawable = GradientDrawable().apply { setColor(primary); setSize(dp(2), dp(20)) }
            }
            highlightColor = withAlpha(primary, 0.3f)
        }

    /** スライダーの色をアクセント色に */
    fun tintSlider(s: SeekBar) {
        s.progressTintList = ColorStateList.valueOf(primary)
        s.progressBackgroundTintList = ColorStateList.valueOf(outline)
        s.thumbTintList = ColorStateList.valueOf(primary)
        s.splitTrack = false
    }

    /** 区切り線 */
    fun divider(): View = View(ctx).apply { setBackgroundColor(outlineVariant) }

    companion object {
        private val fonts = HashMap<Int, Typeface>()
        private val monoFonts = HashMap<Int, Typeface>()
    }
}
