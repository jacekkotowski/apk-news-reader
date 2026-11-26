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
- [x] v1.4, 2026-11-23: Fit My Time (5 / 15 / 30 min, one plan a day, Done for today) and Surprise Me in the Feed. The tags v1.4 and v1.4.1 failed the emulator test (a plan made before the sources loaded stayed empty; then the smoke script tapped the new time buttons instead of a card); the working build is v1.4.2: https://github.com/jacekkotowski/apk-news-reader/releases/tag/v1.4.2 (CI run 45163834892). Not tested on a phone.
- [x] v1.5, 2026-11-23: One Story Many Sources (`Clusters`) and Ask My Stash (FTS4, optional Gemini answer). The tag v1.5 failed to compile (a misplaced line in Db.java); the working build is v1.5.1: https://github.com/jacekkotowski/apk-news-reader/releases/tag/v1.5.1 (CI run 45164526686). Not tested on a phone.
- [x] v1.6, 2026-11-23: Listen to Anything (TextToSpeech foreground service with media notification, play queue, speed, position per sentence). Release https://github.com/jacekkotowski/apk-news-reader/releases/tag/v1.6 (CI run 45165172557). Not tested on a phone; nothing was heard.

## 8a. State after v1.6 (2026-11-23, read this first)

How the four releases work, where the code is, and what is unproven. Later releases are added here as they are built.

- **Remembered sort (v1.13).** `localStorage` key `sort` (`savedNewest` / `saveSort` in index.html). Newest is a sort, not a filter: neither `fCount()` nor `FeedFilter.Filters.count()` counts it, so `plain()` stays true and Clear keeps it. In `Db.feedJson` Newest still ranks, then `Ranker.inOrder` puts the items back into date order before `Ranker.mix`, so the score only picks the surprises; only surprise cards show a reason line. All links take the accent colour (one global `a` rule).
- **Learner (v1.15).** Core class `Learner` (tested) replaced `Ranker.profile`/`similarity`. Features: words (title 1, summary-only 0.5), source id, matched topics, kind. For each feature the lift of its rate in kept items over its rate in the Feed pool, and the same for less items (each only above 0, with a prior of 2 pool-rate pseudo counts); weight = kept lift - less lift. Labels fade with half-life 60 days (`kept_at`, which `Db.mark`/`setState` now also set for less items; older less items use `fetched`). A feature needs 2 labels once a side has 3 or more. Caps: word 1.5, all words 2.5, source 1.5, topics 0.75, kind 0.5, total 3.5. `Db.labels()` gives the last 300 kept and 300 less. The why line names the source ("You keep a lot from X", "Lowered: you often swipe away items from X") or the words.
- **Quiet signals, exploring surprises, stricter clusters (v1.16).** DB version 15 adds `items.seen` (first time a Feed card was at least 60% on screen for 1.5 s; IntersectionObserver `seenWatch` in index.html, sent in batches of 5, on tab change and when the app is hidden, bridge `seen(ids)`) and `items.opened_at` (an opened Feed card still open after 30 s, bridge `opened(id)`). `Db.labels()` adds, for items neither kept nor less from the last 30 days: read = a kept label of weight 0.3 (`Learner.READ_WEIGHT`); seen more than a day ago and never opened = a less label of weight 0.15 for the source only (`SEEN_WEIGHT`). Rates are compared with the Feed, so a source that fills half the Feed and half of what you scroll past loses little. `Ranker.Scored.unknown` marks a source the Learner has heard less than 2 about; such surprises get +0.5 quality in `Ranker.mix` and say "the app knows little about X yet". `Clusters` also needs 2 shared title words that are not common (in more than 10% of the titles and more than 5 titles).
- **Week note (v1.17).** `WeekNote.build(..., Extra)`: front matter `tags:` (topic slugs from `WeekNote.tag`, plus `stash/week`); items linked by Builds on form `## Thread: <two rarest shared words>` (`threads`, `threadName`) and are not repeated under their topic; each item gets `Note: [[file]]` (its export file name without .md, skipped when it holds `[]#^|`), then the note, else the first highlight (+ "N more"), else `Sentence from the article:` (`keySentence`, via `Listen.sentences`); `## To remember` lists up to 3 highlights (`toRemember`) whose recall cards `Db.writeWeekNote` makes due at once; a closing line with cards looked at and quiet sources. DB version 16 adds `weeks.answer` and `weeks.asked`. The Monday card (`putWeek` in index.html, top of the plain Feed) asks "What did you learn last week?" for the newest unasked note under 7 days old (bridge `weekAsk`, `weekAnswer`); `WeekNote.withAnswer` puts `## In my words` under the heading and the file is written again.
- **Swipe (v1.14).** Closed Feed cards only; the code sits between `//SWIPE-START` and `//SWIPE-END` in index.html and is tested by `tests/swipe.js` (stand-in elements, run by `tests/run.sh`). It counts at 30% of the card width with sideways at least twice the vertical move; CSS `touch-action:pan-y` leaves vertical scrolling to the page. Right = kept, left = less, together with the `also` copies. The bridge is `swipe(ids, keep, surpriseId)`, `swipeUndo()`, `swipeCommit()`; the core class `Swipes` (tested) holds the one swipe that can be undone. State changes at once (`Db.mark` touches unread items only); writing the .md file, erasing a "less" item's text (`Db.dropText`) and `surpriseLess` wait for the commit, which happens after 4 s, at the next swipe, on a tab change or in `onPause`. Undo is `Db.restore`. The Hide and Less like this buttons and their bridge calls are gone; nothing writes state DONE from the UI now (only the 30-day expiry does).

