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

        // ヘッダー（v0.7: 端に向かって消える方眼の背景 + バージョンの等幅バッジ）
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(32), dp(20), dp(28))
            background = GridDrawable(ui.grid, dp(22).toFloat(), dp(1).coerceAtLeast(1).toFloat())
        }
        header.addView(ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(14) })
        header.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val titleRow = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            titleRow.addView(ui.text(getString(R.string.app_name), 26f, ui.onSurface, weight = Ui.Weight.SEMIBOLD).apply { maxLines = 2 },
                LinearLayout.LayoutParams(WRAP, WRAP))
            titleRow.addView(ui.badge("v" + appVersion(), Ui.Tone.NEUTRAL).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(10) })
            addView(titleRow)
            addView(ui.text(getString(R.string.tagline), 13f, ui.onSurfaceVariant).apply { setPadding(0, dp(4), 0, 0) })
        }, LinearLayout.LayoutParams(0, WRAP, 1f))
        // 方眼を画面の端まで広げる（root の余白を打ち消す）
        root.addView(header, LinearLayout.LayoutParams(MATCH, WRAP).apply {
            marginStart = -dp(16); marginEnd = -dp(16); topMargin = -dp(24); bottomMargin = dp(4)
        })

        statusContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ui.card()
            setPadding(dp(4), dp(4), dp(4), dp(6))
        }
        root.addView(statusContainer, LinearLayout.LayoutParams(MATCH, WRAP))

        // 使い方
        root.addView(ui.sectionHeader(getString(R.string.section_how_to)))
        root.addView(howTo())

        // 一覧
        countView = ui.badge("0")
        root.addView(ui.sectionHeader(getString(R.string.section_macros), countView))
        // v0.7: 一覧は1枚の枠の中に、区切り線で行を並べる（sv-table のデータ表を参考）
        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ui.card()
            clipToOutline = true
        }
        root.addView(listContainer, LinearLayout.LayoutParams(MATCH, WRAP))

        // プラン（monetized フレーバーだけ。standard では表示しない）
        if (entitlements.purchasesAvailable) {
            root.addView(ui.sectionHeader(getString(R.string.section_plan)))
            planContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = ui.card()
                setPadding(dp(16), dp(12), dp(16), dp(12))
            }
            root.addView(planContainer, LinearLayout.LayoutParams(MATCH, WRAP))
        }

        // アプリの設定（言語）
        root.addView(ui.sectionHeader(getString(R.string.section_app_settings)))
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
        root.addView(ui.monoText(getString(R.string.version_label, appVersion()), 11f, ui.ink3).apply { setPadding(dp(30), dp(10), 0, 0) })

        // 最初の表示だけ、上から順にふわっと出す
        for (i in 0 until root.childCount) Motion.enter(root.getChildAt(i), i)
    }

    /** 一度表示した行は、再描画のたびにアニメーションしない */
    private val seenRows = HashSet<String>()

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
            Motion.pressable(this, 0.98f)
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
            row.addView(ui.monoText("%02d".format(i + 1), 11f, ui.ink3, Ui.Weight.MEDIUM).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(24), WRAP))
            row.addView(ImageView(this).apply {
                setImageDrawable(ui.icon(pair.first, ui.onSecondaryContainer)); setPadding(dp(6), dp(6), dp(6), dp(6))
                background = ui.rounded(ui.secondaryContainer, 9, ui.outlineVariant)
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
    private fun statusRow(ok: Boolean, title: String, sub: String, action: View?, live: Boolean = false): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        // v0.7: 状態は角丸の小さなタイルで。表示中のフローティングはドットマトリクスが動く（sv-matrix 風）
        val lead: View = if (live) DotMatrixView(this, 3, 3, dp(4).toFloat(), dp(2).toFloat()).apply {
            color = ui.success; pattern = DotMatrixView.Pattern.WAVE
        } else ImageView(this).apply {
            setImageDrawable(ui.icon(if (ok) R.drawable.ic_check_circle else R.drawable.ic_error, if (ok) ui.success else ui.error))
            setPadding(dp(5), dp(5), dp(5), dp(5))
        }
        lead.background = ui.rounded(if (ok) ui.successContainer else ui.errorContainer, 9,
            ui.withAlpha(if (ok) ui.success else ui.error, 0.25f))
        row.addView(lead, LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginEnd = dp(14) })
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(ui.text(title, 15f, ui.onSurface, medium = true))
            addView(ui.text(sub, 12f, ui.onSurfaceVariant))
        }, LinearLayout.LayoutParams(0, WRAP, 1f))
        if (action != null) row.addView(action, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(8) })
        return row
    }

    // 進み具合のバーは「進んだとき」だけ伸ばす（画面に戻るたびには動かさない）
    private var lastSetupDone = -1
    private val isGalaxy = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    /**
     * v0.8: 状態カードを「セットアップ」のステップ表示に。
     * 参考にしたパターン: appshots / screensdesign / pageflows / mobbin のオンボーディング・フロー
     * （進み具合のバー + 番号つきステップ + いまのステップだけ開いて CTA を1つ）。画像や素材は使っていない。
     */
    private fun buildSetup(svc: MacroAccessibilityService?, running: Boolean, enabled: Boolean) {
        statusContainer.removeAllViews()
        val ignoring = isIgnoringBattery()
        val doneFlags = listOf(running, ignoring)
        val done = doneFlags.count { it }
        val total = doneFlags.size
        if (done < total) {
            // 見出し + 進み具合
            val head = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(16), dp(16), dp(4))
            }
            head.addView(ui.text(getString(R.string.setup_title), Ui.Type.TITLE, ui.onSurface, weight = Ui.Weight.SEMIBOLD),
                LinearLayout.LayoutParams(0, WRAP, 1f))
            head.addView(ui.monoText("$done/$total", Ui.Type.CAPTION, ui.onSurfaceVariant).apply {
                contentDescription = getString(R.string.setup_progress, done, total)
            })
            statusContainer.addView(head)
            val bar = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; setPadding(dp(16), dp(8), dp(16), dp(8))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            for (n in 0 until total) {
                val seg = View(this).apply {
                    background = ui.rounded(if (n < done) ui.primary else ui.outlineVariant, 999)
                }
                bar.addView(seg, LinearLayout.LayoutParams(0, dp(4), 1f).apply { if (n > 0) marginStart = dp(6) })
                if (n == done - 1 && done > lastSetupDone && Motion.enabled(this)) {
                    seg.pivotX = 0f; seg.scaleX = 0f
                    seg.animate().scaleX(1f).setStartDelay(120).setDuration(500).setInterpolator(Motion.easeOut).start()
                }
            }
            statusContainer.addView(bar)
            lastSetupDone = done

            val appInfo = { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
            val svcName = getString(R.string.service_label)
            statusContainer.addView(setupStep(
                number = 1, done = running, current = !running, last = false,
                title = getString(R.string.status_service),
                doneText = getString(R.string.status_service_running),
                body = if (enabled) getString(R.string.status_service_stuck) else getString(R.string.setup_service_body, svcName),
                hints = listOfNotNull(
                    if (isGalaxy) getString(R.string.setup_service_galaxy, svcName) else null,
                    if (Build.VERSION.SDK_INT >= 33) getString(R.string.setup_restricted_hint) else null,
                ),
                cta = getString(R.string.action_open) to { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                secondary = getString(R.string.action_app_info) to appInfo,
            ))
            statusContainer.addView(setupStep(
                number = 2, done = ignoring, current = running && !ignoring, last = true,
                title = getString(R.string.status_battery), recommended = true,
                doneText = getString(R.string.status_battery_ok),
                body = getString(R.string.setup_battery_body),
                hints = listOfNotNull(if (isGalaxy) getString(R.string.setup_battery_galaxy) else null),
                cta = getString(R.string.action_exclude) to { requestIgnoreBattery() },
                // 1 がまだでも、電池の除外は先にできる
                forceCta = !running && !ignoring,
            ))
        }
        if (running) {
            val shown = svc?.isPanelShown == true
            if (done < total) statusContainer.addView(ui.divider(), LinearLayout.LayoutParams(MATCH, dp(1)).apply {
                topMargin = dp(4); marginStart = dp(16); marginEnd = dp(16)
            })
            statusContainer.addView(statusRow(
                shown,
                getString(if (done == total) R.string.setup_done_title else R.string.status_panel),
                getString(when {
                    shown -> R.string.status_panel_shown
                    done == total -> R.string.setup_done_body
                    else -> R.string.status_panel_hidden
                }),
                if (shown) null else ui.button(getString(R.string.action_show), Ui.BtnStyle.FILLED) {
                    Motion.haptic(statusContainer, Motion.Haptic.CONFIRM); svc?.showPanel(); refresh()
                },
                live = shown,
            ))
        }
        if (statusContainer.childCount == 0) statusContainer.visibility = View.GONE else statusContainer.visibility = View.VISIBLE
    }

    /** セットアップの1ステップ（番号ノード + 縦の線 + 内容）。いまのステップだけ説明と CTA を出す */
    private fun setupStep(
        number: Int, done: Boolean, current: Boolean, last: Boolean,
        title: String, doneText: String, body: String, hints: List<String>,
        cta: Pair<String, () -> Unit>, secondary: Pair<String, () -> Unit>? = null,
        recommended: Boolean = false, forceCta: Boolean = false,
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(8), dp(16), if (last) dp(14) else 0)
        }
        // 左: ノードと線
        val rail = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        val node: View = if (done) ImageView(this).apply {
            setImageDrawable(ui.icon(R.drawable.ic_check, ui.success)); setPadding(dp(5), dp(5), dp(5), dp(5))
            background = ui.rounded(ui.successContainer, 999, ui.withAlpha(ui.success, 0.3f))
        } else ui.monoText(number.toString(), Ui.Type.CAPTION, if (current) ui.onPrimary else ui.onSurfaceVariant, Ui.Weight.SEMIBOLD).apply {
            gravity = Gravity.CENTER
            background = if (current) ui.rounded(ui.primary, 999) else ui.rounded(ui.surfaceContainer, 999, ui.outlineVariant)
        }
        node.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        rail.addView(node, LinearLayout.LayoutParams(dp(26), dp(26)))
        if (!last) rail.addView(View(this).apply { setBackgroundColor(if (done) ui.withAlpha(ui.success, 0.5f) else ui.outlineVariant) },
            LinearLayout.LayoutParams(dp(2), 0, 1f).apply { topMargin = dp(4) })
        row.addView(rail, LinearLayout.LayoutParams(dp(26), MATCH).apply { marginEnd = dp(14) })

        // 右: 内容
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(2), 0, dp(12)) }
        val titleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(ui.text(title, Ui.Type.BODY_LARGE, if (done) ui.onSurfaceVariant else ui.onSurface, medium = true),
            LinearLayout.LayoutParams(0, WRAP, 1f))
        if (recommended && !done) titleRow.addView(ui.badge(getString(R.string.setup_recommended)))
        col.addView(titleRow)
        if (done) {
            col.addView(ui.text(doneText, Ui.Type.CAPTION, ui.success))
        } else if (current || forceCta) {
            col.addView(ui.text(body, Ui.Type.BODY, ui.onSurfaceVariant).apply { setPadding(0, dp(4), 0, 0) })
            if (current) for (h in hints) {
                col.addView(ui.text(h, Ui.Type.CAPTION, ui.onSurfaceVariant).apply {
                    background = ui.rounded(ui.surfaceContainerHigh, 10); setPadding(dp(10), dp(8), dp(10), dp(8))
                }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) })
            }
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            actions.addView(ui.button(cta.first, if (current) Ui.BtnStyle.FILLED else Ui.BtnStyle.TONAL) {
                Motion.haptic(row, Motion.Haptic.CONFIRM); cta.second()
            })
            if (secondary != null && current) actions.addView(ui.button(secondary.first, Ui.BtnStyle.TEXT, R.drawable.ic_open) { secondary.second() },
                LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(4) })
            col.addView(actions, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(10) })
        } else {
            col.addView(ui.text(body, Ui.Type.CAPTION, ui.onSurfaceVariant).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            row.alpha = 0.6f
        }
        row.addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
        return row
    }

    @SuppressLint("SetTextI18n")
    private fun refresh() {
        val svc = MacroAccessibilityService.instance
        val running = svc != null
        val enabled = isServiceEnabled()
        buildSetup(svc, running, enabled)

        listContainer.removeAllViews()
        val macros = store.list()
        countView.text = entitlements.maxSavedMacros?.let { "%02d / %d".format(macros.size, it) } ?: "%02d".format(macros.size)
        refreshMonetization()
        if (macros.isEmpty()) {
            // v0.7: 空のときは枠を点線にして、ドットマトリクスを表示
            listContainer.background = ui.dashed(ui.surfaceContainer, 20)
            listContainer.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(16), dp(32), dp(16), dp(32))
                // v0.8: sv-matrix の path-wave（蛇行の波）を移植した SNAKE パターン
                addView(DotMatrixView(this@MainActivity, 5, 5, dp(5).toFloat(), dp(3).toFloat()).apply {
                    color = ui.onSurfaceVariant; pattern = DotMatrixView.Pattern.SNAKE
                }, LinearLayout.LayoutParams(WRAP, WRAP))
                addView(ui.text(getString(R.string.empty_title), Ui.Type.BODY_LARGE, ui.onSurface, medium = true).apply { setPadding(0, dp(16), 0, dp(4)) })
                addView(ui.text(getString(R.string.empty_body), Ui.Type.BODY, ui.onSurfaceVariant).apply { gravity = Gravity.CENTER })
                // v0.8: 空の状態に「次にやること」を1つだけ（pttrns / collectui の empty state パターン）
                val svc = MacroAccessibilityService.instance
                val cta = if (svc != null)
                    ui.button(getString(R.string.empty_cta), Ui.BtnStyle.FILLED, R.drawable.ic_record) {
                        Motion.haptic(this, Motion.Haptic.CONFIRM)
                        if (!svc.isPanelShown) svc.showPanel()
                        // 記録したいアプリへ移れるよう、このアプリは後ろへ
                        moveTaskToBack(true)
                    }
                else ui.button(getString(R.string.empty_cta_setup), Ui.BtnStyle.TONAL, R.drawable.ic_open) {
                    Motion.haptic(this, Motion.Haptic.CONFIRM)
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                addView(cta, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(16) })
            }, LinearLayout.LayoutParams(MATCH, WRAP))
            return
        }
        listContainer.background = ui.card()
        val selected = store.selectedId
        var fresh = 0
        for ((i, m) in macros.withIndex()) {
            if (i > 0) listContainer.addView(ui.divider(), LinearLayout.LayoutParams(MATCH, dp(1).coerceAtLeast(1)))
            val v = row(m, i, m.id == selected)
            listContainer.addView(v)
            if (seenRows.add(m.id)) Motion.enter(v, fresh++)
        }
        // v0.8: 操作のヒント（長押しメニューは見えないので、一行だけ案内）
        listContainer.addView(ui.divider(), LinearLayout.LayoutParams(MATCH, dp(1).coerceAtLeast(1)))
        listContainer.addView(ui.text(getString(R.string.list_hint), Ui.Type.MICRO, ui.ink3).apply {
            gravity = Gravity.CENTER; setPadding(dp(16), dp(10), dp(16), dp(10))
        }, LinearLayout.LayoutParams(MATCH, WRAP))
    }

    /** v0.8: 選択（触覚 + 星のポン）。行のタップ・星ボタン・長押しメニューから共通で使う */
    private var popSelectedId: String? = null
    private fun select(m: Macro, from: View) {
        if (store.selectedId == m.id) { Motion.haptic(from, Motion.Haptic.TICK); return }
        Motion.haptic(from, Motion.Haptic.CONFIRM)
        store.selectedId = m.id; popSelectedId = m.id
        refresh(); toast(getString(R.string.toast_selected))
    }

    /**
     * v0.8: 行の長押しメニュー（下から出るアクションシート）。
     * mobbin / 60fps の Bottom Sheet・Action sheet のパターンを、既存の BottomSheet で作り直したもの。
     */
    private fun actionSheet(m: Macro) {
        val sheet = BottomSheet(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = ui.sheetBackground() }
        val handle = View(this).apply { background = ui.rounded(ui.outline, 999) }
        root.addView(handle, LinearLayout.LayoutParams(dp(36), dp(4)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(10) })
        val head = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(14), dp(24), dp(8)) }
        head.addView(ui.text(m.name, Ui.Type.TITLE, ui.onSurface, weight = Ui.Weight.SEMIBOLD).apply { maxLines = 2 })
        head.addView(ui.text(getString(R.string.macro_summary, Texts.steps(this, m.steps.size), Texts.kinds(this, m.steps)),
            Ui.Type.BODY, ui.onSurfaceVariant).apply { setPadding(0, dp(2), 0, 0) })
        root.addView(head)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(4), dp(12), dp(12)) }
        fun item(icon: Int, label: String, danger: Boolean = false, action: () -> Unit) {
            val color = if (danger) ui.error else ui.onSurface
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(52); setPadding(dp(12), 0, dp(12), 0)
                background = ui.ripple(null, ui.rounded(android.graphics.Color.WHITE, 14))
                isClickable = true; isFocusable = true
                setOnClickListener { sheet.dismiss(); action() }
            }
            r.addView(ImageView(this).apply {
                setImageDrawable(ui.icon(icon, if (danger) ui.error else ui.onSurfaceVariant)); setPadding(dp(6), dp(6), dp(6), dp(6))
                background = ui.rounded(if (danger) ui.errorContainer else ui.surfaceContainerHigh, 10)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(14) })
            r.addView(ui.text(label, Ui.Type.BODY_LARGE, color, medium = true), LinearLayout.LayoutParams(0, WRAP, 1f))
            list.addView(r, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        item(R.drawable.ic_play, getString(R.string.action_run)) { runMacro(m) }
        if (store.selectedId != m.id) item(R.drawable.ic_star, getString(R.string.desc_select)) { select(m, listContainer) }
        item(R.drawable.ic_tune, getString(R.string.desc_settings)) { editSettings(m) }
        item(R.drawable.ic_edit, getString(R.string.desc_rename)) { rename(m) }
        list.addView(ui.divider(), LinearLayout.LayoutParams(MATCH, dp(1)).apply { topMargin = dp(4); bottomMargin = dp(4); marginStart = dp(12); marginEnd = dp(12) })
        item(R.drawable.ic_delete, getString(R.string.delete), danger = true) { confirmDelete(m) }
        root.addView(list)
        root.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION") v.setPadding(0, 0, 0, insets.systemWindowInsetBottom.coerceAtMost(dp(48)))
            insets
        }
        sheet.attach(root)
        sheet.dragToDismiss(handle, head)
        sheet.show()
        for (i in 0 until list.childCount) Motion.enter(list.getChildAt(i), i + 1)
    }

    /**
     * v0.7: マクロの1行（表の行のような見た目）。
     * 番号（等幅）・名前・内訳、設定値は等幅のセル風タグ、操作ボタンは右寄せ。選択中は左端にアクセントの線。
     */
    @SuppressLint("SetTextI18n")
    private fun row(m: Macro, index: Int, isSelected: Boolean): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(8), dp(8))
            background = if (isSelected) android.graphics.drawable.LayerDrawable(arrayOf(
                android.graphics.drawable.ColorDrawable(ui.withAlpha(ui.primaryContainer, 0.7f)),
                android.graphics.drawable.ColorDrawable(ui.primary),
            )).apply {
                setLayerWidth(1, dp(3)); setLayerGravity(1, Gravity.START or Gravity.FILL_VERTICAL)
            } else null
            // v0.8: 行全体を押せるように（押したときのリップル + タップで選択 + 長押しでメニュー）
            foreground = ui.ripple(null, android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE))
            isClickable = true; isLongClickable = true; isFocusable = true
            setOnClickListener { v -> select(m, v) }
            setOnLongClickListener { v -> Motion.haptic(v, Motion.Haptic.LONG_PRESS); actionSheet(m); true }
        }
        val fg = if (isSelected) ui.onPrimaryContainer else ui.onSurface
        val fgVar = if (isSelected) ui.onPrimaryContainer else ui.onSurfaceVariant
        val indent = dp(30)
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(ui.monoText("%02d".format(index + 1), 12f, if (isSelected) ui.primary else ui.ink3, Ui.Weight.MEDIUM).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(indent, WRAP))
        top.addView(ui.text(m.name, 16f, fg, medium = true).apply { maxLines = 2 }, LinearLayout.LayoutParams(0, WRAP, 1f))
        if (isSelected) top.addView(ui.badge(getString(R.string.selected_badge), Ui.Tone.ACCENT),
            LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(8) })
        card.addView(top)
        card.addView(ui.text(getString(R.string.macro_summary, Texts.steps(this, m.steps.size), Texts.kinds(this, m.steps)), 13f, fgVar).apply {
            setPadding(indent, dp(3), dp(8), dp(10))
        })
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(indent, 0, dp(8), 0) }
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
            setPadding(indent - dp(2), dp(10), 0, 0)
        }
        actions.addView(ui.button(getString(R.string.action_run), Ui.BtnStyle.FILLED, R.drawable.ic_play) { runMacro(m) }.apply {
            minHeight = dp(36); textSize = 13f
        })
        actions.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        fun ib(icon: Int, desc: String, onClick: () -> Unit) = ui.iconButton(icon, desc, 40, onClick).apply {
            setImageDrawable(ui.icon(icon, fgVar))
        }
        actions.addView(ib(if (isSelected) R.drawable.ic_star else R.drawable.ic_star_border, getString(R.string.desc_select)) {
            select(m, card)
        }.apply {
            if (isSelected) setImageDrawable(ui.icon(R.drawable.ic_star, ui.primary))
            if (isSelected && popSelectedId == m.id) { popSelectedId = null; post { Motion.pop(this) } }
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
