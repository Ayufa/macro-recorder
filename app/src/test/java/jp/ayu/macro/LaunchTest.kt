package jp.ayu.macro

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * v0.9.1: アプリを実際に起動するテスト（v0.9 は起動直後に落ちていた）。
 * ライト / ダーク × 全言語、主要な Android バージョン、壊れた保存値、ショートカット、
 * すべてのダイアログ / シート / スナックバー、クラッシュログのカードを確認する。
 */
@RunWith(RobolectricTestRunner::class)
class LaunchTest {
    private val locales = listOf("en", "ja", "zh-rCN", "zh-rTW", "ko", "es")
    private val ctx: Context get() = RuntimeEnvironment.getApplication()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun <T> withActivity(intent: Intent? = null, block: (MainActivity) -> T): T {
        val c = if (intent == null) Robolectric.buildActivity(MainActivity::class.java)
        else Robolectric.buildActivity(MainActivity::class.java, intent)
        c.setup()
        try {
            val a = c.get()
            a.window.decorView.apply { measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY)); layout(0, 0, 1080, 2400) }
            idle()
            return block(a)
        } finally {
            c.pause().stop().destroy()
        }
    }

    private fun launchAllConfigs() {
        for (night in listOf("notnight", "night")) for (l in locales) {
            RuntimeEnvironment.setQualifiers("$l-$night")
            withActivity { a -> assertTrue("$l-$night", a.window.decorView.isLaidOut) }
        }
    }

    @Test @Config(sdk = [26]) fun launch26() = launchAllConfigs()
    @Test @Config(sdk = [30]) fun launch30() = launchAllConfigs()
    @Test @Config(sdk = [34]) fun launch34() = launchAllConfigs()
    @Test @Config(sdk = [35]) fun launch35() = launchAllConfigs()
    @Test @Config(sdk = [36]) fun launch36() = launchAllConfigs()
    @Test @Config(sdk = [37]) fun launch37() = launchAllConfigs()

    /** 回転・テーマ変更などで作り直されても落ちない */
    @Test @Config(sdk = [37]) fun recreate() {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup()
        c.recreate(); idle()
        c.configurationChange(Configuration(c.get().resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        }); idle()
        c.pause().stop().destroy()
    }

    /** 壊れた・型の違う保存値（v0.8 からの更新など）でも起動する */
    private fun badPrefs() {
        ctx.getSharedPreferences("app_theme", Context.MODE_PRIVATE).edit().clear().putString("mode", "dark").commit()
        ctx.getSharedPreferences("app_locale", Context.MODE_PRIVATE).edit().clear().putInt("tag", 5).commit()
        withActivity { }
        ctx.getSharedPreferences("app_theme", Context.MODE_PRIVATE).edit().clear().putInt("mode", 99).commit()
        ctx.getSharedPreferences("app_locale", Context.MODE_PRIVATE).edit().clear().putString("tag", "xx-??").commit()
        withActivity { }
        ctx.getSharedPreferences("app_theme", Context.MODE_PRIVATE).edit().clear().putInt("mode", ThemeMode.DARK).commit()
        ctx.getSharedPreferences("app_locale", Context.MODE_PRIVATE).edit().clear().putString("tag", "ja").commit()
        withActivity { }
    }
    @Test @Config(sdk = [26]) fun badPrefs26() = badPrefs()
    @Test @Config(sdk = [30]) fun badPrefs30() = badPrefs()
    @Test @Config(sdk = [37]) fun badPrefs37() = badPrefs()

    /** ランチャーのショートカット（サービスがオフ → スナックバー） */
    @Test @Config(sdk = [37]) fun shortcuts() {
        for (action in listOf(MainActivity.ACTION_SHOW_PANEL, MainActivity.ACTION_PLAY_SELECTED)) {
            withActivity(Intent(action).setClassName(ctx, MainActivity::class.java.name)) { }
        }
    }

    private fun sample(id: String) = Macro(
        id = id, name = "テスト $id", createdAt = 1L, screenWidth = 1080, screenHeight = 2340,
        steps = listOf(
            Step(300, listOf(Stroke(0, listOf(TouchPoint(10f, 20f, 0), TouchPoint(11f, 20f, 80))))),
            Step(1200, listOf(Stroke(0, listOf(TouchPoint(500f, 1500f, 0), TouchPoint(500f, 500f, 300))))),
        ),
    )

    private fun call(a: Activity, name: String, vararg args: Any) {
        val m = a.javaClass.declaredMethods.first { it.name == name && it.parameterCount == args.size }
        m.isAccessible = true
        m.invoke(a, *args)
        idle()
    }

    private fun dismissAll() {
        ShadowDialog.getShownDialogs().forEach { runCatching { it.dismiss() } }
        idle()
    }

    /** すべてのダイアログ / シートを開き、スナックバーも出す */
    private fun allDialogs() {
        val store = MacroStore(ctx)
        store.list().forEach { store.delete(it.id) }
        val m = sample("m1"); store.save(m); store.save(sample("m2"))
        for (night in listOf("notnight", "night")) for (l in locales) {
            RuntimeEnvironment.setQualifiers("$l-$night")
            ShadowDialog.reset()
            withActivity { a ->
                for (name in listOf("pickTheme", "pickLanguage", "openAccessibilitySettings")) {
                    call(a, name)
                    assertNotNull("$name $l-$night", ShadowDialog.getLatestDialog())
                    dismissAll()
                }
                for (name in listOf("rename", "actionSheet", "editSettings")) {
                    call(a, name, m)
                    assertNotNull("$name $l-$night", ShadowDialog.getLatestDialog())
                    dismissAll()
                }
                val snack = a.javaClass.declaredMethods.first { it.name == "snack" && it.parameterCount == 3 }
                snack.isAccessible = true
                snack.invoke(a, "テスト", "OK", { })
                idle()
                snack.invoke(a, "テスト", null, null)
                idle()
            }
        }
        store.list().forEach { store.delete(it.id) }
    }
    @Test @Config(sdk = [30]) fun dialogs30() = allDialogs()
    @Test @Config(sdk = [37]) fun dialogs37() = allDialogs()

    private fun findText(v: View, s: String): TextView? {
        if (v is TextView && v.text?.toString() == s) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findText(v.getChildAt(i), s)?.let { return it }
        return null
    }

    /** クラッシュログ: 記録 → 次の起動でカード → コピー → 消去 */
    @Test @Config(sdk = [37]) fun crashLogCard() {
        CrashLog.clear(ctx)
        CrashLog.write(ctx, Thread.currentThread(), IllegalStateException("boom"))
        assertTrue(CrashLog.read(ctx)!!.contains("boom"))
        withActivity { a ->
            val title = findText(a.window.decorView, a.getString(R.string.crash_title))
            assertNotNull(title)
            findText(a.window.decorView, a.getString(R.string.crash_copy))!!.performClick(); idle()
            val clip = a.getSystemService(android.content.ClipboardManager::class.java).primaryClip
            assertTrue(clip!!.getItemAt(0).text.contains("boom"))
            findText(a.window.decorView, a.getString(R.string.crash_clear))!!.performClick(); idle()
        }
        assertNull(CrashLog.read(ctx))
        withActivity { a -> assertNull(findText(a.window.decorView, a.getString(R.string.crash_title))) }
    }

    /** 「太字テキスト」設定（fontWeightAdjustment）が大きくても書体を作れる */
    @Test @Config(sdk = [37]) fun boldTextFonts() {
        for (adj in listOf(0, 300, 500, 1000, -1000)) {
            val c = ctx.createConfigurationContext(Configuration(ctx.resources.configuration).apply { fontWeightAdjustment = adj })
            val ui = Ui(c)
            for (w in Ui.Weight.values()) { ui.font(w); ui.mono(w) }
        }
    }
}
