Paste everything below the line into a new Claude Code session.
Model: Sonnet. Working folder: `C:\Users\jacek\code\apk-news-reader`.
Permission mode: bypass (the run is unattended; nobody answers prompts).

---

You are building "Stash Reader", a personal Android reader for learning: a
Deepstash-like vertical feed whose sources and topics I choose. I am away
from the computer for this run. Work alone, end to end, and leave me a
**downloadable APK online**: a GitHub Release on
`jacekkotowski/apk-news-reader` whose stable link is
`https://github.com/jacekkotowski/apk-news-reader/releases/latest/download/StashReader.apk`.

First read `C:\Users\jacek\code\apk-news-reader\PLAN-stash-reader.md` in
full. It is the agreed plan; section 0 holds my decisions and section 5 the
steps. This prompt tells you how to run them unattended.

Ask the app to keep the computer awake for the run, if that tool exists.

## Permissions I am giving you now (for this run only)
- Replace the contents of the public repo `jacekkotowski/apk-news-reader`:
  tag the old `main` as `old-attempt` and push the tag, close PR #1 with the
  comment "Replaced by a new plan, see PLAN.md; old code is tag
  old-attempt.", delete the remote branch `verify-and-fix-reader`, push to
  `main`, create tags and GitHub Releases there.
- Create a signing key in the private repo
  `C:\Users\jacek\code\apk-signing-keys` (folder `apk-news-reader/`), commit
  and push that repo, and set this repo's Actions secrets `KEYSTORE_B64` and
  `KS_PASS`.
- Download `Readability.js` (Apache-2.0) from the official
  `mozilla/readability` GitHub repo at its latest release tag, and use the
  GitHub Actions marketplace action `reactivecircus/android-emulator-runner`
  in CI only. No other downloads or libraries.
- Nothing else: no other repos, no payments, no paid APIs, no accounts.

## Ground rules
- Model everything on my working app `C:\Users\jacek\code\apk-cw-news`. Read
  its `CLAUDE.md`, `build.sh`, `.github/workflows/android.yml`,
  `tests/run.sh`, `app/AndroidManifest.xml`, `MainActivity.java`,
  `FeedParser.java`, `Db.java`, `Refresher.java`, `RefreshJob.java` and
  `app/assets/index.html` before writing anything. Copy and adapt; leave
  apk-cw-news untouched.
- Same approach: no Gradle, no third-party libraries in the app, Java 11
  source level, minSdk 26 (no `List.of`, `Map.of`, `isBlank`, `strip`,
  `repeat`), WebView UI in `app/assets`, a small `@JavascriptInterface`
  bridge. All network calls in Java on background threads (never `fetch()`
  from the WebView); JS only renders and parses. Core classes stay free of
  Android imports so `tests/run.sh` tests them on the PC (JDK 17 is
  installed; there is no Android SDK locally, so CI builds the APK).
- Run `bash tests/run.sh` locally before every push. After every push, watch
  the run (`gh run watch`) and read failures (`gh run view --log-failed`).
  Never call something working unless the CI run for that exact commit is
  green.
- Secrets: never print, echo or commit a password or keystore. `*.keystore`
  and `signing.env` go in `.gitignore`. Pipe secret values into
  `gh secret set` from files.
- Polite network use in the app: real User-Agent "StashReader/<version>",
  timeouts, honour robots.txt for page-watching, at most one listing page per
  source per refresh, back off on HTTP 429. Never show an excerpt as if it
  were the full article.
- Plain English in docs, commit messages and the report.

## CI you build in step 0 (and keep green)
`.github/workflows/android.yml`, modelled on apk-cw-news, three jobs:
1. **build**: PC tests (`tests/run.sh`), signed build (`build.sh`, output
   `StashReader.apk`), upload artifact `StashReader-apk`.
2. **smoke** (needs build): `reactivecircus/android-emulator-runner`,
   api-level 34, x86_64, `google_apis`, with KVM enabled as that action's
   README describes. Install the APK, launch `pl.stashreader/.MainActivity`,
   wait 30 s, `adb exec-out screencap -p > smoke.png`, dump logcat; fail the
   job if logcat contains `FATAL EXCEPTION` or the app process is gone.
   Upload `smoke.png` and `logcat.txt` as artifact `smoke`. If the emulator
   itself fails to boot (infrastructure, not the app), the job may retry
   once; then record "smoke not run" and do not count it as a pass.
3. **release** (needs build and smoke; only on tags `v*`; `permissions:
   contents: write`): `gh release create "$GITHUB_REF_NAME" StashReader.apk
   --title "Stash Reader $GITHUB_REF_NAME" --notes-file release-notes.md`
   (`release-notes.md` is in the repo, you rewrite it per release).

After each smoke run, download the `smoke` artifact (`gh run download`) and
look at `smoke.png` yourself with the Read tool: check that the feed is
visible and readable, not a blank or error page. Fix what you see.

