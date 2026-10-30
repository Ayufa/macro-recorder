package jp.ayu.macro

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelTest {
    private fun sample() = Macro(
        id = "abc", name = "テスト", createdAt = 1L, screenWidth = 1080, screenHeight = 2340,
        steps = listOf(
            Step(300, listOf(Stroke(0, listOf(TouchPoint(10f, 20f, 0), TouchPoint(11f, 20f, 80))))),
            Step(1200, listOf(Stroke(0, listOf(TouchPoint(500f, 1500f, 0), TouchPoint(500f, 500f, 300))))),
            Step(500, listOf(Stroke(0, listOf(TouchPoint(100f, 100f, 0), TouchPoint(100f, 100f, 900))))),
        ),
        repeatCount = 0, loopIntervalMs = 2500, speed = 2f,
    )

    @Test fun jsonRoundTrip() {
        val m = sample()
        assertEquals(m, Macro.fromJson(org.json.JSONObject(m.toJson().toString())))
    }

    @Test fun kinds() {
        val m = sample()
        assertEquals(Step.Kind.TAP, m.steps[0].kind())
        assertEquals(Step.Kind.SWIPE, m.steps[1].kind())
        assertEquals(Step.Kind.LONG_PRESS, m.steps[2].kind())
    }

    @Test fun speedScaling() {
        assertEquals(500L, ReplayMath.scaleTime(1000, 2f))
        assertEquals(2000L, ReplayMath.scaleTime(1000, 0.5f))
        assertEquals(250L, ReplayMath.scaleTime(1000, 10f)) // 4倍に制限
    }

    @Test fun pointScaling() {
        assertEquals(540f to 1170f, ReplayMath.scalePoint(540f, 1170f, 1080, 2340, 1080, 2340))
        assertEquals(720f to 1560f, ReplayMath.scalePoint(540f, 1170f, 1080, 2340, 1440, 3120))
    }

    @Test fun simplifyKeepsEnds() {
        val pts = (0..100).map { TouchPoint(it * 0.5f, 0f, it.toLong()) }
        val s = ReplayMath.simplify(pts)
        assertEquals(pts.first(), s.first()); assertEquals(pts.last(), s.last())
        assert(s.size < pts.size)
    }
}

class PanelMathTest {
    @org.junit.Test fun buttonSizeShrinksOnNarrowScreens() {
        org.junit.Assert.assertEquals(40, PanelMath.buttonSizeDp(412))
        org.junit.Assert.assertEquals(36, PanelMath.buttonSizeDp(340))
        org.junit.Assert.assertEquals(32, PanelMath.buttonSizeDp(280))
    }
    @org.junit.Test fun clampKeepsPanelOnScreen() {
        org.junit.Assert.assertEquals(780, PanelMath.clamp(1000, 300, 1080))
        org.junit.Assert.assertEquals(0, PanelMath.clamp(-50, 300, 1080))
        org.junit.Assert.assertEquals(0, PanelMath.clamp(10, 1200, 1080))
    }
    @org.junit.Test fun expandDirectionAndPositions() {
        // 画面左寄り: 右へ広がり、位置はそのまま
        org.junit.Assert.assertFalse(PanelMath.expandLeftward(40, 64, 500, 1080))
        org.junit.Assert.assertEquals(40, PanelMath.expandedX(40, 64, 500, false))
        // 画面右端: 左へ広がり、バブルの右端をそろえる。閉じると元の位置に戻る
        org.junit.Assert.assertTrue(PanelMath.expandLeftward(1000, 64, 500, 1080))
        val ex = PanelMath.expandedX(1000, 64, 500, true)
        org.junit.Assert.assertEquals(564, ex)
        org.junit.Assert.assertEquals(1000, PanelMath.collapsedX(ex, 64, 500, true))
    }
}

class FreeTierTest {
    @org.junit.Test fun freeTierLimitsSavedMacros() {
        val lim = jp.ayu.macro.monetization.FreeTier.FREE_MACRO_LIMIT
        org.junit.Assert.assertEquals(3, lim)
        org.junit.Assert.assertTrue(jp.ayu.macro.monetization.FreeTier.canSaveNew(2, lim))
        org.junit.Assert.assertFalse(jp.ayu.macro.monetization.FreeTier.canSaveNew(3, lim))
        // 上限なし（standard / 購入済み）
        org.junit.Assert.assertTrue(jp.ayu.macro.monetization.FreeTier.canSaveNew(1000, null))
    }
}
