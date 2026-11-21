package science.prokulski.apknewsreader

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

/** HTML/RSS discovery without a browser engine or API keys. Network work must run off the UI thread. */
object AutoSource {
    private const val USER_AGENT = "Mozilla/5.0 (compatible; APKNewsReader/0.3)"
    private fun page(url: String): Document = Jsoup.connect(url).userAgent(USER_AGENT)
        .timeout(15000).maxBodySize(2_000_000).followRedirects(true).get()

    fun discover(address: String): List<NewsItem> {
        val uri = URI(address)
        require(uri.scheme == "https" || uri.scheme == "http") { "Only HTTP(S) URLs supported" }
        val host = uri.host?.lowercase() ?: error("Missing hostname")
        if (host == "youtube.com" || host.endsWith(".youtube.com")) return youtube(address)
        if (uri.path.orEmpty().endsWith(".xml") || listOf("/feed", "/feed/", "/rss", "/rss/", "/atom", "/atom/").any { uri.path.orEmpty().endsWith(it) })
            return FeedReader.fetch(address)

        val doc = page(address)
        val feed = doc.select("link[rel~=alternate][type*=rss],link[rel~=alternate][type*=atom],link[rel~=alternate][type*=xml]")
            .map { it.absUrl("href") }.firstOrNull { it.startsWith("http") }
        if (feed != null) runCatching { FeedReader.fetch(feed) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }

        val discovered = LinkedHashMap<String, NewsItem>()
        fun collect(d: Document) {
            for (item in extractArticles(d, host)) discovered.putIfAbsent(item.link, item)
        }
        collect(doc)
        // Only follow explicit next-page navigation; never crawl an entire site.
        val next = doc.selectFirst("link[rel=next],a[rel=next],a.next,a.nextpostslink,a.page-numbers.next")
            ?.absUrl("href").orEmpty()
        if (next.isNotBlank() && URI(next).host == host && next != address)
            runCatching { collect(page(next)) }
        if (discovered.isEmpty()) {
            val sitemap = URI(address).resolve("/sitemap.xml").toString()
            runCatching { discoverSitemap(sitemap, host) }.getOrNull()?.forEach { discovered.putIfAbsent(it.link, it) }
        }
        return discovered.values.take(150)
    }

    internal fun extractArticles(doc: Document, host: String): List<NewsItem> {
        val containers = doc.select("article,.post,.hentry,.blog-post,.post-preview,.post-item,.entry")
        val candidates: List<Element> = if (containers.isNotEmpty()) containers
            else doc.select("main h2,main h3,.posts h2,.posts h3,.post-list h2,.post-list h3").toList()
        return candidates.mapNotNull { element ->
            val heading = element.selectFirst("h1,h2,h3")
            val anchor = heading?.selectFirst("a[href]") ?: element.selectFirst("a[rel=bookmark],a[href]")
            val link = anchor?.absUrl("href").orEmpty().substringBefore('#')
            val title = (heading?.text() ?: anchor?.text()).orEmpty().trim()
            val uri = runCatching { URI(link) }.getOrNull()
            if (title.length < 8 || uri?.scheme !in listOf("https","http") || uri?.host.isNullOrBlank() ||
                uri?.host != host || link == doc.location().substringBefore('#')) null
            else NewsItem(link, title, link,
                element.selectFirst("p,.excerpt,.summary")?.text().orEmpty().take(700),
                host, element.selectFirst("time[datetime]")?.attr("datetime")
                    ?: element.selectFirst("time")?.text().orEmpty())
        }.distinctBy { it.link }
    }

    private fun discoverSitemap(url: String, host: String): List<NewsItem> {
        val doc = Jsoup.connect(url).userAgent(USER_AGENT).timeout(12000).get()
        val urls = doc.select("url loc").map { it.text() }.filter { runCatching { URI(it).host == host }.getOrDefault(false) }
        return urls.filter { URI(it).path.trim('/').contains('/') }.take(50).map { link ->
            NewsItem(link, URI(link).path.substringAfterLast('/').replace('-', ' '), link, "", host, "")
        }
    }

    private fun youtube(address: String): List<NewsItem> {
        val uri = URI(address)
        val direct = Regex("/channel/(UC[A-Za-z0-9_-]{20,})").find(uri.path)?.groupValues?.get(1)
        val id = direct ?: run {
            val doc = page(address)
            val canonical = doc.selectFirst("link[rel=canonical]")?.absUrl("href").orEmpty()
            val metadata = doc.selectFirst("meta[itemprop=channelId],meta[itemprop=identifier]")?.attr("content").orEmpty()
            val html = doc.outerHtml()
            listOf(
                Regex("/channel/(UC[A-Za-z0-9_-]{20,})").find(canonical)?.groupValues?.get(1),
                metadata.takeIf { it.matches(Regex("UC[A-Za-z0-9_-]{20,}")) },
                Regex("\\\"externalId\\\"\\s*:\\s*\\\"(UC[A-Za-z0-9_-]{20,})\\\"").find(html)?.groupValues?.get(1),
                Regex("\\\"channelId\\\"\\s*:\\s*\\\"(UC[A-Za-z0-9_-]{20,})\\\"").find(html)?.groupValues?.get(1)
            ).firstOrNull { it != null } ?: error("Could not resolve YouTube channel ID from this URL")
        }
        return FeedReader.fetch("https://www.youtube.com/feeds/videos.xml?channel_id=$id")
    }
}
