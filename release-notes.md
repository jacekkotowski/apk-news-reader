## Stash Reader 1.11: quiet feeds

**Quiet feeds**
- In **Sources**, every source line now says how much of it you keep, for example **kept 3 of 41 in 30 days**. "In 30 days" means items the app fetched from that source in the last 30 days; "kept" is how many of those you kept.
- A source that gave **at least 20 items and none was kept** shows one **Mute** button. One tap turns the source off (the same as unticking its box). Tick the box again to turn it back on.
- There is no other new button, and nothing is muted for you.

**Try on the phone**
1. Open Sources: sources that have items show "kept K of N in 30 days" under their status.
2. A source you never keep from, with 20 or more items fetched, shows **Mute**. Tap it: the box becomes unticked and the button disappears.
3. Tick the box again: the source is on, and the Mute suggestion returns only while the numbers still qualify.

**Known limits**
- Nothing here has been tested on a real phone. The PC tests cover the counting rule and the 20-item limit; the database counts themselves were not run on a PC.
- Only items still on the phone are counted: unread items older than 30 days are removed, so N can be lower than what a source really published. A new source needs a few weeks of history before it can be suggested.
- Earlier limits still apply.
