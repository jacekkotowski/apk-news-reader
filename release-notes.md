## Stash Reader 1.15: the Feed learns from your swipes

**What changed**
- The app now learns from three things: the **source**, the **words** and the **topics** of what you swipe right (keep) and left (less).
- **Sources count.** Keep a lot from one blog and its new posts rise. Keep swiping one site left and its posts sink, even on subjects it has never covered before.
- **Common words count little.** A word that is everywhere in your Feed (say "data") barely moves anything. A word that is as common in what you keep as in what you swipe left counts for nothing.
- **Old interests fade.** A swipe counts half as much after 60 days, so the Feed follows what you read now.
- **Your topics still lead.** What the app learns is capped, so it cannot push your own topics aside.
- **The "Why this" line says what it learned.** For example "You keep a lot from R-bloggers", "Like items you kept (duckdb, parquet)", or "Lowered: you often swipe away items from X".

**Try on the phone**
1. Swipe a few posts from one source left on different days, then refresh. Its posts should drop, and the "Why this" line should say why.
2. Keep two or three posts from a blog you like and look for "You keep a lot from ...".

**Known limits**
- Not tested on a real phone. The PC tests cover the rules: fading, learning a source, a shared word weighing nothing, and the caps.
- The app needs at least two swipes on a source or a word before it acts on it, once you have three or more swipes on that side.
- Items you skip are not used yet. That comes in 1.16.
- Earlier limits still apply.
