## Stash Reader 1.2: a Feed with an end, and notes saved by themselves

**Feed**
- **A short list first.** The Feed shows the 15 best cards ("Top 15 of 292") and a **Show 15 more** button, so it has an end.
- **Filters behind the three dots (⋯).** One panel: a search box, **Type** (articles, videos, podcasts), **Topic**, **Source** and **Sort** (best match or newest), plus **Clear**. The button shows how many filters are on ("⋯ 2"). With a filter or a search on, all matches show (up to 300). Search needs every word you type, anywhere in the title or text. Filters stay while the app runs and reset when it restarts.
- **Cards expire.** A card you neither kept nor hid disappears 30 days after it first arrived in the app. Kept cards never expire. The 30 days count from arrival, not from the article's own date, so an old blog post that is new to you is not wiped when it arrives.

**Notes folder (automatic)**
- On Android 10 and newer, notes are saved by themselves to **Documents/Stash Reader**. You can see them in the Files app, and you can open that folder as an Obsidian vault. No folder needs to be picked. On the first start after this update the app writes the notes of everything you already kept.
- Gemini notes are inside each note file, under "AI notes (Gemini, not the article)". A learning path is written as `Path - <topic>.md` in the same folder.
- In the Stash, **Options** (next to "Export folder") lets you choose another folder, go back to the automatic one, export everything again, or stop exporting. On Android 8 and 9 the automatic folder is not available and the folder picker works as before.

**Settings**
- The Sources screen now ends with one **Settings** box: reading text size, background refresh and the Gemini key. The long grey explanations are shorter.

**Try on the phone**
1. Open the Feed: you should see 15 cards and "Show 15 more". Press ⋯ and try Type, Topic, Source, Sort and the search box.
2. Keep a card, then open the Files app: Documents, Stash Reader. There should be a `.md` file.
3. Sources, scroll to the bottom: Settings.

**Known limits**
- Nothing here has been tested on a real phone. The emulator test checks that the app starts, the ⋯ panel opens, and that a kept note appears as a `.md` file in Documents/Stash Reader. The expiry after 30 days could not be tried in the emulator; the date arithmetic is tested on the PC and the rule is one database line.
- If you reinstall the app, the old note files belong to the old install, so the new install cannot overwrite them and will make copies named like "Title (1).md".
- Removing an item from the Stash does not delete its file (as before).
- Earlier limits still apply.
