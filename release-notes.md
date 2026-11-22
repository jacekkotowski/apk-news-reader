## Stash Reader 0.2: reader mode and add-by-page

**New**
- **Read full article** on every opened card: the app downloads the page and extracts the article text with Mozilla Readability (the code behind Firefox Reader View). The cleaned text is saved on the phone, so it opens again offline. Scripts, styles, frames and images are removed.
- If reader mode cannot find the article (paywall, a site that refuses the download, a JavaScript-only page, no network), the card falls back to the feed text labelled **Excerpt**, with the reason and the Open in browser button. An excerpt is never shown as the full article.
- **A- / A+** change the reading size; the choice is remembered.
- **Add a source from any page address** (Sources tab): paste a blog address such as `example.com/blog`. The app reads the feed the page announces (`<link rel="alternate">`), then tries `/feed`, `/rss`, `/index.xml`, `/atom.xml`, `/feed.xml`. At most nine requests per attempt.
- Reading time is corrected once the full article is saved.

**Try on the phone:** open a card and press Read full article on ten different blogs (Julia Silge, SQLBI, Simon Willison, Prokulski...). Turn on aeroplane mode and open one of them again. Add a blog by its plain address in Sources.

**Known limits**
- Nothing here has been tested on a real phone. The emulator smoke test presses Read full article on three cards; it shows that the flow runs, not that ten blogs read well.
- Some sites answer the download with an error (Towards Data Science gave HTTP 403 in the emulator); those cards show the excerpt.
- Images in articles are not shown or saved.
- Topics and ranking come in step 3; the feed is still newest first.
