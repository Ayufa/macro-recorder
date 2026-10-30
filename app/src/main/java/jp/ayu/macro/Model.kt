package jp.ayu.macro

import org.json.JSONArray
import org.json.JSONObject

/** 1本の指の軌跡。t は stroke 開始からの経過ミリ秒。 */
data class TouchPoint(val x: Float, val y: Float, val t: Long)

data class Stroke(
    /** ジェスチャー開始からこのストロークが始まるまで (ms) */
    val startOffsetMs: Long,
    val points: List<TouchPoint>,
) {
    val durationMs: Long get() = (points.lastOrNull()?.t ?: 0L).coerceAtLeast(1L)
}

/** 1つの操作ステップ（タップ・長押し・スワイプ・複数指）。 */
data class Step(
    /** 前のステップが終わってからこのステップを始めるまでの待ち時間 (ms) */
    val delayMs: Long,
    val strokes: List<Stroke>,
) {
    val totalDurationMs: Long get() = strokes.maxOfOrNull { it.startOffsetMs + it.durationMs } ?: 1L

    enum class Kind { TAP, LONG_PRESS, SWIPE, MULTI, EMPTY }

    /** 操作の種類（表示名は Texts.kinds で strings.xml から） */
    fun kind(): Kind {
        if (strokes.size > 1) return Kind.MULTI
        val s = strokes.firstOrNull() ?: return Kind.EMPTY
        val dist = pathLength(s.points)
        return when {
            dist < MOVE_TOLERANCE_PX && s.durationMs >= LONG_PRESS_MS -> Kind.LONG_PRESS
            dist < MOVE_TOLERANCE_PX -> Kind.TAP
            else -> Kind.SWIPE
        }
    }

    companion object {
        const val MOVE_TOLERANCE_PX = 24f
        const val LONG_PRESS_MS = 500L
        fun pathLength(p: List<TouchPoint>): Float {
            var d = 0f
            for (i in 1 until p.size) {
                val dx = p[i].x - p[i - 1].x
                val dy = p[i].y - p[i - 1].y
                d += kotlin.math.sqrt(dx * dx + dy * dy)
            }
            return d
        }
    }
}

data class Macro(
    val id: String,
    val name: String,
    val createdAt: Long,
    val screenWidth: Int,
    val screenHeight: Int,
    val steps: List<Step>,
    /** 繰り返し回数。0 = 無限 */
    val repeatCount: Int = 1,
    /** ループ間の待ち時間 (ms) */
    val loopIntervalMs: Long = 1000L,
    /** 再生速度倍率 (0.5〜4.0) */
    val speed: Float = 1.0f,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("version", 1)
        put("id", id)
        put("name", name)
        put("createdAt", createdAt)
        put("screenWidth", screenWidth)
        put("screenHeight", screenHeight)
        put("repeatCount", repeatCount)
        put("loopIntervalMs", loopIntervalMs)
        put("speed", speed.toDouble())
        put("steps", JSONArray().apply {
            steps.forEach { st ->
                put(JSONObject().apply {
                    put("delayMs", st.delayMs)
                    put("strokes", JSONArray().apply {
                        st.strokes.forEach { sk ->
                            put(JSONObject().apply {
                                put("start", sk.startOffsetMs)
                                // [x, y, t, x, y, t, ...] でコンパクトに保存
                                put("p", JSONArray().apply {
                                    sk.points.forEach { pt ->
                                        put(pt.x.toDouble()); put(pt.y.toDouble()); put(pt.t)
                                    }
                                })
                            })
                        }
                    })
                })
            }
        })
    }

    companion object {
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 4.0f

        fun fromJson(o: JSONObject): Macro {
            val stepsArr = o.getJSONArray("steps")
            val steps = (0 until stepsArr.length()).map { i ->
                val so = stepsArr.getJSONObject(i)
                val ska = so.getJSONArray("strokes")
                Step(
                    delayMs = so.getLong("delayMs"),
                    strokes = (0 until ska.length()).map { j ->
                        val sk = ska.getJSONObject(j)
                        val p = sk.getJSONArray("p")
                        Stroke(
                            startOffsetMs = sk.getLong("start"),
                            points = (0 until p.length() / 3).map { k ->
                                TouchPoint(
                                    p.getDouble(k * 3).toFloat(),
                                    p.getDouble(k * 3 + 1).toFloat(),
                                    p.getLong(k * 3 + 2),
                                )
                            },
                        )
                    },
                )
            }
            return Macro(
                id = o.getString("id"),
                name = o.getString("name"),
                createdAt = o.optLong("createdAt"),
                screenWidth = o.optInt("screenWidth"),
                screenHeight = o.optInt("screenHeight"),
                steps = steps,
                repeatCount = o.optInt("repeatCount", 1).coerceAtLeast(0),
                loopIntervalMs = o.optLong("loopIntervalMs", 1000L).coerceAtLeast(0L),
                speed = o.optDouble("speed", 1.0).toFloat().coerceIn(MIN_SPEED, MAX_SPEED),
            )
        }
    }
}

