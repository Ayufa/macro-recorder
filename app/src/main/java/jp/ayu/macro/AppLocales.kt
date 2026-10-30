package jp.ayu.macro

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * アプリの言語（AppCompat なし）。
 * - Android 13+ : LocaleManager（システムの「アプリの言語」設定と同じもの）
 * - Android 12 以下: 選んだ言語を保存し、Activity / サービスの Context を差し替えて反映
 * 空文字 = システムの言語に従う
 */
object AppLocales {
    /** 対応言語（タグ）。表示名は strings.xml の lang_* */
    val SUPPORTED = listOf("", "en", "ja", "zh-CN", "zh-TW", "ko", "es")

    private const val PREFS = "app_locale"
    private const val KEY = "tag"

    fun nameRes(tag: String): Int = when (tag) {
        "en" -> R.string.lang_en
        "ja" -> R.string.lang_ja
        "zh-CN" -> R.string.lang_zh_cn
        "zh-TW" -> R.string.lang_zh_tw
        "ko" -> R.string.lang_ko
        "es" -> R.string.lang_es
        else -> R.string.lang_system
    }

    fun current(ctx: Context): String = runCatching { currentRaw(ctx) }.getOrDefault("").let { if (it in SUPPORTED) it else "" }

    private fun currentRaw(ctx: Context): String {
        if (Build.VERSION.SDK_INT >= 33) {
            val l = ctx.getSystemService(LocaleManager::class.java)?.applicationLocales ?: return ""
            if (l.isEmpty) return ""
            return match(l[0])
        }
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
    }

    /** "zh-Hant-TW" などを対応タグにまとめる */
    private fun match(l: Locale): String = when (l.language) {
        "zh" -> if (l.script == "Hant" || l.country in setOf("TW", "HK", "MO")) "zh-TW" else "zh-CN"
        "en", "ja", "ko", "es" -> l.language
        else -> l.toLanguageTag()
    }

    fun set(activity: Activity, tag: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            activity.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
            // システムが Activity を作り直す
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
            MacroAccessibilityService.instance?.onAppLocaleChanged()
            activity.recreate()
        }
    }

    /**
     * Android 12 以下: 保存した言語で Context を包む（13+ では何もしない）。
     * v0.9: Android 11 以下では、選んだテーマ（ライト/ダーク）もここで反映する（12+ は UiModeManager）。
     */
    // v0.9.1: 保存値が壊れていても起動を止めない（読めなければシステムの設定に従う）
    fun wrap(base: Context): Context = runCatching { wrapRaw(base) }.getOrDefault(base)

    private fun wrapRaw(base: Context): Context {
        val tag = if (Build.VERSION.SDK_INT >= 33) "" else
            (runCatching { base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") }.getOrNull() ?: "").takeIf { it in SUPPORTED } ?: ""
        val night = if (Build.VERSION.SDK_INT >= 31) ThemeMode.SYSTEM else ThemeMode.current(base)
        if (tag.isEmpty() && night == ThemeMode.SYSTEM) return base
        val config = Configuration(base.resources.configuration)
        if (tag.isNotEmpty()) config.setLocales(LocaleList(Locale.forLanguageTag(tag)))
        if (night != ThemeMode.SYSTEM) {
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                (if (night == ThemeMode.DARK) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        }
        return base.createConfigurationContext(config)
    }
}

/**
 * v0.9: テーマ（システムに従う / ライト / ダーク）を手動で選べるように（android-design-guidelines R1.9）。
 * - Android 12+ : UiModeManager.setApplicationNightMode（システムがアプリの設定として覚え、Activity を作り直す）
 * - Android 11 以下: 保存して AppLocales.wrap で Context の uiMode を差し替える
 */
object ThemeMode {
    const val SYSTEM = 0
    const val LIGHT = 1
    const val DARK = 2
    val ALL = listOf(SYSTEM, LIGHT, DARK)
    private const val PREFS = "app_theme"
    private const val KEY = "mode"

    fun nameRes(mode: Int): Int = when (mode) {
        LIGHT -> R.string.theme_light
        DARK -> R.string.theme_dark
        else -> R.string.theme_system
    }

    /** 選んだテーマ（12+ でも表示用に保存しておく） */
    fun current(ctx: Context): Int = runCatching {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, SYSTEM)
    }.getOrDefault(SYSTEM).takeIf { it in ALL } ?: SYSTEM  // v0.9.1: 壊れた値・型違いはシステムに従う

    fun set(activity: Activity, mode: Int) {
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, mode).apply()
        if (Build.VERSION.SDK_INT >= 31) {
            runCatching {
                activity.getSystemService(android.app.UiModeManager::class.java)?.setApplicationNightMode(when (mode) {
                    LIGHT -> android.app.UiModeManager.MODE_NIGHT_NO
                    DARK -> android.app.UiModeManager.MODE_NIGHT_YES
                    else -> android.app.UiModeManager.MODE_NIGHT_AUTO
                })
            }
            // システムが構成の変更として Activity を作り直す（サービスのパネルは onConfigurationChanged で作り直す）
        } else {
            MacroAccessibilityService.instance?.onAppLocaleChanged()
            activity.recreate()
        }
    }
}
