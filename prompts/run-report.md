# Run report: Stash Reader, autonomous run of 2026-11-22

**Download (always the latest release):** https://github.com/jacekkotowski/apk-news-reader/releases/latest/download/StashReader.apk
**Latest release tag:** `v1.0` (steps 6 to 8 were added later the same day at Jacek's request, see "Second part" below). The link was checked with `curl -sIL` and ends in HTTP 200; the file is about 87 KB.

Steps 0 to 5 are done (first part). Steps 6 to 8 were done in a second part after Jacek said "do 6 through 8". Every step has a GitHub Release whose build was green in CI (PC tests, signed build, emulator smoke test).

| Step | Result | Release | CI run of the commit that was tagged |
|---|---|---|---|
| 0 Skeleton, signed CI, smoke test | done | [v0.0](https://github.com/jacekkotowski/apk-news-reader/releases/tag/v0.0) | [45039572117](https://github.com/jacekkotowski/apk-news-reader/actions/runs/45039572117) |
| 1 Feed, cards, Keep/Done, Sources | done | [v0.1](https://github.com/jacekkotowski/apk-news-reader/releases/tag/v0.1) | [45041567103](https://github.com/jacekkotowski/apk-news-reader/actions/runs/45041567103) |
| 2 Reader mode, offline cache, add by page | done | [v0.2](https://github.com/jacekkotowski/apk-news-reader/releases/tag/v0.2) | [45042839453](https://github.com/jacekkotowski/apk-news-reader/actions/runs/45042839453) |
| 3 Topics, ranking, why-this, background refresh | done | [v0.3](https://github.com/jacekkotowski/apk-news-reader/releases/tag/v0.3) | [45044659198](https://github.com/jacekkotowski/apk-news-reader/actions/runs/45044659198) |
| 4 Stash, highlights, notes, Markdown export | done in code; the picker, share sheet, selection and file writing are untested | [v0.4](https://github.com/jacekkotowski/apk-news-reader/releases/tag/v0.4) | [45047248685](https://github.com/jacekkotowski/apk-news-reader/actions/runs/45047248685) |
| 5 Search sources | done; Reddit and Semantic Scholar answers could not be tested for real | [v0.5](https://github.com/jacekkotowski/apk-news-reader/releases/tag/v0.5) | [45048719238](https://github.com/jacekkotowski/apk-news-reader/actions/runs/45048719238) |

Each tag also started its own CI run (build, smoke, release); all were green. The old `main` is tag `old-attempt`, PR #1 is closed, the branch `verify-and-fix-reader` is deleted. No step needed more than a few CI runs, so the "five attempts" stop rule was never close.

## Tested on the PC (`bash tests/run.sh`, 189 checks)
Sanitiser (scripts, styles, iframes, event attributes, `javascript:` and entity-hidden links, balancing), RSS and Atom parser (dates, duplicates, per-feed cap, DOCTYPE refused, the Tidyverse feed's stray `]]>` repaired), feed autodiscovery (`<link rel=alternate>`, common paths, input clean-up), page character sets, the ranker (BM25 on topics, kept and "less like this" profiles, freshness, "why" sentences), the Markdown note and file name, the JSON reader, and the six search-source parsers (fixtures saved from real arXiv, Hacker News, Hugging Face and GitHub answers; hand-written Reddit and Semantic Scholar fixtures). No test touches the network. `starter.json` is checked for valid, unique https addresses.

## What the emulator smoke test showed
Android 34 emulator in CI, one run per commit. It installs the APK, starts it, waits 30 s, takes screenshots, and fails on `FATAL EXCEPTION` or a missing process. I looked at the screenshots of every run. In the last run (v0.5): the Feed shows ranked cards with "Why this" lines; earlier runs showed the first card opening, **reader mode succeeding on a real Tidyverse article** ("Full article, reader mode (saved on this phone)"), Keep moving it into the Stash with topic chips, and the Topics and Sources tabs. Towards Data Science answers the download with HTTP 403, and the card then fell back to "Excerpt" as designed. The feed held 673 items after the first refresh; 3 sources showed errors in the last run (Reddit, Semantic Scholar, and a one-off timeout from the Hacker News feed address).

**Nothing was tested on a real phone.** The emulator did not exercise: text selection and the Highlight selection bar, notes, the share sheet, writing into an export folder (the folder picker opened once by accident), background refresh firing, or low-end phones. In the emulator the folder picker did open, so that part is wired.

## Starter feeds
Kept (20): Tidyverse blog, Julia Silge, R-bloggers, SQLBI, Power BI blog (Microsoft), RADACAD, Data Goblins, Simon Willison, Hugging Face blog, Sebastian Raschka, Interconnects, Latent Space, Towards Data Science, Eugene Yan, FlowingData, Prokulski Science, Simon Willison on Claude Code, Claude Code releases, Hacker News "Claude Code", Claude blog.
Dropped after checking with curl: `tidymodels.org/start/index.xml` (404), `posit.co/blog/feed/` (404), `blog.rstudio.com/index.xml` (web page), `anthropic.com/rss.xml` (404), `sqlbi.com/blog/feed/` (duplicate). Left out to stay near 20: Machine Learning Mastery, KDnuggets, O'Reilly Radar.
Starter searches (7): arXiv "causal inference", Hacker News "claude code" and "duckdb", Hugging Face daily papers, Reddit r/datascience, GitHub "tidymodels", Semantic Scholar "causal machine learning".

## Open problems
- Reddit answered the build computer with 404 for r/rstats and 429 for other subreddits; Semantic Scholar answered 429. Their parsers rest on hand-written fixtures. Check the status of both in Sources on the phone.
- The Tidyverse blog feed is over 5 MB (the size limit is now 16 MB) and is not valid XML in one place; the parser repairs it. Towards Data Science blocks the page download (HTTP 403), so reader mode shows its excerpt.
- Removing an item from the Stash does not delete its exported file. Two kept items with the same title kept on the same day share a file name.
- Highlights are plain text, not positions in the article; they are not painted onto the page.
- The smoke test cannot drive text selection or the system pickers.
- Not done on purpose: steps 6 to 8 (page watch, seed-paper recommendations, curator job), podcasts, accounts, sync.

## What I would do next
1. Install v0.5 on the phone and go through the "Try on the phone" list in each release note; the ones for 0.4 (highlight, share to Obsidian, export folder) matter most.
2. Fix what the phone shows, in small releases (v0.5.1 ...), before step 6.
3. Then step 6 (page watch for sites without a feed), step 7 (recommendations from kept papers), step 8 (curator).

## Second part: steps 6 to 8 (same day)

| Step | Result | Release | CI run of the tagged commit |
|---|---|---|---|
| 6 Page and sitemap watch, robots.txt | done in code and PC tests; not driven in the emulator | [v0.6](https://github.com/jacekkotowski/apk-news-reader/releases/tag/v0.6) | [45064965080](https://github.com/jacekkotowski/apk-news-reader/actions/runs/45064965080) |
| 7 Recommendations from kept papers | done in code and PC tests; Semantic Scholar answers hand-written | v0.6 (same release as step 6) | same run |
| 8 Daily curator on GitHub Actions | done; one manual run published a feed the app parser reads; the scheduled run has not happened yet | [v0.7](https://github.com/jacekkotowski/apk-news-reader/releases/tag/v0.7) | [45066117861](https://github.com/jacekkotowski/apk-news-reader/actions/runs/45066117861) |

Also in v0.5.1/v0.6 (Jacek's corrections): **Keep** is a single toggle (Keep / "✓ Kept", no Undo); a finishing refresh no longer redraws the Feed under an open card (found in the emulator, where a tap on a card was undone by the redraw).

- **Step 6:** `Robots` (rules, wildcards, longest match), `PageWatch` (article links, page title/description/date, sitemaps), `Watcher` (one listing page per refresh, at most 8 new pages, 1 s apart, daily at most). Link finder also tried on three live pages (Anthropic news, OpenAI news, RStudio blog).
- **Step 7:** `QuerySource.paperId` (arXiv, Hugging Face papers, Semantic Scholar, DOI), `Recommender` (Semantic Scholar recommendations from kept papers, negatives from Less like this, plus recent papers citing the two newest seeds), a ranking bonus and a "why" line.
- **Step 8:** `curator/curate.py` (Python standard library only), `.github/workflows/curator.yml` (daily 05:17 UTC), output on branch `curator-data` as Atom and JSON Feed; the app source is `https://raw.githubusercontent.com/jacekkotowski/apk-news-reader/curator-data/curator.xml`. Served on a branch instead of GitHub Pages so no repository setting was touched. Free: public repo, key-less APIs, no LLM, no Firecrawl.
- **Tests:** 246 Java checks plus 11 Python tests (CI runs both). Smoke run of v0.7: feed with 689 items, Keep toggle shows "✓ Kept" and the card lands in the Stash, Sources shows the Recommendations note.
- **Open problems added:** the curator's scheduled run has not yet fired; Semantic Scholar recommendations depend on an unauthenticated API that often answers 429; the page-watch link finder is a heuristic; page watch, recommendations, highlights, share and the export folder have not been used on a phone.

## Third part: v0.8 to v1.0 (same day, Jacek's requests)

| Release | What | CI run of the tagged commit |
|---|---|---|
| [v0.8](https://github.com/jacekkotowski/apk-news-reader/releases/tag/v0.8) | YouTube channels (Watch on YouTube) and podcasts (Listen, opens the phone's audio player); 7 starter sources | [45074079681](https://github.com/jacekkotowski/apk-news-reader/actions/runs/45074079681) |
| [v0.9](https://github.com/jacekkotowski/apk-news-reader/releases/tag/v0.9) | Share to Stash (Android share target); Recall cards from highlights, 1/3/7/21/60 days, 5 a day | [45081607827](https://github.com/jacekkotowski/apk-news-reader/actions/runs/45081607827) |
| [v1.0](https://github.com/jacekkotowski/apk-news-reader/releases/tag/v1.0) | Gemini (free tier, Jacek's own key): Make notes for videos and articles, questions become Recall cards, learning path per Stash topic, AI notes in the Markdown export | [45082816528](https://github.com/jacekkotowski/apk-news-reader/actions/runs/45082816528) |

- **Tested:** 326 PC checks. The Gemini request builder was run against the live API from the PC (video link, article, path, wrong key) and the answers are saved in `tests/fixtures/gemini-*.json`; the parser is tested on those real answers. The emulator smoke test now also sends a shared link and the Stash shows it. The new buttons (Make notes, Recall, learning path) were driven in a browser pane against a stubbed bridge, not in the app.
- **Not tested:** anything on a real phone; Make notes and the path inside the Android app; the real YouTube and Chrome Share buttons; Listen and Watch opening the other apps; Gemini's free limits over time.
- **Gemini key:** Jacek pasted it in chat and asked to keep it in the private keys repo: `apk-signing-keys/apk-news-reader/gemini.env` (private repo, pushed). It is not in this repo, not in CI, not in any note. He types or pastes it into Sources on the phone. Because it appeared in the chat transcript, a new key after testing would be tidy.
- **Free tier and privacy:** Google's pricing page says free-tier content may be used to improve its products. The app sends only public links, article text, titles and highlights, never the user's own notes, and says so next to the key field.
