## Stash Reader 1.5.1: one card per story, and questions for your Stash

This is release 1.5. The tag v1.5 never became a release: the build failed on a misplaced line in the database code (found by the build, fixed here). Use this one.

**One Story, Many Sources**
- Unread items from the last 3 days that tell the same story become **one card**. They are grouped when they have the same link (tracking parameters, "www" and a trailing slash are ignored) or when their titles share most of their key words. The best-ranked one stays; the card shows **+3 sources on this story**. Tap it to see the other titles and sources, and tap one to read it inside Stash.
- This works in the Feed list for your time, and in the filtered list too. Older items are never grouped.

**Ask My Stash**
- The Stash has a **search box** at the top. Type a word or a question ("how does parquet store columns"). The app looks through the text of everything you kept, your highlights, your notes and your Gemini notes, and shows the best **passages**, each marked [1], [2] ... with the kind (article, highlight, my note, AI notes) and a button that opens the article inside Stash. At most two passages from one article.
- With a Gemini key saved, **Answer with Gemini** writes a short answer **only from those passages** and cites them as [1], [2]. If the passages hold no answer, or there are none, you get "Nothing in your Stash answers this" and no made-up answer. An answer without a citation is not shown, and citations to passages that were not sent are removed.
- Only the passages and your question are sent to Gemini. The first search after this update builds the index of everything you kept; later changes (a new highlight, a note) are added at the next search.

**Try on the phone**
1. Feed, 30 min: look for a card with "+N sources on this story" (it appears only when two sources really cover one story that week). Tap it.
2. Stash, search box: type a word from something you kept, for example "parquet" or "causal". Passages should appear; tap the title button to open the article.
3. Add a Gemini key (Sources, Settings) and press **Answer with Gemini**: the answer should carry [1], [2] marks that match the passages above. Then ask something your Stash does not cover: you should get the "Nothing in your Stash answers this" line.

**Known limits**
- Nothing here has been tested on a real phone. The PC tests cover grouping (on a fixture of 13 titles), the link comparison, passage cutting, the search query, ranking, and the answer request and citation checks; the answer parser was tested on a hand-written reply in Gemini's format, not on a live answer. The full-text index (SQLite FTS4) and the new screens were only started in the emulator, not driven.
- Search finds words that begin like your words ("model" finds "models") but does not understand meaning. A question phrased with other words than the text uses finds nothing.
- Clustering is by words in titles: two different stories with very similar headlines can be merged, and one story told in different words can stay separate.
- Earlier limits still apply.
