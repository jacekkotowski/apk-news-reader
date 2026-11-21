package science.prokulski.apknewsreader

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

data class NewsItem(val id: String, val title: String, val link: String, val summary: String, val source: String, val date: String)

object FeedReader {
    fun fetch(address: String): List<NewsItem> {
        val url = URL(address)
        require(url.protocol == "https" || url.protocol == "http") { "Only HTTP(S) feeds supported" }
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        conn.setRequestProperty("User-Agent", "APKNewsReader/0.1")
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val parser = Xml.newPullParser()
            conn.inputStream.buffered().use { stream ->
                parser.setInput(stream, null)
                return parse(parser, url.host)
            }
        } finally { conn.disconnect() }
    }

    internal fun parse(parser: XmlPullParser, source: String): List<NewsItem> {
        val items = mutableListOf<NewsItem>()
        var inside = false
        var container = ""
        var title = ""
        var link = ""
        var description = ""
        var date = ""
        var guid = ""
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name?.substringAfter(':')?.lowercase(Locale.ROOT) ?: ""
            if (event == XmlPullParser.START_TAG && (tag == "item" || tag == "entry")) {
                inside = true; container = tag
                title = ""; link = ""; description = ""; date = ""; guid = ""
            } else if (inside && event == XmlPullParser.START_TAG) {
                when (tag) {
                    "title" -> title = parser.nextText().trim()
                    "description", "summary", "content" -> if (description.isBlank()) description = parser.nextText().trim()
                    "pubdate", "published", "updated", "date" -> if (date.isBlank()) date = parser.nextText().trim()
                    "guid", "id" -> guid = parser.nextText().trim()
                    "link" -> {
                        val href = parser.getAttributeValue(null, "href")
                        val rel = parser.getAttributeValue(null, "rel")
                        if (href != null) {
                            if (link.isBlank() && (rel == null || rel == "alternate")) link = href
                        } else link = parser.nextText().trim()
                    }
                }
            } else if (event == XmlPullParser.END_TAG && inside && tag == container) {
                if (title.isNotBlank() && link.isNotBlank()) {
                    items.add(NewsItem(guid.ifBlank { link }, title, link, description.replace(Regex("<[^>]*>"), " ").take(700), source, date))
                }
                inside = false
            }
            event = parser.next()
        }
        return items.distinctBy { it.id }
    }
}
