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
    private lateinit var snackHost: android.widget.FrameLayout
    private lateinit var scroll: ScrollView
    private lateinit var root: LinearLayout
    private lateinit var header: LinearLayout
    private var sysInsets = Bars(0, 0, 0, 0)

    /** システムバー / 切り欠きの幅（android.graphics.Insets は API 29+ なので自前で） */
    private data class Bars(val left: Int, val top: Int, val right: Int, val bottom: Int)
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
        applySystemBarAppearance()
        scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(ui.surface); clipToPadding = false }
        // 画面全体: 上にスクロール、下に広告枠（standard では常に非表示）。その上にスナックバーを重ねる
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(ui.surface) }
        outer.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        adContainer = android.widget.FrameLayout(this).apply { visibility = View.GONE }
        outer.addView(adContainer, LinearLayout.LayoutParams(MATCH, WRAP))
        snackHost = android.widget.FrameLayout(this).apply { addView(outer, android.widget.FrameLayout.LayoutParams(MATCH, MATCH)) }
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(root)
        // v0.9: edge-to-edge（R3.7 / R3.8）。内容はシステムバーの下までスクロールし、余白は内側（root）でとる。
        // 横のバー / 切り欠きの分だけは外側でよける。
        snackHost.setOnApplyWindowInsetsListener { _, insets ->
            sysInsets = if (Build.VERSION.SDK_INT >= 30) {
                insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()).let { Bars(it.left, it.top, it.right, it.bottom) }
            } else {
                @Suppress("DEPRECATION")
                Bars(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            applyContentPadding()
            insets
        }
        // v0.9: 大きい画面では本文の幅を 840dp までにして中央へ（R3.4）
        scroll.addOnLayoutChangeListener { _, l, _, r, _, ol, _, or_, _ -> if (r - l != or_ - ol) scroll.post { applyContentPadding() } }
        setContentView(snackHost)

        // ヘッダー（v0.7: 端に向かって消える方眼の背景 + バージョンの等幅バッジ）
        header = LinearLayout(this).apply {
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
            titleRow.addView(ui.text(getString(R.string.app_name), Ui.Type.HEADLINE_SMALL, ui.onSurface, weight = Ui.Weight.SEMIBOLD).apply {
                maxLines = 2
                if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
            },
                LinearLayout.LayoutParams(WRAP, WRAP))
            titleRow.addView(ui.badge("v" + appVersion(), Ui.Tone.NEUTRAL).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(10) })
            addView(titleRow)
            addView(ui.text(getString(R.string.tagline), Ui.Type.BODY_MEDIUM, ui.onSurfaceVariant).apply { setPadding(0, dp(4), 0, 0) })
        }, LinearLayout.LayoutParams(0, WRAP, 1f))
        // 方眼を画面の端まで広げる（root の余白を打ち消す。値は applyContentPadding で設定）
        root.addView(header, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(4) })
        applyContentPadding()

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

        // アプリの設定（言語・v0.9: テーマ）
        root.addView(ui.sectionHeader(getString(R.string.section_app_settings)))
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = ui.card(); clipToOutline = true
            addView(settingsRow(R.drawable.ic_language, getString(R.string.language),
                getString(AppLocales.nameRes(AppLocales.current(this@MainActivity)))) { pickLanguage() })
            addView(ui.divider(), LinearLayout.LayoutParams(MATCH, dp(1).coerceAtLeast(1)).apply { marginStart = dp(64) })
            addView(settingsRow(R.drawable.ic_contrast, getString(R.string.theme),
                getString(ThemeMode.nameRes(ThemeMode.current(this@MainActivity)))) { pickTheme() })
        }, LinearLayout.LayoutParams(MATCH, WRAP))

        // プライバシー
        val privacy = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(dp(4), dp(24), dp(4), 0)
        }
        privacy.addView(ImageView(this).apply {
            setImageDrawable(ui.icon(R.drawable.ic_shield, ui.onSurfaceVariant))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        },
            LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(8); topMargin = dp(1) })
        privacy.addView(ui.text(getString(R.string.privacy_note),
            Ui.Type.BODY_SMALL, ui.onSurfaceVariant), LinearLayout.LayoutParams(0, WRAP, 1f))
        root.addView(privacy)
        ads.initialize(this)
        root.addView(ui.monoText(getString(R.string.version_label, appVersion()), Ui.Type.LABEL_SMALL, ui.ink3).apply { setPadding(dp(30), dp(10), 0, 0) })

        // 最初の表示だけ、上から順にふわっと出す
        for (i in 0 until root.childCount) Motion.enter(root.getChildAt(i), i)

        if (savedInstanceState == null) handleShortcut(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShortcut(intent)
    }

    /** v0.9: ランチャーのショートカット（R10）。処理したら同じ操作を繰り返さないように消す */
    private fun handleShortcut(i: Intent?) {
        val action = i?.action ?: return
        if (action != ACTION_SHOW_PANEL && action != ACTION_PLAY_SELECTED) return
        setIntent(Intent(this, MainActivity::class.java))
        val svc = MacroAccessibilityService.instance
        if (svc == null) {
            snack(getString(R.string.toast_enable_service_first, getString(R.string.app_name)), getString(R.string.action_open)) {
                openAccessibilitySettings()
            }
            return
        }
        when (action) {
            ACTION_SHOW_PANEL -> {
                if (!svc.isPanelShown) svc.showPanel()
                moveTaskToBack(true)
            }
            ACTION_PLAY_SELECTED -> {
                val m = store.selectedId?.let { store.get(it) } ?: store.list().firstOrNull()
                if (m == null) snack(getString(R.string.toast_no_macros_short)) else runMacro(m)
            }
        }
    }

    /**
     * v0.9: 余白。画面幅 600dp 以上は 24dp、それ未満は 16dp（R3.5）。本文は最大 840dp で中央寄せ。
     * 上下はシステムバーの分を足す（内容はバーの下までスクロールする）。
     */
    private fun applyContentPadding() {
        if (!::root.isInitialized) return
        val w = scroll.width.takeIf { it > 0 } ?: (resources.displayMetrics.widthPixels - sysInsets.left - sysInsets.right)
        val margin = if (resources.configuration.screenWidthDp >= 600) dp(24) else dp(16)
        val side = maxOf(margin, (w - dp(840)) / 2)
        val adShown = adContainer.visibility == View.VISIBLE
        snackHost.getChildAt(0)?.setPadding(sysInsets.left, 0, sysInsets.right, 0)
        adContainer.setPadding(0, 0, 0, if (adShown) sysInsets.bottom else 0)
        root.setPadding(side, dp(24) + sysInsets.top, side, dp(28) + if (adShown) 0 else sysInsets.bottom)
        // スナックバーはナビゲーションバー（と広告）の上に
        snackHost.setPadding(sysInsets.left, 0, sysInsets.right, if (adShown) adContainer.height + sysInsets.bottom else sysInsets.bottom)
        if (::header.isInitialized) {
            (header.layoutParams as? LinearLayout.LayoutParams)?.let {
                it.marginStart = -side; it.marginEnd = -side; it.topMargin = -(dp(24) + sysInsets.top)
                header.layoutParams = it
            }
            header.setPadding(side + dp(4), dp(32) + sysInsets.top, side + dp(4), dp(28))
        }
    }

    /** v0.9: ライトテーマではナビゲーションバーのアイコンも濃い色に（R1.7） */
    private fun applySystemBarAppearance() {
        val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        if (Build.VERSION.SDK_INT >= 30) {
            val mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (night) 0 else mask, mask)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (night) 0 else
                (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
        }
    }

    /** v0.9: トーストの代わりにスナックバー（アプリが前面にあるとき。R5.14） */
    private fun snack(msg: String, action: String? = null, onAction: (() -> Unit)? = null) =
        Snackbar.of(snackHost, ui).show(msg, action, onAction)

    /** 一度表示した行は、再描画のたびにアニメーションしない */
    private val seenRows = HashSet<String>()

    private fun appVersion(): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    }.getOrDefault("")

    /**
     * 設定の行（タップで選択）。言語は Android 13+ ではシステムの「アプリの言語」と同じ設定。
     * v0.9: テーマの行も同じ形で。アイコンと矢印は飾りなので読み上げない（R6.1 / R6.2）
     */
    private fun settingsRow(icon: Int, title: String, value: String, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56)
            setPadding(dp(16), dp(12), dp(12), dp(12))
            background = ui.ripple(null, android.graphics.drawable.ColorDrawable(Color.WHITE))
            isClickable = true; isFocusable = true
            setOnClickListener { onClick() }
        }
        row.addView(ImageView(this).apply {
            setImageDrawable(ui.icon(icon, ui.onSecondaryContainer)); setPadding(dp(7), dp(7), dp(7), dp(7))
            background = ui.oval(ui.secondaryContainer)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(14) })
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(ui.text(title, Ui.Type.BODY_LARGE, ui.onSurface, medium = true))
            addView(ui.text(value, Ui.Type.BODY_MEDIUM, ui.onSurfaceVariant))
        }, LinearLayout.LayoutParams(0, WRAP, 1f))
        row.addView(ImageView(this).apply {
            setImageDrawable(ui.icon(R.drawable.ic_chevron_right, ui.ink3))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            // 右から左へ書く言語では向きを反転
            isAutoMirrored()
        }, LinearLayout.LayoutParams(dp(20), dp(20)))
        return row
    }

    private fun ImageView.isAutoMirrored() { drawable?.isAutoMirrored = true }

    private fun pickTheme() {
        val modes = ThemeMode.ALL
        val cur = modes.indexOf(ThemeMode.current(this)).coerceAtLeast(0)
        Dialogs.choice(this, getString(R.string.theme), modes.map { getString(ThemeMode.nameRes(it)) }, cur, R.drawable.ic_contrast) { i ->
            ThemeMode.set(this, modes[i])
        }
    }

    /**
     * v0.9: ユーザー補助の設定を開く前に、何に使うか・何をしないかを説明する（R9.2 / Google Play の
     * Accessibility API ポリシーの「目立つ開示」）。同意したときだけ設定画面へ。
     */
    private fun openAccessibilitySettings() {
        Dialogs.show(this, getString(R.string.disclosure_title), getString(R.string.disclosure_message),
            iconId = R.drawable.ic_shield, confirmLabel = getString(R.string.disclosure_continue),
            cancelLabel = getString(R.string.not_now)) {
            runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            true
        }
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
            row.addView(ui.monoText("%02d".format(i + 1), Ui.Type.LABEL_SMALL, ui.ink3, Ui.Weight.MEDIUM).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(24), WRAP))
            row.addView(ImageView(this).apply {
                setImageDrawable(ui.icon(pair.first, ui.onSecondaryContainer)); setPadding(dp(6), dp(6), dp(6), dp(6))
                background = ui.rounded(ui.secondaryContainer, 9, ui.outlineVariant)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginEnd = dp(12) })
            row.addView(ui.text(getString(pair.second), Ui.Type.BODY_MEDIUM, ui.onSurface), LinearLayout.LayoutParams(0, WRAP, 1f))
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
        adContainer.post { applyContentPadding() }
        val box = planContainer ?: return
        box.removeAllViews()
        val limit = entitlements.maxSavedMacros
        box.addView(ui.text(
            if (limit == null) getString(R.string.plan_unlimited) else resources.getQuantityString(R.plurals.plan_free_limit, limit, limit),
            Ui.Type.BODY_MEDIUM, ui.onSurface))
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

    /** アプリが後ろへ下がるとき（再生の予告）だけトーストを使う。それ以外はスナックバー */
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
        if (isIgnoringBattery()) { snack(getString(R.string.toast_battery_already)); return }
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
        // 状態は文字（タイトル + 説明）で伝えるので、アイコンは飾り（R6.8: 色だけに頼らない）
        lead.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        lead.background = ui.rounded(if (ok) ui.successContainer else ui.errorContainer, 9,
            ui.withAlpha(if (ok) ui.success else ui.error, 0.25f))
        row.addView(lead, LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginEnd = dp(14) })
        row.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(ui.text(title, Ui.Type.TITLE_MEDIUM, ui.onSurface, medium = true))
            addView(ui.text(sub, Ui.Type.BODY_SMALL, ui.onSurfaceVariant))
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
            head.addView(ui.text(getString(R.string.setup_title), Ui.Type.TITLE_LARGE, ui.onSurface, weight = Ui.Weight.SEMIBOLD).apply {
                if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
            },
                LinearLayout.LayoutParams(0, WRAP, 1f))
            head.addView(ui.monoText("$done/$total", Ui.Type.LABEL_MEDIUM, ui.onSurfaceVariant).apply {
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
                cta = getString(R.string.action_open) to { openAccessibilitySettings() },
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
        } else ui.monoText(number.toString(), Ui.Type.LABEL_MEDIUM, if (current) ui.onPrimary else ui.onSurfaceVariant, Ui.Weight.SEMIBOLD).apply {
            gravity = Gravity.CENTER
            // v0.9: 文字を大きくしても丸からはみ出さないように縮める（R4.4 / R6.9）
            setLineSpacing(0f, 1f)
            setAutoSizeTextTypeUniformWithConfiguration(8, 12, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
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
        titleRow.addView(ui.text(title, Ui.Type.TITLE_MEDIUM, if (done) ui.onSurfaceVariant else ui.onSurface, medium = true),
            LinearLayout.LayoutParams(0, WRAP, 1f))
        if (recommended && !done) titleRow.addView(ui.badge(getString(R.string.setup_recommended)))
        col.addView(titleRow)
        if (done) {
            col.addView(ui.text(doneText, Ui.Type.BODY_SMALL, ui.success))
        } else if (current || forceCta) {
            col.addView(ui.text(body, Ui.Type.BODY_MEDIUM, ui.onSurfaceVariant).apply { setPadding(0, dp(4), 0, 0) })
            if (current) for (h in hints) {
                col.addView(ui.text(h, Ui.Type.BODY_SMALL, ui.onSurfaceVariant).apply {
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
            col.addView(ui.text(body, Ui.Type.BODY_SMALL, ui.onSurfaceVariant).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
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
                addView(ui.text(getString(R.string.empty_title), Ui.Type.TITLE_MEDIUM, ui.onSurface, medium = true).apply { setPadding(0, dp(16), 0, dp(4)) })
                addView(ui.text(getString(R.string.empty_body), Ui.Type.BODY_MEDIUM, ui.onSurfaceVariant).apply { gravity = Gravity.CENTER })
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
                    openAccessibilitySettings()
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
        listContainer.addView(ui.text(getString(R.string.list_hint), Ui.Type.BODY_SMALL, ui.ink3).apply {
            gravity = Gravity.CENTER; setPadding(dp(16), dp(10), dp(16), dp(10))
        }, LinearLayout.LayoutParams(MATCH, WRAP))
    }

    /** v0.8: 選択（触覚 + 星のポン）。行のタップ・星ボタン・長押しメニューから共通で使う */
    private var popSelectedId: String? = null
    private fun select(m: Macro, from: View) {
        if (store.selectedId == m.id) { Motion.haptic(from, Motion.Haptic.TICK); return }
        Motion.haptic(from, Motion.Haptic.CONFIRM)
        store.selectedId = m.id; popSelectedId = m.id
        refresh(); snack(getString(R.string.toast_selected))
    }

    /**
     * v0.8: 行の長押しメニュー（下から出るアクションシート）。
     * mobbin / 60fps の Bottom Sheet・Action sheet のパターンを、既存の BottomSheet で作り直したもの。
     */
    private fun actionSheet(m: Macro) {
        val sheet = BottomSheet(this)
        // v0.9: 項目が多い / 文字が大きいときはスクロールできるように（R5.10 / R6.9）
        val root = MaxHeightLayout(this, 0.9f).apply { orientation = LinearLayout.VERTICAL; background = ui.sheetBackground() }
        val handle = sheet.handle()
        root.addView(handle, LinearLayout.LayoutParams(dp(96), dp(48)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        val head = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), 0, dp(24), dp(8)) }
        head.addView(ui.text(m.name, Ui.Type.TITLE_LARGE, ui.onSurface, weight = Ui.Weight.SEMIBOLD).apply {
            maxLines = 2
            if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
        })
        head.addView(ui.text(getString(R.string.macro_summary, Texts.steps(this, m.steps.size), Texts.kinds(this, m.steps)),
            Ui.Type.BODY_MEDIUM, ui.onSurfaceVariant).apply { setPadding(0, dp(2), 0, 0) })
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
        item(R.drawable.ic_delete, getString(R.string.delete), danger = true) { deleteWithUndo(m) }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(MATCH, 0, 1f))
        sheet.padForNavigationBar(root)
        sheet.attach(root)
        // v0.9: 閉じたらフォーカスを元の行へ（R6.11）
        sheet.setOnDismissListener { focusRow(m.id) }
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
            tag = rowTag(m.id)
            // v0.9: TalkBack 向け。タップ / 長押しの意味、選択中の状態、各操作をカスタムアクションで（R6.4 / R7.6）
            if (Build.VERSION.SDK_INT >= 30) stateDescription = if (isSelected) getString(R.string.state_selected) else null
            accessibilityDelegate = rowA11y(m)
        }
        val fg = if (isSelected) ui.onPrimaryContainer else ui.onSurface
        val fgVar = if (isSelected) ui.onPrimaryContainer else ui.onSurfaceVariant
        val indent = dp(30)
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(ui.monoText("%02d".format(index + 1), Ui.Type.LABEL_MEDIUM, if (isSelected) ui.onPrimaryContainer else ui.ink3, Ui.Weight.MEDIUM).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(indent, WRAP))
        top.addView(ui.text(m.name, Ui.Type.TITLE_MEDIUM, fg, medium = true).apply { maxLines = 2 }, LinearLayout.LayoutParams(0, WRAP, 1f))
        if (isSelected) top.addView(ui.badge(getString(R.string.selected_badge), Ui.Tone.ACCENT),
            LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(8) })
        card.addView(top)
        card.addView(ui.text(getString(R.string.macro_summary, Texts.steps(this, m.steps.size), Texts.kinds(this, m.steps)), Ui.Type.BODY_MEDIUM, fgVar).apply {
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
        // チップは読み上げ用に1つにまとめる（行の説明に含まれるのでスクロール枠はフォーカスさせない）
        card.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false; isFocusable = false; addView(chips)
        })

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(indent - dp(2), dp(10), 0, 0)
        }
        // v0.9: タッチ範囲はすべて 48dp 以上（R6.5）。名前の変更・削除は「その他」メニューへ（R5.7: 操作アイコンは 2〜3 個、残りはオーバーフロー）
        actions.addView(ui.button(getString(R.string.action_run), Ui.BtnStyle.FILLED, R.drawable.ic_play) { runMacro(m) })
        actions.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        fun ib(icon: Int, desc: String, onClick: () -> Unit) = ui.iconButton(icon, desc, 48, onClick = onClick).apply {
            setImageDrawable(ui.icon(icon, fgVar))
        }
        actions.addView(ib(if (isSelected) R.drawable.ic_star else R.drawable.ic_star_border, getString(R.string.desc_select)) {
            select(m, card)
        }.apply {
            if (isSelected) setImageDrawable(ui.icon(R.drawable.ic_star, ui.primary))
            if (isSelected && popSelectedId == m.id) { popSelectedId = null; post { Motion.pop(this) } }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        actions.addView(ib(R.drawable.ic_tune, getString(R.string.desc_settings)) { editSettings(m) }, LinearLayout.LayoutParams(dp(48), dp(48)))
        actions.addView(ib(R.drawable.ic_more_vert, getString(R.string.desc_more)) { actionSheet(m) }, LinearLayout.LayoutParams(dp(48), dp(48)))
        card.addView(actions)
        return card
    }

    private fun runMacro(m: Macro) {
        val s = MacroAccessibilityService.instance
        if (s == null) {
            snack(getString(R.string.toast_enable_service_first, getString(R.string.app_name)), getString(R.string.action_open)) {
                openAccessibilitySettings()
            }
            return
        }
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
        }.setOnDismissListener { focusRow(m.id) }
        edit.requestFocus()
    }

    /**
     * v0.9: 削除は確認ダイアログを出さずにすぐ行い、「元に戻す」付きのスナックバーを出す（R5.16 / アンチパターン:
     * 取り消せる操作に確認ダイアログ）。元に戻すと選択中の状態も戻す。
     */
    private fun deleteWithUndo(m: Macro) {
        val wasSelected = store.selectedId == m.id
        store.delete(m.id); refresh()
        Motion.haptic(listContainer, Motion.Haptic.CONFIRM)
        snack(getString(R.string.snack_deleted, m.name), getString(R.string.undo)) {
            store.save(m)
            if (wasSelected) store.selectedId = m.id
            refresh(); focusRow(m.id)
        }
    }

    /** v0.6: 再生設定はボトムシート（SettingsSheet） */
    private fun editSettings(m: Macro) {
        SettingsSheet(this, m, onSave = { store.save(it); refresh() }, onDelete = { deleteWithUndo(m) }).apply {
            setOnDismiss { focusRow(m.id) }
        }.show()
    }

    private fun rowTag(id: String) = "macro-row:$id"

    /** v0.9: シート / ダイアログを閉じたあと、TalkBack のフォーカスを元の行へ戻す（R6.11） */
    private fun focusRow(id: String) {
        listContainer.post {
            val v = listContainer.findViewWithTag<View>(rowTag(id)) ?: return@post
            // 入力フォーカスを戻す（TalkBack はウィンドウが閉じたあと入力フォーカスに従う。強制的な a11y フォーカスはしない）
            v.requestFocus()
        }
    }

    /** v0.9: 行のカスタムアクション（実行・設定・名前の変更・削除）とタップ / 長押しの説明 */
    private fun rowA11y(m: Macro) = object : View.AccessibilityDelegate() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            fun add(id: Int, label: String) =
                info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(id, label))
            add(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK, getString(R.string.desc_select))
            add(android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK, getString(R.string.desc_more))
            add(R.id.a11y_action_run, getString(R.string.action_run))
            add(R.id.a11y_action_settings, getString(R.string.desc_settings))
            add(R.id.a11y_action_rename, getString(R.string.desc_rename))
            add(R.id.a11y_action_delete, getString(R.string.delete))
        }

        override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean = when (action) {
            R.id.a11y_action_run -> { runMacro(m); true }
            R.id.a11y_action_settings -> { editSettings(m); true }
            R.id.a11y_action_rename -> { rename(m); true }
            R.id.a11y_action_delete -> { deleteWithUndo(m); true }
            else -> super.performAccessibilityAction(host, action, args)
        }
    }

    companion object {
        const val ACTION_SHOW_PANEL = "jp.ayu.macro.action.SHOW_PANEL"
        const val ACTION_PLAY_SELECTED = "jp.ayu.macro.action.PLAY_SELECTED"
    }
}

private const val MATCH = android.view.ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
