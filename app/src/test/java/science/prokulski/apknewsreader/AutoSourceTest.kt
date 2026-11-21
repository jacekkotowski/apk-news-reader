package science.prokulski.apknewsreader

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSourceTest {
    @Test fun quartoListingRecognizedWithoutRss() {
        val html = """
            <main><div class="quarto-post">
              <div class="listing-title"><a href="/posts/analysis-2026/">Data analysis in practice</a></div>
              <div class="listing-description">An article about practical data analysis.</div>
            </div></main>
        """.trimIndent()
        val doc = Jsoup.parse(html, "https://blog.prokulski.science/")
        val result = AutoSource.extractArticles(doc, "blog.prokulski.science")
        assertEquals(1, result.size)
        assertEquals("Data analysis in practice", result.single().title)
        assertEquals("https://blog.prokulski.science/posts/analysis-2026/", result.single().link)
        assertTrue(result.single().summary.contains("practical"))
    }

    @Test fun navigationAndExternalLinksAreExcluded() {
        val doc = Jsoup.parse("""
            <main><div class="quarto-post"><div class="listing-title"><a href="https://example.org/">External link</a></div></div></main>
        """.trimIndent(), "https://blog.prokulski.science/")
        assertTrue(AutoSource.extractArticles(doc, "blog.prokulski.science").isEmpty())
    }
}
