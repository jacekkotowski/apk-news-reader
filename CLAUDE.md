# Stash Reader

Hand-built Android APK, no Gradle: aapt2, javac, d8, zipalign, apksigner (`build.sh`). WebView UI in `app/assets`, a small `@JavascriptInterface` bridge in `MainActivity`. Package `pl.stashreader`. Interface text is English. Modelled on `C:\Users\jacek\code\apk-cw-news`; leave that repo untouched.

- Plan and steps: `PLAN.md`. One step at a time; each ends with green CI for the exact commit and a GitHub Release.
- No third-party libraries in the app. The only vendored file is `app/assets/Readability.js` (see `THIRD_PARTY.md`).
- Core classes (listed in `tests/core.txt`) must stay free of Android imports so `bash tests/run.sh` can test them on a PC. Tests use saved files in `tests/fixtures/` and never touch the network.
- minSdk 26, Java 11 source level: avoid `List.of`, `Map.of`, `String.isBlank`, `strip`, `repeat`.
- All network calls happen in Java on background threads. Never `fetch()` from the WebView; JS only renders and parses.
- Be polite to servers: User-Agent `StashReader/<version>`, timeouts, honour robots.txt for page watching, at most one listing page per source per refresh, back off on HTTP 429.
- Never show an excerpt as if it were the full article.
- Never commit `*.keystore` or `signing.env`; never print a password. The key is in the private repo `apk-signing-keys/apk-news-reader/`.
- Raise `versionCode` and `versionName` with every release; rewrite `release-notes.md`.
- Don't call anything working unless the CI run for that exact commit is green. State plainly what was not tested on a phone.
- Plain English in docs and commit messages.
- Plan first: for any change beyond a tiny fix, write a short plan, ask Jacek the questions that matter, and wait for his yes before building. He wants fewer buttons, not more. Ideas for later are in `PLAN.md` section 9.
