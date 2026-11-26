## Stash Reader 1.17: a week note you keep, and a Feed that learns from what you skip

This release also contains 1.16, which was not released on its own.

**The week note (written every Monday from what you kept)**
- **Threads.** Items that build on each other are grouped under one heading, named by the words they share, for example "Thread: parquet, group".
- **One short entry per item:** your note, else its first highlight, else one sentence quoted from the article. The note says "Sentence from the article", so it is never mistaken for a summary.
- **Obsidian.** Each item links to its own note file (`[[2026-11-16 Title]]`), and the topics are tags. The week shows up in Obsidian's graph and tag list.
- **To remember.** Three highlights from the week are listed. Their recall cards come back in the Feed now, not on their usual date.
- **A closing line**, for example "You kept 9 and looked at 212 other cards in the Feed. Quietest sources: A, B."
- **Your own words.** On the Monday after a week with something kept, the top of the Feed asks "What did you learn last week?". Your answer goes at the top of that week's note, and the file is written again. **Skip** closes the question for that week. It disappears after 7 days.

**What you skip and what you read (from 1.16)**
- A card you **look at in the Feed** (most of it on screen for a moment) and never open counts as a very small "less" for its source only. It counts after a day, so you can still come back to it.
- An article you **read for half a minute** without keeping it counts as a small "more".
- Both are compared with how often a source appears in your Feed, so a big source is not punished for being big.
- **Surprise cards** prefer sources the app knows little about, and say so.
- **One story, many sources** joins two headlines only if they share at least two words that are not common across your Feed.

**Try on the phone**
1. Next Monday, look at the top of the Feed for "What did you learn last week?". Answer it, then open the week note in Obsidian.
2. In Obsidian, click a `[[...]]` link in the week note: it should open that item's note.

**Known limits**
- Not tested on a real phone. The PC tests cover the note's text, the answer, the tags, the links and the quiet signals with made-up data. The emulator only checks start-up and a swipe.
- The week note is still written once, on the first start of a new week. Only your answer changes it later.
- A note file name holding `[`, `]`, `#`, `^` or `|` gets no `[[link]]`, because Obsidian cannot open such a link.
- The database moves to version 16 the first time 1.17 starts. There is no way back to an older version without reinstalling.
- Earlier limits still apply.
