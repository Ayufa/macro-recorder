package jp.ayu.macro

import android.accessibilityservice.GestureDescription
import android.graphics.Path

object GestureFactory {
    /** 記録したステップを GestureDescription に変換。speed で時間を短縮/延長、画面サイズ差は比例変換。 */
    fun build(step: Step, speed: Float, recW: Int, recH: Int, curW: Int, curH: Int): GestureDescription? {
        val maxStrokes = GestureDescription.getMaxStrokeCount()
        val maxDuration = GestureDescription.getMaxGestureDuration()
        val builder = GestureDescription.Builder()
        var count = 0
        for (s in step.strokes) {
            if (count >= maxStrokes || s.points.isEmpty()) continue
            val path = Path()
            s.points.forEachIndexed { i, p ->
                val (x, y) = ReplayMath.scalePoint(p.x, p.y, recW, recH, curW, curH)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val start = ReplayMath.scaleTime(s.startOffsetMs, speed).coerceIn(0L, maxDuration - 1)
            val dur = ReplayMath.scaleTime(s.durationMs, speed).coerceIn(1L, maxDuration - start)
            builder.addStroke(GestureDescription.StrokeDescription(path, start, dur))
            count++
        }
        return if (count == 0) null else builder.build()
    }
}
