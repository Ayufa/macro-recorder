package jp.ayu.macro

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import jp.ayu.macro.monetization.Monetization
import jp.ayu.macro.monetization.Products

class MainActivity : Activity() {

    private lateinit var store: MacroStore
    private lateinit var ui: Ui
    private lateinit var statusContainer: LinearLayout
    private lateinit var listContainer: LinearLayout
    private lateinit var countView: TextView
    private lateinit var adContainer: android.widget.FrameLayout
    private var planContainer: LinearLayout? = null
    private val entitlements by lazy { Monetization.entitlements(this) }
    private val ads by lazy { Monetization.ads(this) }
    private val onEntitlementsChanged: () -> Unit = { refresh() }

    private fun dp(v: Int) = ui.dp(v)

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppLocales.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = MacroStore(this)
        ui = Ui(this)
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(ui.surface) }
        // 画面全体: 上にスクロール、下に広告枠（standard では常に非表示）
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(ui.surface) }
        outer.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        adContainer = android.widget.FrameLayout(this).apply { visibility = View.GONE }
        outer.addView(adContainer, LinearLayout.LayoutParams(MATCH, WRAP))
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(28))
        }
        scroll.addView(root)
        // edge-to-edge (targetSdk 35+ では強制) に対応: システムバー分の余白を付ける
        outer.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                v.setPadding(b.left, b.top, b.right, b.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        setContentView(outer)

        // ヘッダー
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(20))
        }
        header.addView(ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(14) })
        header.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(ui.text(getString(R.string.app_name), 26f, ui.onSurface, weight = Ui.Weight.SEMIBOLD))
            addView(ui.text(getString(R.string.tagline), 13f, ui.onSurfaceVariant))
        }, LinearLayout.LayoutParams(0, WRAP, 1f))
        root.addView(header)

        statusContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ui.card()
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        root.addView(statusContainer, LinearLayout.LayoutParams(MATCH, WRAP))

        // 使い方
        root.addView(ui.sectionLabel(getString(R.string.section_how_to)))
        root.addView(howTo())

        // 一覧
        val listHeader = LinearLayout(this).apply {
            // 見出しと件数バッジは文字のベースラインでそろえる
            orientation = LinearLayout.HORIZONTAL; isBaselineAligned = true
        }
        listHeader.addView(ui.sectionLabel(getString(R.string.section_macros)))
        countView = ui.text("", 12f, ui.onSecondaryContainer, medium = true).apply {
            setPadding(dp(8), dp(1), dp(8), dp(1)); background = ui.rounded(ui.secondaryContainer, 999)
        }
        listHeader.addView(countView, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(2) })
        root.addView(listHeader)
        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listContainer)

        // プラン（monetized フレーバーだけ。standard では表示しない）
        if (entitlements.purchasesAvailable) {
            root.addView(ui.sectionLabel(getString(R.string.section_plan)))
            planContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = ui.card()
                setPadding(dp(16), dp(12), dp(16), dp(12))
            }
            root.addView(planContainer, LinearLayout.LayoutParams(MATCH, WRAP))
        }

        // アプリの設定（言語）
        root.addView(ui.sectionLabel(getString(R.string.section_app_settings)))
        root.addView(languageRow(), LinearLayout.LayoutParams(MATCH, WRAP))

        // プライバシー
        val privacy = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(dp(4), dp(24), dp(4), 0)
        }
        privacy.addView(ImageView(this).apply { setImageDrawable(ui.icon(R.drawable.ic_shield, ui.onSurfaceVariant)) },
            LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(8); topMargin = dp(1) })
        privacy.addView(ui.text(getString(R.string.privacy_note),
            12f, ui.onSurfaceVariant), LinearLayout.LayoutParams(0, WRAP, 1f))
        root.addView(privacy)
        ads.initialize(this)
        root.addView(ui.text(getString(R.string.version_label, appVersion()), 11f, ui.ink3).apply { setPadding(dp(30), dp(8), 0, 0) })
    }

    private fun appVersion(): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    }.getOrDefault("")

    /** 言語の行（タップで選択。Android 13+ はシステムの「アプリの言語」と同じ設定） */
    private fun languageRow(): View {
        val tag = AppLocales.current(this)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(12), dp(14))
            background = ui.ripple(ui.card(), ui.rounded(Color.WHITE, 20))
            isClickable = true; isFocusable = true
            setOnClickListener { pickLanguage() }
        }
        row.addView(ImageView(this).apply {
            setImageDrawable(ui.icon(R.drawable.ic_language, ui.onSecondaryContainer)); setPadding(dp(7), dp(7), dp(7), dp(7))
            background = ui.oval(ui.secondaryContainer)
        }, LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(14) })
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(ui.text(getString(R.string.language), 15f, ui.onSurface, medium = true))
            addView(ui.text(getString(AppLocales.nameRes(tag)), 12f, ui.onSurfaceVariant))
        }, LinearLayout.LayoutParams(0, WRAP, 1f))
        row.addView(ImageView(this).apply { setImageDrawable(ui.icon(R.drawable.ic_chevron_right, ui.ink3)) },
            LinearLayout.LayoutParams(dp(20), dp(20)))
        return row
    }

    private fun pickLanguage() {
        val tags = AppLocales.SUPPORTED
        val cur = tags.indexOf(AppLocales.current(this)).coerceAtLeast(0)
        Dialogs.choice(this, getString(R.string.language), tags.map { getString(AppLocales.nameRes(it)) }, cur) { i ->
            AppLocales.set(this, tags[i])
        }
    }

    private fun howTo(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ui.card()
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val steps = listOf(
            R.drawable.ic_record to R.string.howto_1,
            R.drawable.ic_stop to R.string.howto_2,
            R.drawable.ic_play to R.string.howto_3,
            R.drawable.ic_collapse to R.string.howto_4,
            R.drawable.ic_settings to R.string.howto_5,
        )
        for ((i, pair) in steps.withIndex()) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, if (i == 0) 0 else dp(10), 0, 0)
            }
            row.addView(ImageView(this).apply {
                setImageDrawable(ui.icon(pair.first, ui.onSecondaryContainer)); setPadding(dp(6), dp(6), dp(6), dp(6))
                background = ui.oval(ui.secondaryContainer)
            }, LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginEnd = dp(12) })
            row.addView(ui.text(getString(pair.second), 13f, ui.onSurface), LinearLayout.LayoutParams(0, WRAP, 1f))
            box.addView(row)
        }
        return box
    }

    override fun onResume() {
        super.onResume()
        MacroAccessibilityService.onChanged = { refresh() }
        entitlements.addListener(onEntitlementsChanged)
        refresh()
    }

    override fun onPause() {
        MacroAccessibilityService.onChanged = null
        entitlements.removeListener(onEntitlementsChanged)
        super.onPause()
    }

    override fun onDestroy() {
        ads.destroy()
        super.onDestroy()
    }

    /** monetized フレーバーのみ: 購入ボタンと広告の表示を更新 */
    private fun refreshMonetization() {
        ads.attachBanner(this, adContainer)
        val box = planContainer ?: return
        box.removeAllViews()
        val limit = entitlements.maxSavedMacros
        box.addView(ui.text(
            if (limit == null) getString(R.string.plan_unlimited) else resources.getQuantityString(R.plurals.plan_free_limit, limit, limit),
            13f, ui.onSurface))
        fun item(id: String, label: String, owned: Boolean) {
            val price = entitlements.priceOf(id)
            val b = ui.button(if (owned) getString(R.string.plan_owned, label) else label + (price?.let { "  $it" } ?: ""),
                if (owned) Ui.BtnStyle.TEXT else Ui.BtnStyle.TONAL) { entitlements.launchPurchase(this, id) }
            b.isEnabled = !owned
            box.addView(b, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) })
        }
        item(Products.REMOVE_ADS, getString(R.string.plan_remove_ads), entitlements.isAdFree)
        item(Products.UNLIMITED_MACROS, getString(R.string.plan_unlimited_macros), limit == null)
        if (ads.privacyOptionsAvailable) {
            val b = ui.button(getString(R.string.plan_ad_privacy), Ui.BtnStyle.TEXT) { ads.showPrivacyOptions(this) }
            box.addView(b, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(4) })
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(this, MacroAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    private fun isIgnoringBattery(): Boolean =
        (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)

    @SuppressLint("BatteryLife")
    private fun requestIgnoreBattery() {
        if (isIgnoringBattery()) { toast(getString(R.string.toast_battery_already)); return }
        val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
        runCatching { startActivity(i) }.onFailure {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    /** 状態カードの1行（アイコン + タイトル + 説明 + 任意のボタン） */
    private fun statusRow(ok: Boolean, title: String, sub: String, action: View?): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        row.addView(ImageView(this).apply {
            setImageDrawable(ui.icon(if (ok) R.drawable.ic_check_circle else R.drawable.ic_error, if (ok) ui.primary else ui.error))
        }, LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(14) })
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(ui.text(title, 15f, ui.onSurface, medium = true))
            addView(ui.text(sub, 12f, ui.onSurfaceVariant))
        }, LinearLayout.LayoutParams(0, WRAP, 1f))
        if (action != null) row.addView(action, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(8) })
        return row
    }

    @SuppressLint("SetTextI18n")
    private fun refresh() {
        val svc = MacroAccessibilityService.instance
        val running = svc != null
        val enabled = isServiceEnabled()
        statusContainer.removeAllViews()
        statusContainer.addView(statusRow(
            running, getString(R.string.status_service),
            when {
                running -> getString(R.string.status_service_running)
                enabled -> getString(R.string.status_service_stuck)
                else -> getString(R.string.status_service_off, getString(R.string.app_name))
            },
            if (running) null else ui.button(getString(R.string.action_open), Ui.BtnStyle.TONAL) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
        ))
        val ignoring = isIgnoringBattery()
        statusContainer.addView(statusRow(
            ignoring, getString(R.string.status_battery),
            getString(if (ignoring) R.string.status_battery_ok else R.string.status_battery_on),
            if (ignoring) null else ui.button(getString(R.string.action_exclude), Ui.BtnStyle.TONAL) { requestIgnoreBattery() },
        ))
        if (running) {
            val shown = svc?.isPanelShown == true
            statusContainer.addView(statusRow(
                shown, getString(R.string.status_panel), getString(if (shown) R.string.status_panel_shown else R.string.status_panel_hidden),
                if (shown) null else ui.button(getString(R.string.action_show), Ui.BtnStyle.FILLED) { svc?.showPanel(); refresh() },
            ))
        }
        val links = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(4), 0, dp(4), 0) }
        links.addView(ui.button(getString(R.string.action_app_info), Ui.BtnStyle.TEXT, R.drawable.ic_open) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })
        statusContainer.addView(links)

        listContainer.removeAllViews()
        val macros = store.list()
        countView.text = entitlements.maxSavedMacros?.let { "${macros.size} / $it" } ?: macros.size.toString()
        refreshMonetization()
        if (macros.isEmpty()) {
            listContainer.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(16), dp(28), dp(16), dp(28))
                background = ui.card()
                addView(ImageView(this@MainActivity).apply { setImageDrawable(ui.icon(R.drawable.ic_touch, ui.onSurfaceVariant)) },
                    LinearLayout.LayoutParams(dp(36), dp(36)))
                addView(ui.text(getString(R.string.empty_title), 15f, ui.onSurface, medium = true).apply { setPadding(0, dp(10), 0, dp(2)) })
                addView(ui.text(getString(R.string.empty_body), 12f, ui.onSurfaceVariant))
            }, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        val selected = store.selectedId
        for (m in macros) listContainer.addView(row(m, m.id == selected))
    }

    @SuppressLint("SetTextI18n")
    private fun row(m: Macro, isSelected: Boolean): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(10), dp(10))
            background = ui.card(isSelected)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(10) }
        }
        val fg = if (isSelected) ui.onPrimaryContainer else ui.onSurface
        val fgVar = if (isSelected) ui.onPrimaryContainer else ui.onSurfaceVariant
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(ui.text(m.name, 16f, fg, medium = true).apply { maxLines = 2 }, LinearLayout.LayoutParams(0, WRAP, 1f))
        if (isSelected) top.addView(ui.text(getString(R.string.selected_badge), 11f, ui.onPrimary, medium = true).apply {
            setPadding(dp(8), dp(2), dp(8), dp(2)); background = ui.rounded(ui.primary, 999)
        }, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(8) })
        card.addView(top)
        card.addView(ui.text(getString(R.string.macro_summary, Texts.steps(this, m.steps.size), Texts.kinds(this, m.steps)), 13f, fgVar).apply { setPadding(0, dp(2), 0, dp(8)) })
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val chipTexts = listOf(
            getString(R.string.chip_repeat, Texts.repeat(this, m.repeatCount)),
            getString(R.string.chip_interval, Texts.seconds(this, m.loopIntervalMs)),
            getString(R.string.chip_speed, Texts.num(this, m.speed.toDouble())),
        )
        for ((i, t) in chipTexts.withIndex()) {
            chips.addView(ui.chip(t).apply { setTextColor(fgVar) },
                LinearLayout.LayoutParams(WRAP, WRAP).apply { if (i > 0) marginStart = dp(6) })
        }
        card.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(chips) })

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, 0)
        }
        actions.addView(ui.button(getString(R.string.action_run), Ui.BtnStyle.FILLED, R.drawable.ic_play) { runMacro(m) })
        actions.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        fun ib(icon: Int, desc: String, onClick: () -> Unit) = ui.iconButton(icon, desc, 40, onClick).apply {
            setImageDrawable(ui.icon(icon, fgVar))
        }
        actions.addView(ib(if (isSelected) R.drawable.ic_star else R.drawable.ic_star_border, getString(R.string.desc_select)) {
            store.selectedId = m.id; refresh(); toast(getString(R.string.toast_selected))
        }, LinearLayout.LayoutParams(dp(40), dp(40)))
        actions.addView(ib(R.drawable.ic_tune, getString(R.string.desc_settings)) { editSettings(m) }, LinearLayout.LayoutParams(dp(40), dp(40)))
        actions.addView(ib(R.drawable.ic_edit, getString(R.string.desc_rename)) { rename(m) }, LinearLayout.LayoutParams(dp(40), dp(40)))
        actions.addView(ib(R.drawable.ic_delete, getString(R.string.delete)) { confirmDelete(m) }, LinearLayout.LayoutParams(dp(40), dp(40)))
        card.addView(actions)
        return card
    }

    private fun runMacro(m: Macro) {
        val s = MacroAccessibilityService.instance
        if (s == null) { toast(getString(R.string.toast_enable_service_first, getString(R.string.app_name))); return }
        store.selectedId = m.id
        toast(getString(R.string.toast_play_in_3s))
        moveTaskToBack(true)
        Handler(Looper.getMainLooper()).postDelayed({ MacroAccessibilityService.instance?.play(m) }, 3000)
    }

    private fun rename(m: Macro) {
        val edit = ui.field(m.name, getString(R.string.name_hint)).apply { setSelection(text.length) }
        Dialogs.show(this, getString(R.string.rename_title), iconId = R.drawable.ic_edit, content = edit,
            confirmLabel = getString(R.string.save), showKeyboard = true) {
            val n = edit.text.toString().trim()
            if (n.isNotEmpty()) { store.save(m.copy(name = n)); refresh() }
            true
        }
        edit.requestFocus()
    }

    private fun confirmDelete(m: Macro) {
        Dialogs.show(this, getString(R.string.delete_title), getString(R.string.delete_message, m.name),
            iconId = R.drawable.ic_delete, destructive = true, confirmLabel = getString(R.string.delete)) {
            store.delete(m.id); refresh(); true
        }
    }

    /** v0.6: 再生設定はボトムシート（SettingsSheet） */
    private fun editSettings(m: Macro) {
        SettingsSheet(this, m, onSave = { store.save(it); refresh() }, onDelete = { store.delete(it.id); refresh() }).show()
    }
}

private const val MATCH = android.view.ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
