# Stash reader: plan (draft 2026-11-22)

A personal "Deepstash on my terms": a vertical feed of things to learn
(data science, new developments, ideas, skills). I decide the sources and the
topics, the app ranks them, I read, keep and export what I learned.
Reading comes first; discovery grows step by step.

## 0. Decisions (Jacek, 2026-11-22)

- Same repo, `jacekkotowski/apk-news-reader` (public). Old code replaced;
  the old `main` is kept only as tag `old-attempt`, PR #1 closed.
- App name "Stash Reader", package `pl.stashreader`. English interface;
  Polish sources welcome.
- Own signing key in `apk-signing-keys/apk-news-reader/`, CI secrets
  `KEYSTORE_B64` and `KS_PASS` as in apk-cw-news.
- Export (Claude's choice): each kept item becomes one Markdown note
  (YAML front matter: title, source, url, date, topics; then highlights as
  `>` quotes and your note). Two ways out, both Android-only, no library:
  (a) Share button: sends the note to any app (Obsidian, Drive, e-mail);
  (b) "Export folder" picked once with the system folder picker; every Keep
  writes `YYYY-MM-DD title.md` there. Pointing that folder at a synced vault
  folder (Obsidian Sync or Syncthing) later needs no app change.
- Starter topics: tidymodels, Power BI, AI for data science, data-science
  skills, Claude Code tricks and solutions. Each comes with a starter set of
  feeds and search queries the user can edit.
- Curator job off the phone: yes, but last (step 8). Costs: GitHub Actions is
  free for a public repo; LLM or Firecrawl calls inside it would be paid, so
  the default curator uses only free APIs.

## 1. Why the first attempt failed (jacekkotowski/apk-news-reader)

| Fact | Consequence |
|---|---|
| 32 GitHub Actions runs, 32 failures. No APK was ever built. | Nothing was ever tested on a phone. |
| Gradle + AGP 8.13 + Kotlin 2.3 + Compose BOM, no Gradle wrapper, no local SDK; every fix was a blind push to CI. | Most commits fixed the build, not the app. |
| Features piled up before a first APK: podcasts, a foreground media service, YouTube handle scraping, AutoSource. | 484 lines nobody had run. |
| Last run (branch `verify-and-fix-reader`) did compile; it stopped on one failing unit test of the Quarto "no RSS" heuristic. | The fixture blog `blog.prokulski.science` **has** RSS (`/index.xml`, announced in `<link rel=alternate>`). The test solved a problem that does not exist there. |
| Package `science.prokulski...` | Named after the fixture blog, not you. |

What is worth keeping: the idea (continuous feed, tap to expand, local-first),
the rules in AGENTS.md (no tracking, no keys in the repo, honour robots and
paywalls, never pass an excerpt off as full text). The code itself: nothing.

## 2. The lean route: copy what already works (apk-cw-news)

Your CW News app builds green in about 1 minute, signed, with **zero
third-party libraries and no Gradle**: `build.sh` runs aapt2, javac, d8,
zipalign, apksigner. UI is one `index.html` in a WebView with a small Java
bridge. Core classes have no Android imports and are tested on the PC
(`tests/run.sh`, JDK 17 is installed). Signing key in `apk-signing-keys`,
CI secrets `KEYSTORE_B64`/`KS_PASS` already proven.

The new app reuses this skeleton and three tested parts:
`FeedParser.java` (RSS/Atom), `Db.java` (SQLite), `Refresher` + `RefreshJob`
(background refresh with JobScheduler).

Why a WebView UI suits a reader better than Compose:
- Text layout, fonts, images, code blocks, dark mode: the browser engine does it.
- **Full-text reader mode with no dependency**: Mozilla `Readability.js`
  (Apache-2.0, one file, the code behind Firefox Reader View) runs in the
  WebView on the fetched HTML via `DOMParser`. Same for finding article lists
  on blogs without RSS: `DOMParser` + CSS selectors in JS, no jsoup.
- UI changes are HTML/JS edits: fast for Sonnet, no compile of UI code.

Dependency count for v1: **0 libraries** (one vendored JS file).
APK expected well under 1 MB.

## 3. What the app is

**Feed (home).** One vertical scroll of cards: title, source, 2-line
summary, topic chips, estimated reading time. Tap: expands inline to the
full text (reader mode, cached offline). Actions on a card: Keep, Less like
this, Done. No auto-play, no infinite fake content: when unread items end,
the feed says so.

**Reader.** Readability text, adjustable font size, dark mode, highlight a
passage, add a one-line note. Article stays readable offline.

**Stash.** Everything kept, with highlights and notes, filter by topic.
Export a kept item as a Markdown note (title, source link, date, highlights,
note) via the Android share sheet or into a chosen folder, so it can land in
your Obsidian vault (`05_Wiedza/`).

**Sources & topics (you decide what is pushed).** Topics are words/phrases
with a weight (e.g. "tidymodels", "causal inference", "duckdb", "LLM agents").
Sources are feeds, sites without RSS, or queries against open APIs.
Ranking v1 is a plain, explainable score: topic word matches (BM25 on title
+ summary) + boost from what you kept + small freshness term, minus what you
marked "less like this". Each card can show "why this": the matched topics.

## 4. Discovery: where new things come from

From cheapest/most reliable to hardest. All run on the phone, no backend.

1. **RSS/Atom you add** (most blogs, Substack `/feed`, Medium `/feed/@user`,
   Quarto `index.xml`, WordPress `/feed`, Ghost `/rss`, YouTube channel RSS).
   Paste any page URL: the app reads `<link rel=alternate>`, then tries
   these common paths. This alone covers most "no RSS" cases.
2. **Open APIs as "query sources"** (a topic becomes a search):
   - arXiv API / category RSS (cs.LG, stat.ML, stat.ME), no key.
   - Hacker News via Algolia search API, filter by points, no key.
   - Hugging Face daily papers (`huggingface.co/api/daily_papers`), no key.
   - Reddit subreddit/search `.rss`, no key.
   - GitHub search API for new repos by topic with stars, no key (low limit).
   - Semantic Scholar: search + **recommendations from seed papers**
     (positive/negative paper IDs). No key needed at low volume.
   - OpenAlex: free key ($1/day free budget), needed beyond page 1.
3. **Sites without RSS: watch the page.** Store the set of article links on
   a listing page (blog index, "news", "publications"); on refresh, links not
   seen before are new items; fetch each and run Readability for title and
   text. Also `sitemap.xml` `<lastmod>`. This is the changedetection.io idea,
   reduced to "new links on one page you chose". Polite: one page per source
   per refresh, honour robots.txt, at most daily. Pages built only by
   JavaScript can be loaded in a hidden WebView later (step 6), not in v1.
4. **Citation-graph discovery (the Research Rabbit / Litmaps / Inciteful
   idea).** Those tools are built on the same open data (Semantic Scholar,
   OpenAlex, Crossref). The app can do the useful core: when you Keep a
   paper, it becomes a seed; the app asks Semantic Scholar for
   recommendations and for new papers that cite your seeds, and puts them in
   the feed tagged "because you kept X". Open Knowledge Maps and VOSviewer are
   map views for a desk screen: not part of a phone feed; a "map" of your
   stash can be a later desktop export.
5. **Learning from you (patterns).** v1: keyword profile from kept and
   rejected items (no ML). Later, optional: on-device sentence embeddings for
   "more like this" (adds a model file of tens of MB, so only if keyword
   ranking proves too weak).
6. **Later, optional: a "curator" job off the phone.** A GitHub Actions cron
   (free) that runs heavier discovery (an LLM summary, a JS-rendered scrape,
   Firecrawl) and writes one `feed.json` the phone reads as a source. Keeps
   the APK lean and lets discovery change without a new build. Not in v1.

## 5. Steps to the first APK and beyond

Each step ends with: tests pass on the PC, CI green, APK installed on your
phone, and a short note of what was and was not tested on the phone.

| Step | Delivers | Done when |
|---|---|---|
| 0 | Repo `apk-news-reader` reset: tag `old-attempt`, close PR #1, delete branch, replace contents with a skeleton copied from apk-cw-news, package `pl.stashreader`, own signing key in `apk-signing-keys`, CI secrets. | CI builds a signed "hello" APK; installs on phone. |
| 1 **first APK** | Feed of RSS sources (a starter list of ~15 data-science feeds), cards, tap to expand to the summary, open in browser, Keep. SQLite cache, pull to refresh. | You scroll real items on the phone. |
| 2 | Reader mode: Readability.js full text, offline cache, font size, dark mode. Add source by page URL (feed autodiscovery + common paths). | Ten real blogs open as clean text. |
| 3 | Topics + explainable ranking, "why this", Less like this, Done; background refresh (RefreshJob). | Feed order changes visibly with your topics. |
| 4 | Stash screen, highlights + notes, Markdown export to share sheet / folder. | A kept article arrives as a note in Obsidian. |
| 5 | Query sources: arXiv, HN, HF papers, Reddit, GitHub, Semantic Scholar. | Topics bring items from sources you never added by hand. |
| 6 | Page watch for sites without RSS; sitemap lastmod. | A chosen no-RSS site shows its new post within a day. |
| 7 | Seed-paper recommendations (Semantic Scholar), "because you kept X". | Keeping a paper brings related new papers. |
| 8 | Curator: GitHub Actions cron (daily) runs a script over free APIs and writes `feed.json` to GitHub Pages; the app adds it as one source. | The phone shows curator items; Actions minutes stay at 0 cost. |

Out of scope until asked: podcasts, YouTube playback, accounts, sync, AI
summaries, Play Store.

## 6. Risks

- WebView `fetch()` hits CORS: network calls go through the Java bridge
  (HttpURLConnection on a background thread), JS only parses.
- Readability fails on some pages: show the feed summary and an "Open in
  browser" button, labelled as an excerpt.
- API rate limits: cache responses, refresh at most every few hours, back
  off on HTTP 429.
- Page-watch on JS-only sites returns nothing: say so in the source's status
  line instead of failing silently.

## 7. Progress (autonomous run, started 2026-11-22)

- [x] Step 0, 2026-11-22: skeleton, signed CI, emulator smoke. Release https://github.com/jacekkotowski/apk-news-reader/releases/tag/v0.0 (CI run https://github.com/jacekkotowski/apk-news-reader/actions/runs/45039572117)
- [x] Step 1, 2026-11-22: feed, cards, Keep/Done, Sources screen. Release https://github.com/jacekkotowski/apk-news-reader/releases/tag/v0.1 (CI run https://github.com/jacekkotowski/apk-news-reader/actions/runs/45041567103)
- [x] Step 2, 2026-11-22: reader mode (Readability.js 0.6.0), offline article cache, add source by page URL. Release https://github.com/jacekkotowski/apk-news-reader/releases/tag/v0.2 (CI run https://github.com/jacekkotowski/apk-news-reader/actions/runs/45042839453). Reader mode success path checked in a browser with a real article; in the emulator the three tried cards were all Towards Data Science, which answers HTTP 403, so only the fallback to "Excerpt" was seen there.
- [x] Step 3, 2026-11-22: topics with weights, explainable ranking (BM25 + kept/less + freshness), why-this line, Less like this, background refresh (RefreshJob, default every 6 h). Release https://github.com/jacekkotowski/apk-news-reader/releases/tag/v0.3 (CI run https://github.com/jacekkotowski/apk-news-reader/actions/runs/45044659198). The background job was scheduled but not seen firing.
- [x] Step 4, 2026-11-22: Stash tab, highlights + notes, Markdown share and export folder. Release https://github.com/jacekkotowski/apk-news-reader/releases/tag/v0.4 (CI run https://github.com/jacekkotowski/apk-news-reader/actions/runs/45047248685). Emulator showed reader mode on a real article and Keep into the Stash. Not exercised: text selection, highlights, share sheet, writing into the export folder (the folder picker did open once by accident).
- [x] Step 5, 2026-11-22: search sources (arXiv, Hacker News, Hugging Face papers, Reddit, GitHub, Semantic Scholar), seven starter searches, at most one request per search every 3 hours. Release https://github.com/jacekkotowski/apk-news-reader/releases/tag/v0.5 (CI run https://github.com/jacekkotowski/apk-news-reader/actions/runs/45048719238).
- [x] Steps 6 and 7, 2026-11-22: page and sitemap watch with robots.txt, recommendations from kept papers. Release https://github.com/jacekkotowski/apk-news-reader/releases/tag/v0.6 (CI run https://github.com/jacekkotowski/apk-news-reader/actions/runs/45064965080).
- [x] Step 8, 2026-11-22: daily curator on GitHub Actions, Atom feed on branch curator-data. Release https://github.com/jacekkotowski/apk-news-reader/releases/tag/v0.7 (CI run https://github.com/jacekkotowski/apk-news-reader/actions/runs/45066117861).

- [x] Step 9 (added 2026-11-22 at Jacek's request): YouTube channels and podcasts, lean form (Watch opens the YouTube app, Listen opens the phone's audio player). Release https://github.com/jacekkotowski/apk-news-reader/releases/tag/v0.8 (CI run https://github.com/jacekkotowski/apk-news-reader/actions/runs/45074079681). Not tested on a phone.

- [x] Steps 10 and 11, 2026-11-22 (Jacek's requests): v0.9 share to Stash and recall cards (https://github.com/jacekkotowski/apk-news-reader/releases/tag/v0.9, CI run https://github.com/jacekkotowski/apk-news-reader/actions/runs/45081607827); v1.0 Gemini notes and learning paths with his own free key (https://github.com/jacekkotowski/apk-news-reader/releases/tag/v1.0, CI run https://github.com/jacekkotowski/apk-news-reader/actions/runs/45082816528). Not tested on a phone.

- [x] v1.0.1 (YouTube channel address fix) and v1.1 (fewer buttons), 2026-11-22: https://github.com/jacekkotowski/apk-news-reader/releases/tag/v1.0.1 and https://github.com/jacekkotowski/apk-news-reader/releases/tag/v1.1 (CI runs 45085715469 and the v1.1 tag run). Not tested on a phone.

- [x] v1.2, 2026-11-23: Feed with a short list, filters and search behind the three dots, 30-day expiry, automatic notes folder Documents/Stash Reader, Settings group. https://github.com/jacekkotowski/apk-news-reader/releases/tag/v1.2 (CI run 45090729772). Emulator: a kept note appears as a .md file in the folder. Not tested on a phone.

Four releases planned 2026-11-23 (approved by Jacek): v1.3 notes anywhere and a Notes view; v1.4 Fit My Time and Surprise Me; v1.5 One Story Many Sources and Ask My Stash; v1.6 Listen to Anything.

- [x] v1.3, 2026-11-23: links open inside Stash as reader pages, one pencil button keeps the page and makes Gemini notes, Notes chip in the Stash with a Markdown view. Release https://github.com/jacekkotowski/apk-news-reader/releases/tag/v1.3 (CI run 45160827183). Not tested on a phone.
- [x] v1.4, 2026-11-23: Fit My Time (5 / 15 / 30 min, one plan a day, Done for today) and Surprise Me in the Feed. The tags v1.4 and v1.4.1 failed the emulator test (a plan made before the sources loaded stayed empty; then the smoke script tapped the new time buttons instead of a card); the working build is v1.4.2: https://github.com/jacekkotowski/apk-news-reader/releases/tag/v1.4.2. Not tested on a phone.
- [x] v1.5, 2026-11-23: One Story Many Sources (`Clusters`) and Ask My Stash (FTS4, optional Gemini answer). The tag v1.5 failed to compile (a misplaced line in Db.java); the working build is v1.5.1: https://github.com/jacekkotowski/apk-news-reader/releases/tag/v1.5.1. Not tested on a phone.
- [ ] v1.6: Listen to Anything (TextToSpeech foreground service, play queue).

## 8. Open problems

See prompts/run-report.md. In short: nothing was tested on a real phone; Reddit and Semantic Scholar could not be checked for real (HTTP 404 and 429 from the build computer); the Stash folder export, share sheet and text selection were not exercised in the emulator; steps 6 to 8 are not started.

Added after steps 6 to 8: the curator's scheduled run has not fired yet; page watch, recommendations, highlights, share and the export folder were not used on a phone.

Added after v1.0: Gemini was called live only from the PC (answers recorded as test fixtures); Make notes and the learning path were never pressed in the app on a real phone. Free-tier limits and terms can change (the YouTube feature is a preview). The key Jacek pasted in chat is in the private keys repo (apk-news-reader/gemini.env); it is also in this session's transcript, so he may want to make a new key after testing.


## 9. Future plans (ideas for a new session, not commitments)

How to work: plan first, ask Jacek the questions that matter, wait for his yes, then build in small releases (his rule, 2026-11-23). He wants fewer buttons, not more; propose the smallest version. Nothing has been tested on a real phone yet, so the first job of the next session is to read what he found on the phone.

**First, in this order**
1. Collect his corrections from testing v1.1 (install, then the "Try on the phone" lists of v0.8 to v1.1), fix them in one small release.
2. Make a new Gemini key (the old one was pasted in chat) and replace `apk-signing-keys/apk-news-reader/gemini.env`.
3. Check the curator's first scheduled run (05:17 UTC) on branch `curator-data`.

**Ideas he showed interest in or that follow from his goals**
- **A finite daily session.** Done in v1.2 in its simple form (Top 15, then Show more). Possible next step: a real "done for today" end with a daily count.
- **A "follow a person" box in Sources.** Paste a Bluesky handle, a GitHub user, a Substack or blog; the app builds the feed address. Checked 2026-11-22: Bluesky (`bsky.app/profile/<handle>/rss`) and GitHub (`github.com/<user>.atom`) work; X and LinkedIn have no usable feeds; he does not want Mastodon.
- **Podcast player inside the app** (background play, position, speed) only if the phone's own player turns out to be a pain; it needs a foreground service and is the one piece that may deserve a bigger plan.
- **Video thumbnails** on YouTube cards.
- **Recall cards from your own notes**, and notes for podcast episodes from their show notes.
- **Gemini safety nets:** a second model if `gemini-3.5-flash-lite` goes away, a clear message when the free limit is reached (exists), a daily count shown in Sources.

**Known problems carried over** (see section 8): removing an item from the Stash does not delete its exported file; two items with the same title kept on the same day share a file name; highlights are plain text, not positions; Reddit and Semantic Scholar often answer 429 or 404 to automated requests; Towards Data Science blocks reader mode (HTTP 403); on a YouTube consent page the add-by-address flow may fail (the feed address `youtube.com/feeds/videos.xml?channel_id=...` always works); the page-watch link finder is a heuristic.

