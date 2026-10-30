package jp.ayu.macro

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var store: MacroStore
    private lateinit var statusView: TextView
    private lateinit var listContainer: LinearLayout
    private lateinit var serviceButton: Button
    private lateinit var panelButton: Button
    private lateinit var batteryButton: Button

    private fun dp(v: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = MacroStore(this)
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = true
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        scroll.addView(root)
        // edge-to-edge (targetSdk 35+ では強制) に対応: システムバー分の余白を付ける
        scroll.setOnApplyWindowInsetsListener { v, insets ->
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
        setContentView(scroll)

        root.addView(TextView(this).apply {
            text = getString(R.string.app_name); textSize = 24f; setTypeface(typeface, Typeface.BOLD)
        })
        statusView = TextView(this).apply { textSize = 14f; setPadding(0, dp(8), 0, dp(8)) }
        root.addView(statusView)

        serviceButton = button("ユーザー補助の設定を開く") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        root.addView(serviceButton)
        root.addView(button("アプリ情報を開く（制限付き設定の許可など）") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })
        batteryButton = button("電池の最適化から除外する") { requestIgnoreBattery() }
        root.addView(batteryButton)
        panelButton = button("フローティングボタンを表示") {
            val s = MacroAccessibilityService.instance
            if (s == null) toast("先にユーザー補助で「操作マクロ」をオンにしてください") else { s.showPanel(); refresh() }
        }
        root.addView(panelButton)

        root.addView(TextView(this).apply {
            text = "使い方: フローティングボタンの「● 記録」→ 操作 →「■ 記録終了」で保存。「▶ 再生」で選択中（★）のマクロを再生、「■ 停止」で止まります。"
            textSize = 13f; setPadding(0, dp(12), 0, dp(4)); setTextColor(Color.DKGRAY)
        })
        root.addView(TextView(this).apply {
            text = "保存したマクロ"; textSize = 18f; setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(16), 0, dp(8))
        })
        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listContainer)
        root.addView(TextView(this).apply {
            text = "プライバシー: 記録するのは指の位置と時間だけで、他のアプリの画面内容は読み取りません。データはこの端末内にのみ保存され、インターネット権限はありません。"
            textSize = 12f; setTextColor(Color.GRAY); setPadding(0, dp(24), 0, 0)
        })
    }

    override fun onResume() {
        super.onResume()
        MacroAccessibilityService.onChanged = { refresh() }
        refresh()
    }

    override fun onPause() {
        MacroAccessibilityService.onChanged = null
        super.onPause()
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; setOnClickListener { onClick() }
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

    @SuppressLint("SetTextI18n")
    private fun refresh() {
        val running = MacroAccessibilityService.instance != null
        val enabled = isServiceEnabled()
        statusView.text = buildString {
            append("ユーザー補助サービス: ")
            append(when {
                running -> "✅ 動作中"
                enabled -> "⚠ オンですが応答がありません（一度オフ→オンにしてください）"
                else -> "❌ オフ（下のボタンから「操作マクロ」をオンにしてください）"
            })
            append("\n電池の最適化: ")
            append(if (isIgnoringBattery()) "✅ 除外済み" else "⚠ 最適化中（停止されることがあります）")
        }
        serviceButton.visibility = if (running) View.GONE else View.VISIBLE
        panelButton.visibility = if (running && MacroAccessibilityService.instance?.isPanelShown == false) View.VISIBLE else View.GONE
        batteryButton.visibility = if (isIgnoringBattery()) View.GONE else View.VISIBLE

        listContainer.removeAllViews()
        val macros = store.list()
        if (macros.isEmpty()) {
            listContainer.addView(TextView(this).apply {
                text = "まだマクロがありません。フローティングボタンの「● 記録」から作成できます。"
                setTextColor(Color.GRAY)
            })
        }
        val selected = store.selectedId
        for (m in macros) listContainer.addView(row(m, m.id == selected))
    }

    @SuppressLint("SetTextI18n")
    private fun row(m: Macro, isSelected: Boolean): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(if (isSelected) Color.rgb(232, 234, 246) else Color.rgb(245, 245, 245))
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = dp(8) }
        }
        card.addView(TextView(this).apply {
            text = (if (isSelected) "★ " else "") + m.name; textSize = 16f; setTypeface(typeface, Typeface.BOLD)
        })
        val kinds = m.steps.groupingBy { it.kindLabel() }.eachCount().entries.joinToString("・") { "${it.key}${it.value}" }
        val rep = if (m.repeatCount == 0) "無限" else "${m.repeatCount}回"
        card.addView(TextView(this).apply {
            text = "${m.steps.size}操作（$kinds）\n繰り返し: $rep ／ 間隔: ${m.loopIntervalMs / 1000.0}秒 ／ 速度: ${m.speed}倍"
            textSize = 12f; setTextColor(Color.DKGRAY)
        })
        val btns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        fun small(label: String, onClick: () -> Unit) = Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            text = label; isAllCaps = false; textSize = 13f; minWidth = 0; minimumWidth = 0
            setPadding(dp(8), 0, dp(8), 0); setOnClickListener { onClick() }
        }
        btns.addView(small("実行") { runMacro(m) })
        btns.addView(small("★選択") { store.selectedId = m.id; refresh(); toast("フローティングの▶で再生されます") })
        btns.addView(small("設定") { editSettings(m) })
        btns.addView(small("名前") { rename(m) })
        btns.addView(small("削除") { confirmDelete(m) })
        card.addView(btns)
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
        val infinite = CheckBox(this).apply { text = "無限に繰り返す（■ 停止で止める）"; isChecked = m.repeatCount == 0 }
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
