# Current implementation

- RSS/Atom feed parsing using Android XmlPullParser.
- Podcast enclosure URLs persisted in SQLite; background playback via Android foreground media service and notification Pause/Resume/Stop controls. No Media3 dependency.
- AutoSource: HTML RSS/Atom discovery, repeated article cards, one explicit next-page link, sitemap fallback.
- YouTube /channel/UC... and @handle pages: attempt to resolve channel ID from canonical URL, metadata or embedded page data; then use channel RSS.
- SQLiteOpenHelper caches sources, articles and read/bookmark state. No Room dependency.
- Vertical Compose cards expand inline; links open in external browser/YouTube.

## Important limitations
- **Build not verified**; no confirmed installable APK or signed release.
- HTML extraction is heuristic and can fail on JavaScript-only or unusual layouts. Sitemap fallback currently yields URL-derived titles, not full article metadata.
- YouTube may change HTML, block requests or omit IDs; handles may fail. No playlist support.
- Podcast service supports playback/pause/resume/stop and notification controls, but no seek, queue, speed, audio focus or Bluetooth media button verification.
- Podcast and YouTube entries are still dependent on accessible publisher metadata.
- No OPML, automated scheduled refresh, article full-text extraction, source removal, or robust offline media.

## Next verification
1. Verify GitHub Actions debug build and fix any compiler/dependency errors.
2. Test https://blog.prokulski.science/ (feed discovery and HTML fallback) and real YouTube @handles.
3. Test podcast background playback with screen locked and Bluetooth headset.
4. Add parser fixtures, lifecycle tests and regression tests.
5. Secure release signing through GitHub Actions secrets without reading or copying private keys into this public repository.

Read AGENTS.md and PLAN.md before changes.
