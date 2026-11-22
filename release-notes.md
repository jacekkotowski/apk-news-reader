## Stash Reader 0.6: page watch and "because you kept X"

**Fixed**
- When a refresh finished while you were reading, the Feed was redrawn and the open card closed under you (a highlight or note in progress could be lost). The list now waits until no card is open and says "New items are ready; close the open card to show them".

**New**
- **Keep is one toggle**: a plain **Keep** button that turns into **✓ Kept** when pressed, and back when pressed again. No Undo button. Done and Less like this show only on cards you have not kept.
- **Watch a page** (Sources > Add a search or watch a page): for a site without RSS, paste the address of its list of posts. Each day the app reads that page once, finds the article links on it, and every link it has not seen before becomes an item (title and description read from the article page, at most 8 new pages per day, one second apart). If the page builds its list with JavaScript, the status says so instead of failing silently.
- **Watch a sitemap**: same idea from a `sitemap.xml` (newest `lastmod` first). A sitemap index is refused with a hint to add one of the sitemaps it lists.
- **robots.txt is honoured**: a page or article that robots.txt closes to robots is not read; the status says so.
- **Recommendations**: when you keep a paper (a link from arXiv, Hugging Face daily papers, Semantic Scholar or a DOI), it becomes a seed. A new source "Recommendations from papers you kept" asks Semantic Scholar for similar papers (papers you marked Less like this count as negative examples) and for recent papers that cite your two newest seeds. Those cards say "Recommended from papers you kept: ..." or "Cites ... which you kept" in the why line and rank a little higher. With no kept paper yet, its status says what to do (shown in grey, not as an error).

**Try on the phone:** keep an arXiv or Hugging Face paper from the Feed, then open Sources and wait for "Recommendations" to fill (at most every 3 hours, so it may take the next refresh). Watch a blog without RSS, for example `anthropic.com/news` or a university group's news page, and look for its newest posts after the refresh.

**Known limits**
- Nothing here has been tested on a real phone. On the PC the robots rules, the link finder, the page-meta reader, the sitemap reader and the recommendation parsers are tested with saved files; the link finder was also tried on three live pages. The emulator only shows that the app starts and the screens draw; the watch and recommendation requests were not driven there.
- The recommendation answers of Semantic Scholar are tested with hand-written files, and Semantic Scholar often answers HTTP 429 without a key; the source then waits and shows the reason.
- The link finder is a heuristic: a watched page can also list navigation links that look like articles, or miss unusual layouts.
- Watched pages that need JavaScript are not supported.
