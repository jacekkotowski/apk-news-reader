# Implemented scope and limitations

- SQLiteOpenHelper: persistent sources and articles, dedupe by ID, saved/read flags. No Room dependency.
- RSS/Atom and podcast enclosures: Android XML pull parser.
- AutoSource: HTML feed autodiscovery and repeated article cards via Jsoup. Does not yet parse sitemaps, pagination, or JavaScript-only listings. Test source: https://blog.prokulski.science/.
- YouTube: channel-ID URL (youtube.com/channel/UC...) resolved to YouTube's published RSS feed. Handles, playlists and arbitrary channel pages not yet supported.
- Podcast: Android MediaPlayer for basic in-app episode playback. No background media session, notifications or seek UI yet.
- Media extraction only where source provides an enclosure or media link. Video opens in YouTube/browser.
- Release signing deliberately not configured: public repo; do not read private keys from apk-signing-keys.

## Next agent tasks
1. Run CI and fix any build failures. Add test fixtures and parser tests.
2. Improve HTML article detection, pagination, sitemap fallback, and full-text reading.
3. Add background podcast MediaSessionService, play/pause/seek.
4. Resolve YouTube @handle to channel ID using supported metadata, with graceful fallback.
5. Add OPML, source management, refresh scheduling, image loading and filtering.

Never claim these future items are implemented.

## Build verification
Source changes are committed, but no successful GitHub Actions build or installable APK has been verified. Current audio playback is foreground-only. The HTML detector is heuristic and requires site fixtures. No signed release exists yet.
