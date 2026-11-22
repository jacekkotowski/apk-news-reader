## Stash Reader 0.1: the first feed

**New**
- A vertical feed of items from 20 starter RSS/Atom sources: tidymodels and R, Power BI, AI for data science, data-science skills, Claude Code. Items load on start and with the Refresh button (four sources at a time; one broken source never stops the others).
- Tap a card to open it: you see the feed's own text, cleaned of scripts, styles, frames and event attributes. A label says whether it is the full text as sent by the feed or only a summary. "Open in browser" opens the real article.
- **Keep** moves a card to the Kept tab, **Done** hides it for good. The same article from two feeds shows once. At most 2000 items are stored; kept items are never removed.
- Feeds that break the XML rules in one place (the Tidyverse blog does) are repaired and still load.
- Sources tab: status of every source (OK, error text, last update), on/off switch, add your own https RSS/Atom address.
- Light and dark colours follow the phone setting.

**Try on the phone:** install, wait a few seconds for the first refresh, scroll, open a card, press Keep, look in Kept, switch a source off in Sources, add your own blog feed address.

**Starter feeds dropped** (checked with curl on 2026-11-22): `tidymodels.org/start/index.xml` (404), `posit.co/blog/feed/` (404), `blog.rstudio.com/index.xml` (returns a web page), `anthropic.com/rss.xml` (404), `sqlbi.com/blog/feed/` (same feed as `sqlbi.com/feed/`). Also left out to keep the list near 20: Machine Learning Mastery, KDnuggets, O'Reilly Radar.

**Known limits**
- Nothing here has been tested on a real phone; the emulator smoke test only shows that the app starts and draws the feed.
- Cards show the feed text only. Full-article reading arrives in step 2, topics and ranking in step 3 (the order is newest first for now).
- No background refresh yet.
