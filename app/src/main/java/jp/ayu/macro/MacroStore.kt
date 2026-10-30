package jp.ayu.macro

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** アプリ専用領域 (files/macros/ 内の JSON) にのみ保存。外部ストレージやネットワークは使わない。 */
class MacroStore(context: Context) {
    private val dir = File(context.filesDir, "macros").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("macro_prefs", Context.MODE_PRIVATE)

    fun list(): List<Macro> = dir.listFiles { f -> f.name.endsWith(".json") }
        ?.mapNotNull { f -> runCatching { Macro.fromJson(JSONObject(f.readText())) }.getOrNull() }
        ?.sortedByDescending { it.createdAt }
        ?: emptyList()

    fun get(id: String): Macro? = File(dir, "$id.json").takeIf { it.exists() }
        ?.let { runCatching { Macro.fromJson(JSONObject(it.readText())) }.getOrNull() }

    fun save(m: Macro) {
        val tmp = File(dir, "${m.id}.json.tmp")
        tmp.writeText(m.toJson().toString())
        tmp.renameTo(File(dir, "${m.id}.json"))
    }

    fun delete(id: String) {
        File(dir, "$id.json").delete()
        if (selectedId == id) selectedId = null
    }

    /** フローティングボタンの「再生」で使うマクロ */
    var selectedId: String?
        get() = prefs.getString("selected", null)
        set(v) { prefs.edit().putString("selected", v).apply() }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}
