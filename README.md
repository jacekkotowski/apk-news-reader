# Stash Reader

A personal Android app for learning, in the spirit of Deepstash: one vertical feed of things worth reading, watching and listening to, chosen by you instead of an algorithm. You pick the sources and topics, the app ranks the items and says why, and you keep, highlight and export what you learned. It replaces endless scrolling with a feed that ends and a way to make things stick.

**Download the APK (always the latest release):**
https://github.com/jacekkotowski/apk-news-reader/releases/latest/download/StashReader.apk

Package `pl.stashreader`, Android 8.0 or newer. The interface is in English; sources in any language work. Each release has its own notes, with a "Try on the phone" list and the known limits.

## What it does

- **Feed.** About 28 starter sources (data science, Power BI, tidymodels, AI and Claude Code blogs, YouTube channels, podcasts, a daily curated feed). Add any blog or feed by pasting its address; the app finds the feed itself, also for YouTube channels (`youtube.com/@name`). The Feed is endless: 15 cards, 15 more when you scroll, best match first; about every fifth card is a **surprise** outside your topics, and **≤ 5 / 15 / 30 min** filters by reading time. Items from the last 3 days that tell the same story are one card (**+N sources**). The three dots open filters (type, topic, source, sort, search). Cards you neither keep nor hide expire after 30 days.
- **Search and watch sources.** arXiv, Hacker News, Hugging Face daily papers, Reddit, GitHub and Semantic Scholar searches; pages without a feed (or a sitemap) watched for new articles, with robots.txt honoured; recommendations of new papers from the papers you kept.
- **Ranking you can read.** Items are ordered by your Topics, by what you keep, by "Less like this", and by freshness. Every card says why it is there.
- **Reading.** Cards open in place with the feed text, clearly labelled when it is only a summary. **Read full article** loads the page and shows it in reader mode (Mozilla Readability), saved for offline use. Links inside an article open inside Stash as another reader page; the **✎** button there keeps the page and makes Gemini notes. Videos open in the YouTube app; podcast episodes open in your phone's audio player.
- **Keep and learn.** Keep puts an item in the Stash. Highlight passages, add a note, each kept item is saved as a Markdown note, by itself in `Documents/Stash Reader` on Android 10+ (or into a folder you choose, for example an Obsidian vault), or shared. **Recall** asks about your highlights again after 1, 3, 7, 21 and 60 days, at most 5 a day, so the session ends.
- **Ask My Stash.** The Stash search box searches the text of everything you kept, your highlights and notes (SQLite FTS4) and shows passages with links. With a Gemini key, **Answer with Gemini** answers only from those passages and cites them as [1], [2]; with no passages there is no answer. A **Notes** chip is a notebook: every kept item with highlights, your note or Gemini notes, plus learning paths, rendered like its `.md` file, with a button that opens the real file in Obsidian or an editor. To use the files elsewhere: open `Documents/Stash Reader` as an Obsidian vault, or sync it to a PC with Syncthing or a cloud-sync app.
- **Listen to anything.** **Play** reads an article aloud with the phone's own voice, also with the screen off (foreground service, media notification with pause and next), with a speed setting, the place remembered per sentence, and a **Play queue** of kept articles.
- **Share to Stash.** In YouTube, Chrome or any app, press Share and pick Stash Reader to save a link.
- **Gemini notes and learning paths (optional).** With your own free Gemini key, **Make notes** turns a video (through its YouTube link) or an article into study notes and recall questions, and **Make a learning path** orders what you kept on a topic and lists the gaps. Notes are always labelled AI, never shown as the article. Only public links, article text, titles and your highlighted passages are sent; your own notes never are. On Google's free tier, Google may use what is sent to improve its products.
- **Daily curator.** A GitHub Actions job searches Hacker News, arXiv, Hugging Face and GitHub for your topics once a day and publishes one feed (branch `curator-data`). It costs nothing and uses no LLM.

Everything lives on the phone: no account, no analytics, no server of mine. The only network traffic is fetching the sources you listed and, if you add a key, the Gemini calls you ask for. The app is polite to servers: a real User-Agent, timeouts, at most one listing page per source per refresh, back-off on HTTP 429, robots.txt for watched pages.

## Design in short

- No Gradle and no third-party libraries in the app. `build.sh` calls aapt2, javac (Java 11 source level), d8, zipalign and apksigner. minSdk 26.
- The screen is a WebView showing `app/assets/index.html` (plain JavaScript). A small `@JavascriptInterface` bridge in `MainActivity` does everything that touches the network, the database or Android; the WebView itself may not load anything from the network.
- Classes that must stay free of Android imports are listed in `tests/core.txt` and are tested on the PC with `bash tests/run.sh` (432 checks, plus 12 for the Markdown renderer when node is installed), using saved files in `tests/fixtures/`. The curator has its own Python tests (`python3 -m unittest discover -s curator`). No test touches the network.
- The only vendored file is `app/assets/Readability.js` (Apache-2.0), see [THIRD_PARTY.md](THIRD_PARTY.md).

## Repository map

| Path | What |
|---|---|
| `app/src/pl/stashreader/` | Java sources (database, refresh, parsers, ranker, clusters, passages, export, Gemini, speaker, bridge) |
| `app/assets/` | the UI, the starter sources, Readability.js |
| `tests/` | PC tests, fixtures, the emulator smoke script |
| `curator/` | the daily curator (Python, standard library only) |
| `.github/workflows/` | CI (`android.yml`) and the curator (`curator.yml`) |
| [PLAN.md](PLAN.md) | decisions, steps, progress, open problems and future plans |
| `prompts/` | the run brief and the run report |

## Build

You need a JDK 17 and an Android SDK with `platforms;android-36` and `build-tools;36.0.0`.

```bash
bash tests/run.sh                       # tests on the PC
export ANDROID_SDK_ROOT=/path/to/sdk
bash build.sh                           # needs signing.env, see below
```

`signing.env` holds `KS_FILE` (the keystore file) and `KS_PASS`. Both are never committed. The real key lives in the private repo `apk-signing-keys`, folder `apk-news-reader/`. GitHub Actions gets it from the secrets `KEYSTORE_B64` and `KS_PASS`; without them CI signs with a throwaway key. A Gemini key, if you use one, is typed into the app and stays on the phone.

## CI and releases

`.github/workflows/android.yml` has three jobs: **build** (PC tests, signed APK), **smoke** (installs the APK in an Android 34 emulator, starts it, opens a card, keeps it, sends a shared link, takes screenshots, fails on a crash) and **release** (only for tags `v*`, publishes `StashReader.apk` with the notes in `release-notes.md`). A release is made only from a commit whose CI run was green.

## What was not tested

Nothing has been run on a real phone. The emulator smoke test shows that the app starts, the feed loads, Keep works and a shared link reaches the Stash. Features of v1.3 to v1.6 (page view and ✎, Fit My Time, clusters, search and answers, reading aloud) were not driven in the emulator at all; nothing was heard. Not exercised anywhere: text selection and highlighting, the share sheet out, the expiry after 30 days, writing into a picked export folder (the automatic folder was checked in the emulator), the background refresh job, page watching and recommendations in the app, the YouTube and audio hand-offs, and the Gemini buttons inside the app (Gemini itself was called live from a PC, and the app's parser is tested on those real answers).
