## Stash Reader 1.16: it also learns from what you skip and what you read

**Quiet signals**
- A card you **look at in the Feed** (most of it on screen for a moment) and never open counts as a very small "less" for **its source**. It teaches no words.
- An article you **read for half a minute** without keeping it counts as a small "more" for its source and its words.
- Both are compared with how often a source appears in your Feed. A blog that fills half your Feed and half of what you scroll past loses almost nothing. A source you skip far more often than it appears slowly sinks.
- A card counts only after a day, so you can still come back to it.

**Surprises that explore**
- The surprise card (every 5th) now prefers sources the app knows little about. The card says "the app knows little about X yet". Swipe it left if you don't want more like it.

**One story, many sources, stricter**
- Two headlines are joined into one story only if they share at least two words that are not common across your Feed. Before, "AI tools for data teams" and "AI model helps data teams" could be merged.

**Try on the phone**
1. Scroll the Feed normally for a few days. Sources you always pass by should drift down.
2. Watch the surprise cards: they should come from sources you rarely see.

**Known limits**
- Not tested on a real phone. The PC tests cover the rules with made-up data, and the emulator test only checks that the app starts and swipes without crashing.
- Only cards shown in the Feed (not Stash, not search) are counted as seen.
- Earlier limits still apply.
