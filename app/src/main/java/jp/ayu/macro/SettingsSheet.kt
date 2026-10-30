package jp.ayu.macro

import android.app.Activity
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * v0.6: マクロごとの設定（下から出るシート）。
 * 名前 / 繰り返し（1・5・10・∞・カスタム）/ ループ間隔（プリセット＋ステッパー）/ 速度（スライダー＋プリセット）/ 削除
 */
class SettingsSheet(
    private val activity: Activity,
    private val macro: Macro,
    private val onSave: (Macro) -> Unit,
    private val onDelete: (Macro) -> Unit,
) {
    private val sheet = BottomSheet(activity)
    private val ui = sheet.ui
    private fun dp(v: Int) = ui.dp(v)
    private fun s(id: Int) = activity.getString(id)

    private val repeatPresets = listOf(1, 5, 10, 0) // 0 = 無限
    private val intervalPresets = listOf(0L, 500L, 1000L, 2000L, 5000L, 10000L, 30000L)
    private val speedPresets = listOf(0.5f, 1f, 1.5f, 2f, 3f, 4f)

    private var repeat = macro.repeatCount
    private var customRepeat = if (macro.repeatCount in repeatPresets) 3 else macro.repeatCount
    private var intervalMs = macro.loopIntervalMs.coerceIn(0L, MAX_INTERVAL_MS)
    private var speed = macro.speed.coerceIn(Macro.MIN_SPEED, Macro.MAX_SPEED)

    private lateinit var nameField: EditText
    private lateinit var repeatSeg: Ui.Segmented
    private lateinit var repeatHint: TextView
    private lateinit var customRow: View
    private lateinit var customField: EditText
    private lateinit var intervalValue: TextView
    private lateinit var intervalField: EditText
    private val intervalChips = ArrayList<Ui.FilterChip>()
    private lateinit var speedValue: TextView
    private lateinit var speedBar: SeekBar
    private val speedChips = ArrayList<Ui.FilterChip>()
    private var syncing = false

    fun show() {
        val root = MaxHeightLayout(activity, 0.92f).apply {
            orientation = LinearLayout.VERTICAL
            background = ui.sheetBackground()
        }
        // つまみ
        val handle = View(activity).apply { background = ui.rounded(ui.outline, 999) }
        root.addView(handle,
            LinearLayout.LayoutParams(dp(36), dp(4)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(10) })
        // 見出し
        val head = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(14), dp(24), dp(10)) }
        head.addView(ui.text(s(R.string.settings_title), 22f, ui.onSurface, weight = Ui.Weight.SEMIBOLD))
        head.addView(ui.badge(Texts.steps(activity, macro.steps.size)).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(6); bottomMargin = dp(2) })
        head.addView(ui.text(activity.getString(R.string.settings_subtitle, macro.name, Texts.steps(activity, macro.steps.size)),
            14f, ui.onSurfaceVariant).apply { setPadding(0, dp(2), 0, 0); maxLines = 2 })
        root.addView(head)

        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(4), dp(16), dp(16)) }
        // v0.8: グループ見出しで階層をはっきり（spottedinprod / pttrns のグループ化された設定画面のパターン）
        content.addView(groupLabel(s(R.string.settings_group_basic), first = true))
        content.addView(nameCard())
        content.addView(groupLabel(s(R.string.settings_group_playback)))
        content.addView(repeatCard())
        content.addView(intervalCard())
        content.addView(speedCard())
        content.addView(groupLabel(s(R.string.settings_group_manage)))
        content.addView(dangerCard())
        val scroll = ScrollView(activity).apply { isFillViewport = false; addView(content) }
        root.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))

        // 下に固定のボタン列
        root.addView(ui.divider(), LinearLayout.LayoutParams(MATCH, dp(1)))
        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        bar.addView(ui.button(s(R.string.cancel), Ui.BtnStyle.TONAL) { sheet.dismiss() }.apply { minHeight = dp(48) },
            LinearLayout.LayoutParams(0, WRAP, 1f))
        bar.addView(ui.button(s(R.string.save), Ui.BtnStyle.FILLED) { save() }.apply { minHeight = dp(48) },
            LinearLayout.LayoutParams(0, WRAP, 2f).apply { marginStart = dp(10) })
        root.addView(bar)

        // ナビゲーションバー分の余白
        root.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION") v.setPadding(0, 0, 0, insets.systemWindowInsetBottom.coerceAtMost(dp(48)))
            insets
        }

        sheet.attach(root)
        sheet.dragToDismiss(handle, head)
        applyRepeat(); applyInterval(fromField = false); applySpeed(fromBar = false)
        sheet.show()
        // v0.7: シートが上がるのに合わせて、カードを順番に表示
        for (i in 0 until content.childCount) Motion.enter(content.getChildAt(i), i + 1, 14f)
    }

    // ---- カード ----

    private fun groupLabel(t: String, first: Boolean = false) = ui.sectionLabel(t).apply {
        setPadding(dp(8), if (first) dp(8) else dp(20), dp(8), 0)
        isFocusable = true
        if (android.os.Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
    }

    private fun card(iconId: Int, title: String, value: TextView? = null): LinearLayout {
        val c = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = ui.card()
            setPadding(dp(16), dp(14), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) }
        }
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(ImageView(activity).apply {
            setImageDrawable(ui.icon(iconId, ui.onSurfaceVariant)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setPadding(dp(6), dp(6), dp(6), dp(6)); background = ui.rounded(ui.secondaryContainer, 9, ui.outlineVariant)
        }, LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginEnd = dp(12) })
        row.addView(ui.text(title, 15f, ui.onSurface, weight = Ui.Weight.SEMIBOLD).apply { isFocusable = true },
            LinearLayout.LayoutParams(0, WRAP, 1f))
        if (value != null) row.addView(value)
        c.addView(row, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(12) })
        return c
    }

    /** v0.7: 現在値はキーキャップ風の等幅表示 */
    private fun valueBadge() = ui.kbd().apply {
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }

    private fun hint(t: String = "") = ui.text(t, 13f, ui.onSurfaceVariant).apply { setPadding(dp(2), dp(10), 0, 0) }

    private fun nameCard() = card(R.drawable.ic_edit, s(R.string.settings_name)).apply {
        nameField = ui.field(macro.name, s(R.string.name_hint))
        addView(nameField, LinearLayout.LayoutParams(MATCH, WRAP))
    }

    private fun repeatCard() = card(R.drawable.ic_repeat, s(R.string.settings_repeat)).apply {
        val labels = listOf("1", "5", "10", "∞", s(R.string.repeat_custom))
        val descs = listOf(Texts.repeat(activity, 1), Texts.repeat(activity, 5), Texts.repeat(activity, 10),
            s(R.string.repeat_infinite), s(R.string.repeat_custom))
        repeatSeg = ui.Segmented(labels, descs) { i ->
            repeat = if (i < repeatPresets.size) repeatPresets[i] else customRepeat
            applyRepeat()
        }
        addView(repeatSeg.view, LinearLayout.LayoutParams(MATCH, WRAP))
        customField = numberField(customRepeat.toString(), InputType.TYPE_CLASS_NUMBER)
        customField.addTextChangedListener(watcher {
            val v = customField.text.toString().toIntOrNull()
            if (v != null && v >= 1) { customRepeat = v.coerceAtMost(MAX_REPEAT); if (repeatSeg.index == 4) { repeat = customRepeat; updateRepeatHint() } }
        })
        customRow = stepper(customField, s(R.string.unit_times), s(R.string.decrease), s(R.string.increase)) { d ->
            customRepeat = (customRepeat + d).coerceIn(1, MAX_REPEAT); repeat = customRepeat
            setQuiet(customField, customRepeat.toString()); updateRepeatHint()
        }
        addView(customRow, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) })
        repeatHint = hint()
        addView(repeatHint)
    }

    private fun intervalCard(): LinearLayout {
        intervalValue = valueBadge()
        return card(R.drawable.ic_timer, s(R.string.settings_interval), intervalValue).apply {
            addView(chipRow(intervalPresets.map { ms ->
                ui.FilterChip(Texts.seconds(activity, ms)) { intervalMs = ms; applyInterval(fromField = false) }.also { intervalChips += it }
            }))
            intervalField = numberField("", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
            intervalField.addTextChangedListener(watcher {
                val v = intervalField.text.toString().replace(',', '.').toDoubleOrNull() ?: return@watcher
                intervalMs = (v.coerceIn(0.0, MAX_INTERVAL_MS / 1000.0) * 1000).toLong()
                applyInterval(fromField = true)
            })
            addView(stepper(intervalField, s(R.string.unit_seconds), s(R.string.decrease), s(R.string.increase)) { d ->
                intervalMs = ((intervalMs + d * 500L) / 500L * 500L).coerceIn(0L, MAX_INTERVAL_MS)
                applyInterval(fromField = false)
            }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) })
            addView(hint(s(R.string.interval_hint)))
        }
    }

    private fun speedCard(): LinearLayout {
        speedValue = valueBadge()
        return card(R.drawable.ic_speed, s(R.string.settings_speed), speedValue).apply {
            val steps = ((Macro.MAX_SPEED - Macro.MIN_SPEED) / 0.25f).roundToInt()
            speedBar = SeekBar(activity).apply {
                max = steps
                ui.tintSlider(this)
                contentDescription = s(R.string.settings_speed)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                        if (fromUser) {
                            speed = Macro.MIN_SPEED + p * 0.25f; applySpeed(fromBar = true)
                            Motion.haptic(sb, Motion.Haptic.TICK) // v0.8: 目盛りごとに軽いクリック
                        }
                    }
                    override fun onStartTrackingTouch(sb: SeekBar) {}
                    override fun onStopTrackingTouch(sb: SeekBar) {}
                })
            }
            addView(speedBar, LinearLayout.LayoutParams(MATCH, dp(40)))
            val ends = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(4), 0, dp(4), dp(10)) }
            ends.addView(ui.monoText(Texts.speed(activity, Macro.MIN_SPEED), 11f, ui.ink3), LinearLayout.LayoutParams(0, WRAP, 1f))
            ends.addView(ui.monoText(Texts.speed(activity, Macro.MAX_SPEED), 11f, ui.ink3))
            addView(ends)
            addView(chipRow(speedPresets.map { v ->
                ui.FilterChip(Texts.speed(activity, v)) { speed = v; applySpeed(fromBar = false) }.also { speedChips += it }
            }))
            addView(hint(s(R.string.speed_hint)))
        }
    }

    private fun dangerCard() = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        // v0.7: 「危険な操作」は赤みの細い線で区別
        background = ui.rounded(ui.surfaceContainer, 20, ui.withAlpha(ui.error, 0.3f))
        setPadding(dp(8), dp(6), dp(8), dp(6))
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) }
        addView(ui.button(s(R.string.delete_macro), Ui.BtnStyle.DANGER_TEXT, R.drawable.ic_delete) { confirmDelete() }.apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START; minHeight = dp(44)
        }, LinearLayout.LayoutParams(MATCH, WRAP))
    }

    // ---- 部品 ----

    private fun chipRow(chips: List<Ui.FilterChip>): View {
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        chips.forEachIndexed { i, c -> row.addView(c.view, LinearLayout.LayoutParams(WRAP, WRAP).apply { if (i > 0) marginStart = dp(8) }) }
        return HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false; clipToPadding = false
            addView(row)
        }
    }

    private fun numberField(initial: String, type: Int) = ui.field(initial, null, type).apply {
        gravity = Gravity.CENTER; textSize = 18f; typeface = ui.mono(Ui.Weight.SEMIBOLD)
        setSelectAllOnFocus(true)
    }

    /** − [値] 単位 + */
    private fun stepper(field: EditText, unit: String, minusDesc: String, plusDesc: String, onStep: (Int) -> Unit): View {
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fun round(icon: Int, desc: String, d: Int) = ui.iconButton(icon, desc, 44) { onStep(d) }.apply {
            ui.styleIconButton(this, icon, ui.onSurface, ui.secondaryContainer)
        }
        row.addView(round(R.drawable.ic_remove, minusDesc, -1), LinearLayout.LayoutParams(dp(44), dp(44)))
        row.addView(field, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(10); marginEnd = dp(8) })
        row.addView(ui.text(unit, 14f, ui.onSurfaceVariant), LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(10) })
        row.addView(round(R.drawable.ic_add, plusDesc, +1), LinearLayout.LayoutParams(dp(44), dp(44)))
        return row
    }

    private fun watcher(f: () -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: Editable?) { if (!syncing) f() }
    }

    private fun setQuiet(f: EditText, t: String) {
        syncing = true; f.setText(t); f.setSelection(f.text.length); syncing = false
    }

    // ---- 状態を画面に反映 ----

    private fun applyRepeat() {
        val i = repeatPresets.indexOf(repeat).let { if (it < 0 || (repeatSeg.index == 4 && repeat != 0)) 4 else it }
        repeatSeg.select(i)
        customRow.visibility = if (i == 4) View.VISIBLE else View.GONE
        if (i == 4) setQuiet(customField, customRepeat.toString())
        updateRepeatHint()
    }

    private fun updateRepeatHint() {
        repeatHint.text = if (repeat == 0) s(R.string.repeat_hint_infinite)
            else activity.resources.getQuantityString(R.plurals.repeat_hint, repeat, repeat)
    }

    private fun applyInterval(fromField: Boolean) {
        intervalValue.text = Texts.seconds(activity, intervalMs)
        intervalChips.forEachIndexed { i, c -> c.selected = intervalPresets[i] == intervalMs }
        if (!fromField) setQuiet(intervalField, Texts.num(activity, intervalMs / 1000.0))
    }

    private fun applySpeed(fromBar: Boolean) {
        speedValue.text = Texts.speed(activity, speed)
        speedChips.forEachIndexed { i, c -> c.selected = speedPresets[i] == speed }
        if (!fromBar) speedBar.progress = ((speed - Macro.MIN_SPEED) / 0.25f).roundToInt()
        speedBar.stateDescriptionCompat(Texts.speed(activity, speed))
    }

    private fun SeekBar.stateDescriptionCompat(t: String) {
        if (android.os.Build.VERSION.SDK_INT >= 30) stateDescription = t
    }

    private fun save() {
        val name = nameField.text.toString().trim().ifEmpty { macro.name }
        val rc = if (repeatSeg.index == 4) customRepeat.coerceIn(1, MAX_REPEAT) else repeat
        onSave(macro.copy(name = name, repeatCount = rc, loopIntervalMs = intervalMs.coerceIn(0L, MAX_INTERVAL_MS),
            speed = speed.coerceIn(Macro.MIN_SPEED, Macro.MAX_SPEED)))
        sheet.dismiss()
    }

    private fun confirmDelete() {
        Dialogs.show(activity, s(R.string.delete_title), activity.getString(R.string.delete_message, macro.name),
            iconId = R.drawable.ic_delete, destructive = true, confirmLabel = s(R.string.delete)) {
            onDelete(macro); sheet.dismiss(); true
        }
    }

    companion object {
        const val MAX_REPEAT = 100000
        const val MAX_INTERVAL_MS = 3_600_000L
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
