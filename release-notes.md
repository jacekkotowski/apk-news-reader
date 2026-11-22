## Stash Reader 0.8: YouTube channels and podcasts

**New**
- **YouTube channels as sources.** Every channel has a free feed, so no key and no account are needed. A video card shows the title and the description, and **Watch on YouTube** opens the video in the YouTube app. Reader mode is not offered for videos. In Sources, paste a channel address such as `youtube.com/@statquest` and the app finds the feed itself, like it does for any site.
- **Podcasts as sources.** An episode card shows the show notes, and **Listen** opens the episode in the audio player on your phone (your podcast app, or whatever answers). The app has no player of its own, on purpose: your player already remembers the position, speed and background play.
- **Starter list:** channels StatQuest, 3Blue1Brown, Two Minute Papers, Andrej Karpathy, Anthropic; podcasts Data Skeptic and Practical AI. Latent Space (already a source) now gets a Listen button. Remove any you do not want in Sources.
- Cards of videos and episodes say "video" or "podcast" instead of a reading time. Keep, Done and Less like this work on them like on any card, and the Markdown export works too (it links the video or episode page).

**Try on the phone**
1. Refresh, scroll to a StatQuest or 3Blue1Brown card, open it, press **Watch on YouTube**: the YouTube app should open on that video.
2. Open a Data Skeptic or Practical AI card, press **Listen**: a player should start the episode. If your phone asks which app to use, choose one.
3. In Sources, add `youtube.com/@` plus a channel you like. Tell me if it fails.

**Known limits**
- Nothing here has been tested on a real phone. On the PC the parser reads a saved YouTube feed and a saved podcast feed, and the emulator only shows that the app starts. Whether the YouTube app and the audio player open on your phone is not yet known.
- Podcast episodes that were already in the Feed before this update get their Listen button after the next refresh.
- Pasting a channel address may fail where YouTube shows a consent page first (it did not on the build computer). Then use the feed address `youtube.com/feeds/videos.xml?channel_id=...`.
- No transcripts, no thumbnails and no playback inside the app.
- Earlier limits still apply: text selection, the share sheet, the export folder, page watch and recommendations are untested on a phone.