## The loop: one step at a time
Do the steps of PLAN section 5 in order: **0, 1, 2, 3, 4, 5**. Steps 6–8 are
out of scope for this run. For each step:
1. Implement it, with PC tests for every Android-free class you add or change
   (parsers, sanitiser, ranking, Markdown export, URL autodiscovery), using
   saved fixture files in `tests/fixtures/`. Tests must not hit the network.
2. Raise `versionCode` by 1 and set `versionName` to `0.<step>` (step 0 =
   0.0), rewrite `release-notes.md` (what is new, what to try on the phone,
   known limits), push to `main`, get build + smoke green and the screenshot
   checked.
3. Tag `v0.<step>`, push the tag, confirm the release exists and the stable
   link above downloads the new file (`curl -sIL`, expect 200).
4. Tick the step in `PLAN.md` with the date and the release link; commit.

Step notes beyond the plan text:
- **Step 0**: reset the repo as permitted above. The local folder holds only
  `PLAN-stash-reader.md` and this prompt; move `.git` in from a temp clone (or
  `git init`, add the remote, fetch) so both stay. Rename the plan to
  `PLAN.md`, this file to `prompts/autonomous-run.md`. Write `README.md`
  (what it is, the stable download link, how to build), `CLAUDE.md` (the
  ground rules that stay true), `THIRD_PARTY.md`. Package `pl.stashreader`,
  label "Stash Reader", a simple icon. Key: `stash.keystore` (keytool,
  RSA 2048, validity 10000 days, random password) plus `signing.env` with
  `KS_FILE` and `KS_PASS`, same key names as `apk-cw-news/signing.env`
  (read only its key names, not its values); add the app to that repo's
  README list.
- **Step 1**: about 15 starter RSS/Atom feeds in `app/assets/starter.json`
  across my topics: tidymodels and the R world, Power BI (e.g. SQLBI), AI
  for data science, data-science skills, Claude Code tips, plus
  `https://blog.prokulski.science/index.xml`. Check every URL with curl
  first; keep only valid feeds; list dropped ones in the release notes.
  Refresh on start and by a button, parallel with a small thread pool,
  per-source error stored, never crash on one bad feed, dedupe by URL, at
  most 2000 items. Cards expand inline to the sanitised feed summary (strip
  scripts, styles, iframes, event attributes) with Keep, Done, Open in
  browser. Kept tab. Sources screen (status, enable/disable, add RSS URL).
  Dark mode via `prefers-color-scheme`.
- **Step 2**: Java fetches the page HTML; JS runs Readability.js on it via
  `DOMParser`; store the result for offline reading. If Readability fails,
  show the summary labelled "Excerpt" and the Open in browser button. Add a
  source from any page URL: `<link rel=alternate>` first, then common feed
  paths (`/feed`, `/rss`, `/index.xml`, `/atom.xml`, `/feed.xml`).
- **Step 3**: topics with weights, explainable score (BM25 on title +
  summary, boost from kept items, freshness, minus "Less like this"), a
  "why this" line on each card, background refresh with `RefreshJob`.
- **Step 4**: Stash screen, highlight a passage and add a note, Markdown
  export as in PLAN section 0: Share button (`ACTION_SEND`, text/markdown)
  and an export folder picked once with `ACTION_OPEN_DOCUMENT_TREE`
  (persisted permission), one file per kept item `YYYY-MM-DD title.md`.
- **Step 5**: query sources (arXiv API, HN Algolia, Hugging Face daily
  papers, Reddit `.rss`, GitHub search, Semantic Scholar), each a source
  kind with a query string; cache responses; refresh at most every 3 hours.

## When to stop early
- If a step cannot be made green after **5 CI attempts on the same
  problem**, revert `main` to the last green release, write down what failed
  and what you tried in `PLAN.md` under "Open problems", and stop the run
  there. Never leave `main` red at the end.
- If something needs my decision or a permission not listed above, do not
  guess: skip that part, note it under "Open problems", and continue with
  the rest of the step if it still makes sense.
- Stop after step 5, or earlier if I return and tell you to.

## Final report (last message, and also as `prompts/run-report.md`, committed)
- The stable download link and the latest release tag.
- Per step: done / partly / not done, release link, CI run link.
- What was tested on the PC, what the emulator smoke test showed (attach or
  describe the last screenshot), and plainly: nothing was tested on a real
  phone.
- Starter feeds kept and dropped; open problems; what you would do next.
- Update my memory file
  `C:\Users\jacek\.claude\projects\C--Users-jacek-code-apk-news-reader\memory\stash-reader-plan.md`
  with the outcome, add one line to
  `C:\Users\jacek\Documents\Obsidian Vault\05_Wiedza\log.md` in its
  existing format, and commit `C:\Users\jacek\.claude`.
