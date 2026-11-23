## Stash Reader 1.8: a notebook in the app, and open the .md file

**Notebook**
- The Stash **Notes** chip now lists every kept item that has **anything of yours**: highlights, your note or Gemini notes (before, only Gemini notes). Each entry says what it has ("2 highlights | my note | AI notes"). Learning paths are still there.
- Tap one: it opens as **one rendered note**, the same text as the `.md` file (link to the source, your highlights as quotes, AI notes, your note), in your text size and dark mode. Links open inside Stash.

**Open the .md file**
- On that note, **Open the .md file** opens the real file from Documents/Stash Reader (or your chosen export folder) in an app you pick: Obsidian, a text editor, any app that can open Markdown. If the file is not there yet, the app writes it first.

**Ways to use the files** (nothing to install for the first)
- Files app, Documents, Stash Reader: all notes, one file per kept item.
- Obsidian on the phone: open that folder as a vault.
- To the PC: sync the folder with Syncthing or a cloud-sync app (see the README).

**Try on the phone**
1. Keep an article, highlight a passage, write a note. Stash, chip **Notes**: it should be listed, with "1 highlight | my note".
2. Open it: you should see the same sections as in the file. Press **Open the .md file** and pick an app.

**Known limits**
- Nothing here has been tested on a real phone. The PC tests cover the front-matter removal and the rendering; the file hand-off to another app was not run, and some apps (Obsidian included) may not offer themselves for a file opened this way. If yours does not, use the Files app route above.
- The Notes list shows kept items only (plus any item with Gemini notes). Earlier limits still apply.
