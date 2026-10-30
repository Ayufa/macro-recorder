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

    fun current(ctx: Context): String {
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

    /** Android 12 以下: 保存した言語で Context を包む（13+ では何もしない） */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        if (tag.isEmpty()) return base
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(Locale.forLanguageTag(tag)))
        return base.createConfigurationContext(config)
    }
}
