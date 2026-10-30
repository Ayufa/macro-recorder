package jp.ayu.macro

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
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
            setPadding(dp(16), dp(24), dp(16), dp(24))
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
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(14) })
        header.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(ui.text(getString(R.string.app_name), 26f, ui.onSurface, medium = true))
            addView(ui.text("タップ・スワイプを記録して、自動で再生", 13f, ui.onSurfaceVariant))
        })
        root.addView(header)

        statusContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ui.rounded(ui.surfaceContainer, 24)
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        root.addView(statusContainer, LinearLayout.LayoutParams(MATCH, WRAP))

        // 使い方
        root.addView(ui.text("使い方", 14f, ui.primary, medium = true).apply { setPadding(dp(4), dp(24), 0, dp(8)) })
        root.addView(howTo())

        // 一覧
        val listHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(24), dp(4), dp(8))
        }
        listHeader.addView(ui.text("保存したマクロ", 14f, ui.primary, medium = true))
        countView = ui.text("", 12f, ui.onSecondaryContainer, medium = true).apply {
            setPadding(dp(8), dp(1), dp(8), dp(1)); background = ui.rounded(ui.secondaryContainer, 10)
        }
        listHeader.addView(countView, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(8) })
        root.addView(listHeader)
        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listContainer)

        // プラン（monetized フレーバーだけ。standard では表示しない）
        if (entitlements.purchasesAvailable) {
            root.addView(ui.text("プラン", 14f, ui.primary, medium = true).apply { setPadding(dp(4), dp(24), 0, dp(8)) })
            planContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = ui.rounded(ui.surfaceContainer, 24)
                setPadding(dp(16), dp(12), dp(16), dp(12))
            }
            root.addView(planContainer, LinearLayout.LayoutParams(MATCH, WRAP))
        }

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
        root.addView(ui.text("v" + appVersion(), 11f, ui.outlineVariant).apply { setPadding(dp(30), dp(8), 0, 0) })
    }

    private fun appVersion(): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    }.getOrDefault("")

    private fun howTo(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ui.rounded(ui.surfaceContainer, 24)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val steps = listOf(
            R.drawable.ic_record to "フローティングの記録ボタンで記録開始 → ふつうに操作",
            R.drawable.ic_stop to "もう一度押すと記録終了。名前を付けて保存",
            R.drawable.ic_play to "再生ボタンで ★ 選択中のマクロを再生、停止で止まります",
            R.drawable.ic_collapse to "小さくするボタンで丸いアイコンに。タップで元に戻ります",
            R.drawable.ic_settings to "設定ボタンでこの画面に戻れます",
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
            row.addView(ui.text(pair.second, 13f, ui.onSurface), LinearLayout.LayoutParams(0, WRAP, 1f))
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
            if (limit == null) "マクロの保存数: 無制限" else "無料版: マクロは ${limit} 件まで保存できます",
            13f, ui.onSurface))
        fun item(id: String, label: String, owned: Boolean) {
            val price = entitlements.priceOf(id)
            val b = ui.button(if (owned) "$label（購入済み）" else label + (price?.let { "  $it" } ?: ""),
                if (owned) Ui.BtnStyle.TEXT else Ui.BtnStyle.TONAL) { entitlements.launchPurchase(this, id) }
            b.isEnabled = !owned
            box.addView(b, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) })
        }
        item(Products.REMOVE_ADS, "広告を非表示にする", entitlements.isAdFree)
        item(Products.UNLIMITED_MACROS, "マクロを無制限に保存", limit == null)
        if (ads.privacyOptionsAvailable) {
            val b = ui.button("広告のプライバシー設定", Ui.BtnStyle.TEXT) { ads.showPrivacyOptions(this) }
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
        if (isIgnoringBattery()) { toast("すでに除外されています"); return }
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
            running, "ユーザー補助サービス",
            when {
                running -> "動作中"
                enabled -> "オンですが応答がありません（一度オフ→オンに）"
                else -> "オフ：「操作マクロ」をオンにしてください"
            },
            if (running) null else ui.button("開く", Ui.BtnStyle.TONAL) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
        ))
        val ignoring = isIgnoringBattery()
        statusContainer.addView(statusRow(
            ignoring, "電池の最適化",
            if (ignoring) "除外済み" else "最適化中（停止されることがあります）",
            if (ignoring) null else ui.button("除外", Ui.BtnStyle.TONAL) { requestIgnoreBattery() },
        ))
        if (running) {
            val shown = svc?.isPanelShown == true
            statusContainer.addView(statusRow(
                shown, "フローティングボタン", if (shown) "表示中" else "非表示",
                if (shown) null else ui.button("表示", Ui.BtnStyle.FILLED) { svc?.showPanel(); refresh() },
            ))
        }
        val links = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(4), 0, dp(4), 0) }
        links.addView(ui.button("アプリ情報を開く（制限付き設定の許可など）", Ui.BtnStyle.TEXT, R.drawable.ic_open) {
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
                background = ui.rounded(ui.surfaceContainer, 24)
                addView(ImageView(this@MainActivity).apply { setImageDrawable(ui.icon(R.drawable.ic_touch, ui.onSurfaceVariant)) },
                    LinearLayout.LayoutParams(dp(36), dp(36)))
                addView(ui.text("まだマクロがありません", 15f, ui.onSurface, medium = true).apply { setPadding(0, dp(10), 0, dp(2)) })
                addView(ui.text("フローティングの記録ボタンから作成できます", 12f, ui.onSurfaceVariant))
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
            background = if (isSelected) ui.rounded(ui.primaryContainer, 24) else ui.rounded(ui.surfaceContainer, 24)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(10) }
        }
        val fg = if (isSelected) ui.onPrimaryContainer else ui.onSurface
        val fgVar = if (isSelected) ui.onPrimaryContainer else ui.onSurfaceVariant
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(ui.text(m.name, 16f, fg, medium = true).apply { maxLines = 2 }, LinearLayout.LayoutParams(0, WRAP, 1f))
        if (isSelected) top.addView(ui.text("選択中", 11f, ui.onPrimary, medium = true).apply {
            setPadding(dp(8), dp(2), dp(8), dp(2)); background = ui.rounded(ui.primary, 10)
        }, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(8) })
        card.addView(top)
        val kinds = m.steps.groupingBy { it.kindLabel() }.eachCount().entries.joinToString("・") { "${it.key}${it.value}" }
        card.addView(ui.text("${m.steps.size}操作（$kinds）", 13f, fgVar).apply { setPadding(0, dp(2), 0, dp(8)) })
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val rep = if (m.repeatCount == 0) "∞ 無限" else "${m.repeatCount}回"
        for ((i, t) in listOf("繰り返し $rep", "間隔 ${m.loopIntervalMs / 1000.0}秒", "${m.speed}倍速").withIndex()) {
            chips.addView(ui.chip(t).apply { setTextColor(fgVar) },
                LinearLayout.LayoutParams(WRAP, WRAP).apply { if (i > 0) marginStart = dp(6) })
        }
        card.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(chips) })

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, 0)
        }
        actions.addView(ui.button("実行", Ui.BtnStyle.FILLED, R.drawable.ic_play) { runMacro(m) })
        actions.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        fun ib(icon: Int, desc: String, onClick: () -> Unit) = ui.iconButton(icon, desc, 40, onClick).apply {
            setImageDrawable(ui.icon(icon, fgVar))
        }
        actions.addView(ib(if (isSelected) R.drawable.ic_star else R.drawable.ic_star_border, "選択（フローティングの再生で使う）") {
            store.selectedId = m.id; refresh(); toast("フローティングの再生ボタンで再生されます")
        }, LinearLayout.LayoutParams(dp(40), dp(40)))
        actions.addView(ib(R.drawable.ic_tune, "再生設定") { editSettings(m) }, LinearLayout.LayoutParams(dp(40), dp(40)))
        actions.addView(ib(R.drawable.ic_edit, "名前を変更") { rename(m) }, LinearLayout.LayoutParams(dp(40), dp(40)))
        actions.addView(ib(R.drawable.ic_delete, "削除") { confirmDelete(m) }, LinearLayout.LayoutParams(dp(40), dp(40)))
        card.addView(actions)
        return card
    }

    private fun runMacro(m: Macro) {
        val s = MacroAccessibilityService.instance
        if (s == null) { toast("先にユーザー補助で「操作マクロ」をオンにしてください"); return }
        store.selectedId = m.id
        toast("3秒後に再生します。対象のアプリを開いてください")
        moveTaskToBack(true)
        Handler(Looper.getMainLooper()).postDelayed({ MacroAccessibilityService.instance?.play(m) }, 3000)
    }

    private fun rename(m: Macro) {
        val edit = EditText(this).apply { setText(m.name); setSingleLine(); setSelection(text.length) }
        AlertDialog.Builder(this).setTitle("名前を変更").setView(wrap(edit))
            .setPositiveButton("保存") { _, _ ->
                val n = edit.text.toString().trim()
                if (n.isNotEmpty()) { store.save(m.copy(name = n)); refresh() }
            }
            .setNegativeButton("キャンセル", null).show()
    }

    private fun confirmDelete(m: Macro) {
        AlertDialog.Builder(this).setTitle("削除しますか？").setMessage("「${m.name}」を削除します。元に戻せません。")
            .setPositiveButton("削除") { _, _ -> store.delete(m.id); refresh() }
            .setNegativeButton("キャンセル", null).show()
    }

    private fun wrap(v: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0); addView(v)
    }

    @SuppressLint("SetTextI18n")
    private fun editSettings(m: Macro) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val infinite = CheckBox(this).apply { text = "無限に繰り返す（停止ボタンで止める）"; isChecked = m.repeatCount == 0 }
        val count = EditText(this).apply {
            hint = "繰り返し回数"; inputType = InputType.TYPE_CLASS_NUMBER
            setText(if (m.repeatCount == 0) "1" else m.repeatCount.toString()); isEnabled = !infinite.isChecked
        }
        infinite.setOnCheckedChangeListener { _, c -> count.isEnabled = !c }
        val interval = EditText(this).apply {
            hint = "ループ間の待ち時間（秒）"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText((m.loopIntervalMs / 1000.0).toString())
        }
        // 0.5〜4.0 を 0.25 刻み
        val speedLabel = TextView(this)
        val speedBar = SeekBar(this).apply {
            max = ((Macro.MAX_SPEED - Macro.MIN_SPEED) / 0.25f).toInt()
            progress = ((m.speed - Macro.MIN_SPEED) / 0.25f).toInt()
        }
        fun speedOf(p: Int) = Macro.MIN_SPEED + p * 0.25f
        speedLabel.text = "再生速度: ${speedOf(speedBar.progress)}倍"
        speedBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { speedLabel.text = "再生速度: ${speedOf(p)}倍" }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        box.addView(TextView(this).apply { text = "繰り返し回数" }); box.addView(count); box.addView(infinite)
        box.addView(TextView(this).apply { text = "ループ間の待ち時間（秒）" }); box.addView(interval)
        box.addView(speedLabel); box.addView(speedBar)
        AlertDialog.Builder(this).setTitle("再生設定").setView(box)
            .setPositiveButton("保存") { _, _ ->
                val rc = if (infinite.isChecked) 0 else (count.text.toString().toIntOrNull() ?: 1).coerceIn(1, 100000)
                val iv = ((interval.text.toString().toDoubleOrNull() ?: 1.0).coerceIn(0.0, 3600.0) * 1000).toLong()
                store.save(m.copy(repeatCount = rc, loopIntervalMs = iv, speed = speedOf(speedBar.progress)))
                refresh()
            }
            .setNegativeButton("キャンセル", null).show()
    }
}

private const val MATCH = android.view.ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
