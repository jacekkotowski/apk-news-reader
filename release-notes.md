## Stash Reader 1.8.2: the pencil works on YouTube links

**Fix**
- A YouTube link opened inside Stash (for example from an article) showed an error and the pencil did nothing, because a video page has no article text. Now the page says it is a video, offers **▶ Watch**, and the **pencil** keeps the video in your Stash and asks Gemini to write notes **from the video itself** (key ideas, quotes with mm:ss times, why it matters, what to try). The notes appear in Stash, Notes, and in the `.md` file.
- This is how videos were already handled elsewhere: a YouTube card or a link shared to Stash has **Make notes** under the three dots.

**How Gemini reads the video**
- The app sends Gemini the YouTube address. Gemini watches the video, sound and picture, itself; the automatic captions are not needed and are not what it uses. The video must be **public** (not private, and not age- or region-restricted). Long videos and the free tier's daily limit can make it fail with a short message. Google may use what is sent on the free tier.

**Try on the phone**
1. In a reader text, tap a link to a YouTube video. You should see "This is a YouTube video" and a Watch button.
2. Press ✎ (Gemini key saved in Settings). Wait up to a minute; then Stash, Notes should have the notes.

**Known limits**
- Nothing here has been tested on a real phone. Gemini's video answer was recorded live from a PC in an earlier version (the parser is tested on it); this new route in the page view was not run in the emulator.
- Earlier limits still apply.
