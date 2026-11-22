# Curator

A daily job that runs on GitHub Actions, not on the phone (`.github/workflows/curator.yml`, 05:17 UTC, also on demand).

- `curate.py` asks free, key-less APIs (Hacker News, arXiv, Hugging Face daily papers, GitHub) for the queries in `topics.json`, keeps what matches your topics, scores it with a plain rule (topic words found, title counts double, a little for popularity and freshness) and keeps the best 60 items of the last 21 days.
- It writes `curator.xml` (Atom, read by the app like any feed) and `feed.json` (JSON Feed, for other readers) to the branch `curator-data`.
- The app has one source for it: `https://raw.githubusercontent.com/jacekkotowski/apk-news-reader/curator-data/curator.xml`.
- Cost: nothing. A public repository has free Actions minutes, and no paid API is used. No LLM and no Firecrawl: adding one would cost money, so it is not done.
- Edit `topics.json` (topics, and the queries per service) and push to `main`; the next run uses it. The topics here are separate from the topics on the phone.
- Tests: `python3 -m unittest discover -s curator` (offline, uses `tests/fixtures`). CI runs them on every push.
