## Stash Reader 0.4: Stash, highlights, notes and Markdown export

**New**
- The **Kept** tab is now **Stash**. Each kept item shows its highlights and your note, and you can filter the Stash by topic (the source's topic plus any of your Topics found in the title and summary).
- **Highlights**: select text in an opened card (long-press a word, drag the handles), then press **Highlight selection** at the bottom. Highlighting also keeps the item. Each highlight takes a one-line note and can be removed. Each kept item has a **My note** box.
- **Share as Markdown** sends one note through the Android share sheet as `text/markdown` (Obsidian, Drive, e-mail, ...).
- **Export folder**: pick a folder once with the system folder picker (the permission is kept). From then on every Keep, highlight and note change writes or rewrites one file, `YYYY-MM-DD title.md` (the day you kept it), in that folder. **Export all** writes the items you already have; **Stop exporting** forgets the folder. Point it at a folder that Obsidian Sync or Syncthing covers and notes arrive in your vault.
- A note has YAML front matter (`title`, `source`, `url`, `date`, `kept`, `topics`), then the highlights as `>` quotes (with their notes), then `## My note`.

**Try on the phone:** open an article, press Read full article, select a sentence, Highlight selection, add a note, then Share as Markdown to Obsidian. Then in the Stash tab choose an export folder and keep another item; look for the file.

**Known limits**
- Nothing here has been tested on a real phone. The Markdown text is tested on the PC. The folder picker, the write into the folder, the share sheet and text selection could not be driven in the emulator, so they are untested.
- Removing an item from the Stash does not delete its file in the export folder.
- Two kept items with the same title kept on the same day would write the same file name; the later one replaces the earlier one.
- Highlights are stored as plain text, not as positions: the same sentence cannot be highlighted twice in one item, and highlights are not painted onto the article.
- Text selection inside a card depends on the phone's WebView; the Highlight selection bar shows only while a selection exists.
