## Stash Reader 1.0.1: YouTube channel addresses

**Fixed**
- **Pasting a YouTube channel address in Sources now works.** For example `youtube.com/@sqlbi`, also with a share tag such as `?si=...`. The app looked for the channel's feed link only in the first 200,000 characters of the page, but YouTube puts it after about 283,000, so it reported no feed. It now looks much further into the page.

**Try on the phone:** in Sources paste `https://youtube.com/@sqlbi?si=GbjplyTSPoH1WfLZ` and press Add. The new source should appear and show its videos after a moment.

**Known limits**
- Nothing here has been tested on a real phone. On the PC the discovery code is tested with a page where the feed link sits at the real position (283,000 characters in). The same address was fetched with `curl` using the app's headers and returned the page with the feed link, but the app's own add-source flow was not run on a phone or in the emulator.
- Everything from 1.0 (Gemini notes, learning paths) and the earlier limits still apply.