- **Feed, endless with surprises (v1.7; replaced Fit My Time of v1.4.2, which froze one list a day and was dropped after his test).** `Db.feedJson` ranks, groups clusters, and for the plain Feed (no filter, `Filters.plain()`) `Ranker.mix` (core, tested) puts a surprise at every 5th place: no topic hit, 12 min or less, quality = score + length at least 0.4, source not turned down (`sources.surprise_less`, 3 means none). The reading-time chips are a filter (`Filters.maxMins`, a video counts 10 min, a podcast 20). index.html shows 15 cards and adds 15 when scrolled near the end (`feedMore`); the bridge call is `feedFiltered(..., maxMins)`.
- **Clusters (v1.5.1).** `Clusters.group` (core): last 3 days, same link key or titles sharing at least 3 key words and 55% of the shorter title. `Db.collapse` keeps the best-ranked member and attaches the others as `also`. Known weakness: similar headlines of different stories merge.
- **Ask My Stash (v1.5.1).** FTS4 table `fts` (docid = item id * 1000 + n) plus `fts_dirty`; every writer calls `touch(id)`, `flushIndex()` rebuilds before a search. `Passages` (core) cuts text, builds the MATCH query (prefix terms joined by OR) and ranks; `Gemini.answerRequest` + `Passages.checkCitations` keep the answer to the passages. If a phone's SQLite lacks FTS4 the search returns nothing (not seen yet).
- **Notes anywhere (v1.3).** Reader links call `openPage` (overlay `#pg`), which uses `fetchUrl` + Readability + `cleanHtml` + `keepPage` (`Db.addShared`). The Android Back button needs `enableOnBackInvokedCallback="false"` in the manifest (target SDK 36) and the JS `setBack/pageBack` pair. `md2html` lives between `//MD-START` and `//MD-END` in index.html and is tested by `tests/md.js` (run by `tests/run.sh` when node exists).
- **Listen (v1.6).** `Speaker` (foreground service, TextToSpeech, MediaSession, audio focus, wake lock) reads `Listen.sentences` (core, tested); position per item in table `listen`; the bridge sends intents (`PLAY`, `TOGGLE`, `NEXT`, `STOP`, `RATE`) and polls `Speaker.state()`. Needs `<queries>` for the TTS engine and the media-playback foreground type.
- **Database version is 16** (v1.17 added `weeks.answer` and `weeks.asked`; v1.16 added `items.seen` and `items.opened_at`; 14 since v1.12 added the `weeks` table). Upgrades are in `Db.onUpgrade`.
- **Build facts learned the hard way.** No Android SDK on Jacek's PC, so only CI compiles the app classes; before pushing, run `javac -proc:none` over all sources and look for errors that are not "cannot find symbol" (that catches syntax slips). A tag that fails CI cannot be moved (the permission classifier blocks deleting or force-pushing tags), so check first: this is why the releases are v1.4.2 and v1.5.1 (v1.4, v1.4.1, v1.5 failed). The smoke script taps by screen position; any change to the top of the Feed can break it.
- **Not tested anywhere:** every v1.3 to v1.6 screen on a phone or in the emulator beyond start-up; nothing has been heard from Speaker; FTS4 on a real phone; Gemini answers (only a hand-written reply fixture); the surprise quality threshold on real data.
- **Ideas not built:** a daily count, "follow a person" box, video thumbnails, clusters by meaning instead of words, position by time for podcasts, reading aloud inside the page view, a sleep timer.
- [x] v1.8.3, 2026-11-23, found on his phone: the player buttons showed literal backslash-u codes (an escape written inside HTML, not in a JS string; use &#x...; entities there); Listen skips pre code blocks, reads web addresses as the word link, and chooses Polish (diacritics) or English, never the phone's locale. Release https://github.com/jacekkotowski/apk-news-reader/releases/tag/v1.8.3 (CI run 45177792285).
- [x] v1.8.2, 2026-11-23, his question about YouTube: the page view recognises YouTube links (`isVideoUrl`), skips Readability and lets the pencil keep the video and run `makeNotes` (video path: the link goes to Gemini as fileData). Release https://github.com/jacekkotowski/apk-news-reader/releases/tag/v1.8.2.
- [x] v1.8.1, 2026-11-23, his request: optional Semantic Scholar API key in Settings (`Refresher.s2Key`, header x-api-key only for api.semanticscholar.org, `Db.clearS2Backoff`). Release https://github.com/jacekkotowski/apk-news-reader/releases/tag/v1.8.1. Not tried against the real service.
- [x] v1.8, 2026-11-23, his questions about the .md files: Notes chip is now a notebook of everything kept with highlights, a note or AI notes, rendered from the same text as the file (`Bridge.noteMarkdown`, `stripFront` in index.html); button Open the .md file (`Exporter.fileUri`, ACTION_VIEW). Release https://github.com/jacekkotowski/apk-news-reader/releases/tag/v1.8.
- [x] v1.7.1, 2026-11-23, found on his phone: A-/A+ only changed the reader text by 1 px; now `WebSettings.setTextZoom` scales the whole app (70 to 170%, saved in prefs). Release https://github.com/jacekkotowski/apk-news-reader/releases/tag/v1.7.1.
- [x] v1.7, 2026-11-23, after his phone test: the Feed is endless (loads 15 more on scroll); 5/15/30 min is a reading-time filter, not a plan; surprises every 5th card. Reddit 404 fixed (subreddit feed address is `/new.rss`; Reddit and Semantic Scholar still answer 429 to unauthenticated apps, an S2 API key is the real fix). Release https://github.com/jacekkotowski/apk-news-reader/releases/tag/v1.7.
- [x] v1.6.1, 2026-11-23, found on his phone: Play said there was nothing to read (articles were taken for non-articles; `FeedParser.kindOf` returns an empty string for a plain article). Release https://github.com/jacekkotowski/apk-news-reader/releases/tag/v1.6.1.

Four small releases decided 2026-11-24 (approved by Jacek). Research behind them: unread counts and volume drive people away from RSS; saving is not learning; self-testing spaced over days works, highlights and summaries do not; questions work best next to the reading; people use local "related to this" lists, not whole graphs. v1.6 Listen to Anything was built before them (another session), so these take the next numbers.
- [x] v1.9, 2026-11-24: Recall in the Feed (due cards appear in the Feed scroll, one after about every 4 articles, answered in place; the "Recall: N cards" button in the Stash is removed). Release https://github.com/jacekkotowski/apk-news-reader/releases/tag/v1.9. Not tested on a phone.
- [x] v1.10, 2026-11-24: Builds on (one local line "Builds on: <kept title>" on a feed card, up to 3 at the end of an item; no Gemini). Release https://github.com/jacekkotowski/apk-news-reader/releases/tag/v1.10. Not tested on a phone.
- [x] v1.11, 2026-11-24: Quiet feeds ("kept K of N in 30 days" per source, one Mute suggestion for a source with 0 kept out of 20 or more). Release https://github.com/jacekkotowski/apk-news-reader/releases/tag/v1.11. Not tested on a phone.
- [x] v1.12, 2026-11-24: Week note (one Markdown note per week, written automatically, local, no Gemini, no button). Release https://github.com/jacekkotowski/apk-news-reader/releases/tag/v1.12. Not tested on a phone.
Dropped by Jacek 2026-11-24: a concepts table, rabbit-hole searches, a typed-relation graph, a graph view, Gemini in the week note.

Five releases decided 2026-11-26 after his test of v1.12 (approved by Jacek): v1.13 links and sort, v1.14 swipe, v1.15 a scorer that learns, v1.16 quiet signals, exploring surprises and stricter clusters, v1.17 a richer week note.
- [x] v1.13, 2026-11-26: links readable in dark mode; the Newest sort is remembered. Release https://github.com/jacekkotowski/apk-news-reader/releases/tag/v1.13. Not tested on a phone.
- [x] v1.14, 2026-11-26: swipe a Feed card right to keep, left for less, with Undo; Hide and Less like this removed from the menu. Release https://github.com/jacekkotowski/apk-news-reader/releases/tag/v1.14. The feel of the swipe was not tested on a phone.
- [x] v1.15, 2026-11-26: a scorer that learns (`Learner`): sources and words from what you keep and swipe left, fading over 60 days, capped, with the reason on the card. Not tested on a phone.
- [x] v1.16, 2026-11-26: quiet signals (cards scrolled past, articles read for 30 s), surprises from sources the app knows little about, same-story grouping needs rare shared words. Database version 15. Not released on its own (Jacek asked to release only 1.17); its CI on de3d8ba was green.
- [x] v1.17, 2026-11-27: week note with threads, Obsidian links and tags, To remember, a closing line, and the Monday "What did you learn last week?" card. Database version 16. Not tested on a phone.

## 8. Open problems

See prompts/run-report.md. In short: nothing was tested on a real phone; Reddit and Semantic Scholar could not be checked for real (HTTP 404 and 429 from the build computer); the Stash folder export, share sheet and text selection were not exercised in the emulator; steps 6 to 8 are not started.

Added after steps 6 to 8: the curator's scheduled run has not fired yet; page watch, recommendations, highlights, share and the export folder were not used on a phone.

Added after v1.0: Gemini was called live only from the PC (answers recorded as test fixtures); Make notes and the learning path were never pressed in the app on a real phone. Free-tier limits and terms can change (the YouTube feature is a preview). The key Jacek pasted in chat is in the private keys repo (apk-news-reader/gemini.env); it is also in this session's transcript, so he may want to make a new key after testing.


## 9. Future plans (ideas for a new session, not commitments)

How to work: plan first, ask Jacek the questions that matter, wait for his yes, then build in small releases (his rule, 2026-11-23). He wants fewer buttons, not more; propose the smallest version. Nothing has been tested on a real phone yet, so the first job of the next session is to read what he found on the phone.

**First, in this order**
1. Collect his corrections from testing v1.6 (install, then the "Try on the phone" lists of v1.3, v1.4.2, v1.5.1 and v1.6), fix them in one small release.
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

**Decided 2026-11-24 (Jacek):** move recall into the Feed, add "Builds on" lines, add quiet-feed counts with a Mute suggestion, and an automatic local week note. Dropped: a concepts table, rabbit-hole searches, a typed-relation graph, a graph view, Gemini in the week note. These four come before v1.6 Listen to Anything.