/** 速度倍率と画面サイズ差を考慮した再生用の変換。純粋関数なので単体テスト可能。 */
object ReplayMath {
    fun scaleTime(ms: Long, speed: Float): Long =
        (ms / speed.coerceIn(Macro.MIN_SPEED, Macro.MAX_SPEED)).toLong()

    /** 録画時と現在の画面サイズが違う場合（回転・解像度変更）に座標を比例変換 */
    fun scalePoint(x: Float, y: Float, recW: Int, recH: Int, curW: Int, curH: Int): Pair<Float, Float> {
        if (recW <= 0 || recH <= 0 || (recW == curW && recH == curH)) return x to y
        val sx = (x * curW / recW).coerceIn(0f, (curW - 1).toFloat())
        val sy = (y * curH / recH).coerceIn(0f, (curH - 1).toFloat())
        return sx to sy
    }

    /** 点列を間引く（保存サイズ削減と dispatchGesture の安定化）。最初と最後の点は必ず残す。 */
    fun simplify(points: List<TouchPoint>, minDistPx: Float = 4f): List<TouchPoint> {
        if (points.size <= 2) return points
        val out = ArrayList<TouchPoint>()
        out.add(points.first())
        for (i in 1 until points.size - 1) {
            val last = out.last()
            val dx = points[i].x - last.x
            val dy = points[i].y - last.y
            if (dx * dx + dy * dy >= minDistPx * minDistPx) out.add(points[i])
        }
        out.add(points.last())
        return out
    }
}

/** フローティングパネルの寸法計算（テスト可能な純粋関数） */
object PanelMath {
    /**
     * 画面幅 (dp) に応じたボタンの幅 (dp)。狭い画面ではボタンを小さくしてはみ出しを防ぐ。
     * v0.9: 縦のタッチ領域は常に 48dp（パネルの高さ）。幅 412dp 以上の画面では横も 48dp（R6.5）。
     * それより狭い画面では、記録中に 6 個のボタン＋状態表示が 1 行に収まらなくなるので 40dp 以下のまま。
     */
    fun buttonSizeDp(screenWidthDp: Int): Int = when {
        screenWidthDp >= 412 -> 48
        screenWidthDp >= 360 -> 40
        screenWidthDp >= 320 -> 36
        else -> 32
    }

    /** パネルが画面内に収まるように位置を補正する */
    fun clamp(pos: Int, size: Int, screen: Int): Int = pos.coerceIn(0, (screen - size).coerceAtLeast(0))

    /**
     * バブル (x, 幅 bubbleW) をピル (幅 pillW) に広げる向き。
     * 右に収まるなら右へ。収まらず、左側のほうが広いときだけ左へ（バブルの右端を基準にする）。
     */
    fun expandLeftward(x: Int, bubbleW: Int, pillW: Int, screen: Int): Boolean =
        x + pillW > screen && (x + bubbleW) > (screen - x)

    /** 展開後のウィンドウの x。左へ広げるときはバブルの右端をそろえる */
    fun expandedX(x: Int, bubbleW: Int, pillW: Int, leftward: Boolean): Int =
        if (leftward) (x + bubbleW - pillW).coerceAtLeast(0) else x

    /** 閉じたあとのバブルの x。左へ広げていたときは右端（元のバブルの位置）に戻す */
    fun collapsedX(x: Int, bubbleW: Int, pillW: Int, leftward: Boolean): Int =
        if (leftward) x + (pillW - bubbleW) else x
}
