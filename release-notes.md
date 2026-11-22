## Stash Reader 0.7: the daily curator

**New**
- **A curator that runs off the phone.** Once a day (05:17 UTC) a GitHub Actions job asks Hacker News, arXiv, Hugging Face daily papers and GitHub about your topics, keeps what matches, scores it with a plain rule (topic words found, the title counts double, a little for popularity and freshness) and publishes the best 60 items of the last 21 days as one feed. The app has it as a source called **Curator (daily, from the cloud)**; it appears in Sources like any other feed and its items rank with the rest by your Topics. New items show "Curator: topic (service)" in their text.
- **It costs nothing.** The repository is public, so GitHub Actions minutes are free, and the job uses only key-less public APIs. No LLM and no Firecrawl are used, because those would cost money.
- You can change what the curator looks for by editing `curator/topics.json` in the repository and pushing to `main`. Its topics are separate from the Topics on the phone. See `curator/README.md`.
- The result is an Atom feed (`curator.xml`) on the branch `curator-data`, read by the same parser as every other feed, plus a JSON Feed (`feed.json`) for other readers. It is published on a branch, not on GitHub Pages, so no repository setting was changed.

**Try on the phone:** open Sources and find the Curator line (status OK, up to 60 items). Look in the Feed for items whose text ends with "Curator: ...". Check that nothing in it looks off-topic, and tell me what to change in `topics.json`.

**Known limits**
- Nothing here has been tested on a real phone. The curator ran once on GitHub (manual start) and its output was read by the app's own feed parser on the PC; the emulator only shows that the app starts. The scheduled daily run has not yet happened.
- The curator sees only what the four services return for the queries in `topics.json`; it will miss anything else. Semantic Scholar and Reddit are not used there because they refuse most automated requests.
- If a service fails, the others still run; if all fail, the job fails and the last published feed stays in place.
- Earlier limits still apply: text selection, the share sheet, the export folder, page watch and recommendations are untested on a phone.
