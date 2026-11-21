package science.prokulski.apknewsreader

import org.jsoup.Jsoup
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Simple, conservative discovery; no headless browser or remote scraping service. */
object AutoSource {
    fun discover(address: String): List<NewsItem> {
        val uri = URI(address)
        require(uri.scheme == "https" || uri.scheme == "http") { "Only HTTP(S) URLs" }
        val host = uri.host ?: error("Missing hostname")
        if (uri.path.orEmpty().matches(Regex(".*(feed|rss|atom|\\\\.xml)/?"))) return FeedReader.fetch(address)
        if (host == "youtube.com" || host == "www.youtube.com" || host == "m.youtube.com") {
            val channelId = Regex("/channel/(UC[\\w-]+)").find(uri.path)?.groupValues?.get(1)
            if (channelId != null) return FeedReader.fetch("https://www.youtube.com/feeds/videos.xml?channel_id=$channelId")
            error("For YouTube, use a /channel/UC... URL; handle-based discovery is not available yet.")
        }
        val conn = URL(address).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000; conn.readTimeout = 15000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; APKNewsReader/0.2)")
        try {
            require(conn.responseCode in 200..299) { "HTTP ${conn.responseCode}" }
            val html = conn.inputStream.bufferedReader().use { it.readText() }
            val doc = Jsoup.parse(html, address)
            val feed = doc.select("link[rel=alternate][type*=rss], link[rel=alternate][type*=atom], link[rel=alternate][type*=xml]")
                .firstOrNull()?.absUrl("href")
            if (!feed.isNullOrBlank()) return FeedReader.fetch(feed)
            val candidates = doc.select("article, .post, .entry, .blog-post, .post-preview")
            val cards = if (candidates.size >= 2) candidates else doc.select("main h2, main h3, .posts h2, .posts h3")
            return cards.mapNotNull { element ->
                val anchor = if (element.tagName() == "a") element else element.selectFirst("h1 a, h2 a, h3 a, a[href]")
                val link = anchor?.absUrl("href").orEmpty()
                val title = (element.selectFirst("h1,h2,h3")?.text() ?: anchor?.text()).orEmpty().trim()
                if (title.length < 8 || link.isBlank() || !link.startsWith("http")) null
                else NewsItem(link, title, link, element.selectFirst("p")?.text().orEmpty().take(700), host, element.selectFirst("time")?.attr("datetime").orEmpty())
            }.distinctBy { it.link }.take(100)
        } finally { conn.disconnect() }
    }
}
