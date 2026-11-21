# Roadmap

1. Android Compose project, working CI debug APK, local sample cards.
2. RSS/Atom fetch and parsing, SQLite storage, duplicate handling, OPML.
3. Continuous vertical feed with inline article expansion, offline cache and bookmarks.
4. AutoSource: feed discovery, sitemap and article-list extraction; fixture: https://blog.prokulski.science/.
5. YouTube channel feeds and podcast RSS enclosures; Media3 only when audio player is implemented.
6. Deterministic personalized ranking and optional source-grounded AI explanations.
7. Signed public release, tested on a physical Android device.

Constraints: no mandatory backend, no tracking SDK, minimal dependencies, no embedded DuckDB, no auto-play, no keys in public repository. Never pretend an excerpt is full article text. Honor publisher access controls and crawling limits.
