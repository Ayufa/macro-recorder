package jp.ayu.macro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** すべての言語に同じ文言キーがあり、%1$s などの差し込みが一致していることを確認 */
class StringsResourcesTest {
    private val langs = listOf("", "-ja", "-zh-rCN", "-zh-rTW", "-ko", "-es")
    private val ph = Regex("%(\\d\\$)?[sd]")

    private fun load(dir: String): Map<String, List<String>> {
        val f = File("src/$dir/strings.xml")
        assertTrue("missing $f", f.exists())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
        val out = HashMap<String, List<String>>()
        val strings = doc.getElementsByTagName("string")
        for (i in 0 until strings.length) {
            val e = strings.item(i) as org.w3c.dom.Element
            if (e.getAttribute("translatable") == "false") continue
            out[e.getAttribute("name")] = ph.findAll(e.textContent).map { it.value }.sorted().toList()
        }
        val plurals = doc.getElementsByTagName("plurals")
        for (i in 0 until plurals.length) {
            val e = plurals.item(i) as org.w3c.dom.Element
            val items = e.getElementsByTagName("item")
            val other = (0 until items.length).map { items.item(it) as org.w3c.dom.Element }
                .first { it.getAttribute("quantity") == "other" }
            out["plurals/" + e.getAttribute("name")] = ph.findAll(other.textContent).map { it.value }.sorted().toList()
        }
        return out
    }

    @Test fun mainLocalesComplete() = compare("main/res/values")
    @Test fun monetizedLocalesComplete() = compare("monetized/res/values")

    private fun compare(base: String) {
        val en = load(base)
        assertTrue(en.isNotEmpty())
        for (l in langs.drop(1)) {
            val other = load(base + l)
            assertEquals("keys in $base$l", en.keys, other.keys)
            for ((k, v) in en) assertEquals("placeholders of $k in $base$l", v, other[k])
        }
    }
}
