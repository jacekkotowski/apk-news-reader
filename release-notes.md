## Stash Reader 1.10: "Builds on"

**Builds on**
- A card in the Feed can show one line: **Builds on: <title of something you kept>**. When you open an item, up to **3** such titles are listed at its end under "Builds on". Tap one to read that kept item inside Stash.
- It is worked out on the phone, with no Gemini and no network. The app takes about eight of the item's rarest key words (from its title, your highlights and the start of its text), looks for them in what you kept, and links only when **at least two** of those rare words are shared and their combined rarity is high enough. If nothing qualifies, nothing is shown: no filler.
- It is computed only for the cards on screen, one at a time, and kept until the next refresh. It never points an item at itself.

**Try on the phone**
1. Keep two or three articles on the same subject (for example two about Parquet). Refresh, and open the Feed: a new article on that subject should show "Builds on: ..." under its text.
2. Open that card: at its end, "Builds on" lists up to 3 kept items. Tap one: it opens in the reader.
3. A card about something you have never kept anything on shows no such line.

**Known limits**
- Nothing here has been tested on a real phone. The PC tests cover the word choice, the scoring rule and these cases: a related pair is found, an unrelated item is not, no self-match, an empty Stash returns nothing, and generic words alone do not link. The database query itself (SQLite FTS4) was not run on a PC or in the emulator.
- With fewer than 2 other kept items nothing is shown. Words are matched by their start ("model" matches "models"), not by meaning, so a related piece in other words is not found.
- If a phone lacks FTS4, the line simply never appears.
- Earlier limits still apply.
