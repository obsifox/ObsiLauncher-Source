package studio.obsifox.launcher.core.launch

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Vanilla's client logging config prints log4j XML events to stdout (the official launcher parses them).
 * This turns them back into "[12:34:56] [Render thread/INFO]: message" lines; anything else passes through unchanged.
 */
class Log4jXmlFormatter {
    private val header = Regex("""<log4j:Event logger="([^"]*)" timestamp="(\d+)" level="(\w+)" thread="([^"]*)"[^>]*>""")
    private val message = Regex("""<log4j:Message><!\[CDATA\[(.*?)]]></log4j:Message>""", RegexOption.DOT_MATCHES_ALL)
    private val throwable = Regex("""<log4j:Throwable><!\[CDATA\[(.*?)]]></log4j:Throwable>""", RegexOption.DOT_MATCHES_ALL)
    private val time = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

    private var meta: MatchResult? = null
    private val body = StringBuilder()

    /** Feed one raw output line; returns the display lines it completes (possibly none while an event is still open). */
    fun feed(line: String): List<String> {
        if (meta == null) {
            val m = header.find(line) ?: return listOf(line)
            meta = m
            body.setLength(0)
            return consume(line.substring(m.range.last + 1))
        }
        return consume(line)
    }

    private fun consume(text: String): List<String> {
        body.append(text).append('\n')
        if (!text.contains("</log4j:Event>")) return emptyList()
        val m = meta!!
        meta = null
        val b = body.toString()
        val msg = message.find(b)?.groupValues?.get(1) ?: ""
        val thr = throwable.find(b)?.groupValues?.get(1)
        val ts = time.format(Instant.ofEpochMilli(m.groupValues[2].toLong()))
        val prefix = "[$ts] [${m.groupValues[4]}/${m.groupValues[3]}]: "
        val out = ArrayList<String>()
        msg.lines().forEachIndexed { i, l -> out += if (i == 0) prefix + l else l }
        thr?.lines()?.forEach { if (it.isNotBlank()) out += it }
        return out
    }
}
