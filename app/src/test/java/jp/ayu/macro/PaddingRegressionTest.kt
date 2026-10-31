package jp.ayu.macro

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v0.10.1: padding を background より先に設定すると、背景 Drawable の padding に
 * 上書きされて 0 になる不具合（実機で「表示」「実行」ボタンの文字が縁に張り付いた）の回帰テスト。
 * Ui の部品は padding を background の後に設定すること。
 */
@RunWith(RobolectricTestRunner::class)
class PaddingRegressionTest {
    @Test @Config(sdk = [35], qualifiers = "ja-night")
    fun componentPaddingsSurviveBackground() {
        val c = Robolectric.buildActivity(MainActivity::class.java); c.setup()
        val a = c.get()
        val ui = Ui(a)

        val filled = ui.button("表示", Ui.BtnStyle.FILLED) {}
        assertEquals("button FILLED padL", ui.dp(24), filled.paddingLeft)
        assertEquals("button FILLED padR", ui.dp(24), filled.paddingRight)

        val withIcon = ui.button("実行", Ui.BtnStyle.FILLED, R.drawable.ic_play) {}
        assertEquals("button icon padL", ui.dp(20), withIcon.paddingLeft)
        assertEquals("button icon padR", ui.dp(24), withIcon.paddingRight)

        val textBtn = ui.button("OK", Ui.BtnStyle.TEXT) {}
        assertEquals("button TEXT padL", ui.dp(20), textBtn.paddingLeft)
        assertEquals("button TEXT padR", ui.dp(20), textBtn.paddingRight)

        val chip = ui.chip("1倍速")
        assertEquals("chip padL", ui.dp(12), chip.paddingLeft)
        assertEquals("chip padT", ui.dp(6), chip.paddingTop)

        val kbd = ui.kbd("1.0")
        assertEquals("kbd padL", ui.dp(12), kbd.paddingLeft)
        assertEquals("kbd padT", ui.dp(6), kbd.paddingTop)

        val field = ui.field("")
        assertEquals("field padL", ui.dp(16), field.paddingLeft)
        assertEquals("field padT", ui.dp(12), field.paddingTop)

        val filterChip = ui.FilterChip("x") {}
        assertEquals("filterChip padL", ui.dp(16), filterChip.view.paddingLeft)
        assertEquals("filterChip padR", ui.dp(16), filterChip.view.paddingRight)

        c.pause().stop().destroy()
    }
}
