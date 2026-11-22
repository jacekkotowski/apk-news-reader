## Stash Reader 0.5: searches on open services

**New**
- **Search sources**: a source can now be a search instead of a feed address. Six kinds, all free, no account or key: **arXiv** (newest papers matching your words), **Hacker News** (stories with 20+ points), **Hugging Face daily papers** (the day's list; an optional word filter), **Reddit** (`r/rstats` for a subreddit, or any words for a search), **GitHub** (repositories created in the last 90 days, most starred first) and **Semantic Scholar** (papers from this and last year).
- Seven starter searches are added: arXiv "causal inference", Hacker News "claude code" and "duckdb", Hugging Face daily papers, Reddit r/datascience, GitHub "tidymodels", Semantic Scholar "causal machine learning". They show up in the Feed, ranked by your Topics like everything else. Edit them in Sources: remove one and add your own under **Add a search**.
- Each search is asked **at most once every 3 hours** (the items already stored are the cache). A search that failed is not retried for 3 hours; a server that answers HTTP 429 is left alone for the time it asks. One request per search per refresh, with the `StashReader/0.5` user agent.
- Links to `http://` pages (for example arXiv) are opened as `https://`.

**Try on the phone:** open Sources and look at the status of each search. Add a search for a word you care about (for example arXiv "conformal prediction"). Check that items from arXiv, Hacker News or GitHub appear in the Feed with a "Why this" line.

**Known limits**
- Nothing here has been tested on a real phone. The answers of arXiv, Hacker News, Hugging Face and GitHub were saved and tested on the PC; the Reddit and Semantic Scholar answers could not be fetched while building (both answered HTTP 429 to the build computer), so their test files are hand-written from the documented formats. Real answers may differ.
- Reddit answered the build computer with HTTP 404 for r/rstats and HTTP 429 for other subreddits, so the starter uses r/datascience and it may show an error on your phone too. Reddit refuses some networks and Semantic Scholar limits requests that carry no key; in that case the source shows the error and tries again after the wait. Nothing else breaks.
- GitHub allows about 10 anonymous searches a minute; the starter list uses one.
- Seed-paper recommendations ("because you kept X") and page watching are not in this release.
