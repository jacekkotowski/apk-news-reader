## Stash Reader 1.3: notes for any page, and a Notes view

**Open links inside Stash**
- A link tapped in a reader text (for example an outside link in a blog post) now opens **inside Stash** as another reader page, not in the phone browser. Links in that page open the same way, and **Back** goes one page back (the phone's Back button too).
- If a page cannot be read (blocked, no article text, no network), you see why and an **Open in browser** button.

**One pencil button: keep and make notes**
- On such a page, the floating **✎** keeps the page in your Stash (with its reader text) and asks Gemini for notes. The `.md` note goes to Documents/Stash Reader as before.
- Without a Gemini key the page is still kept, and the app says where to add the key.

**Notes view**
- In the Stash there is a new **Notes** chip. It lists every item that has Gemini notes, and your learning paths. Tap one to read it laid out properly: headings, lists, bold, quotes and links. It follows your text size and dark mode. Links in a note open inside Stash too.

**Try on the phone**
1. Open a Feed card with a blog post, tap a link inside the text: a Stash page should open. Press the phone's Back.
2. On that page press ✎ (with a Gemini key saved in Sources, Settings). Wait for "Notes saved".
3. Stash, chip **Notes**: the note should be there. Open it and check the layout in light and dark mode, and with A+ larger text.
4. Open a link to a site that blocks apps: you should get a message and Open in browser.

**Known limits**
- Nothing here has been tested on a real phone. The PC tests cover the Markdown converter (12 checks, including that no raw HTML or `javascript:` link gets through); the emulator test only checks that the app starts and still writes notes. The page view, the pencil button and the Back button were not driven in the emulator.
- Only text is shown on these pages, as in reader mode; no scripts run. Plain `http://` links fail because the app only allows secure connections.
- Earlier limits still apply.
