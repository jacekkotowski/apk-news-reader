## Stash Reader 1.0: Gemini notes and learning paths

**New** (all optional: nothing changes until you save a key)
- **Make notes.** On a video or article card, press **Make notes**. Gemini reads the video (through its YouTube link) or the article text and writes study notes in Markdown: 5 key ideas, up to 3 quotes (with timestamps for videos), why it matters for your Topics, and what to try next. They show under the card, labelled **AI notes (Gemini), not the article**, and start with a line saying what they were based on (the video, the saved article, the page text, or only the feed excerpt). They are asked for once and saved; they also go into the Markdown note when you export or share it.
- **Questions become Recall cards.** The 3 questions Gemini writes for each item join your Recall cards, so what you read is asked again after 1, 3, 7, 21 and 60 days.
- **Learning path.** In the Stash, pick a topic chip and press **Make a learning path**. Gemini orders what you kept on that topic (basics, core, advanced), says why, and lists the gaps. Under it are buttons that turn a gap into a search source with one press ("Fill the gaps"). The path is also written to your export folder as `Path - <topic>.md`.
- **Your key.** Sources has a new Gemini block: paste a free key from aistudio.google.com, press **Save**, then **Test key**. The key stays on this phone only.

**What is sent to Google.** Only public things: a video link, article text, and for a path the titles, sources and passages you highlighted. Never your own notes. On Google's free tier, Google may use what you send to improve its products (their pricing page says so). The app uses the model `gemini-3.5-flash-lite`, one request at a time, and never repeats a request by itself. If the free limit is reached it says so and you try later.

**Try on the phone**
1. Sources, Gemini block: paste your key, Save, Test key. It should say "Gemini accepted the key".
2. Open a video card from a channel, press Make notes. Then try an article card.
3. Keep a few items on one topic, open the Stash, pick the topic chip, press Make a learning path.
4. Next day, check that the questions show up in Recall.

**Known limits**
- Nothing here has been tested on a real phone. On the PC, the request builder and the answer parser run against answers recorded from the live Gemini API (a video, a path, a wrong key); a quota error is hand-written. The emulator does not use a key, so Make notes and the path were not pressed in the app.
- Videos must be public. Google limits free YouTube video to 8 hours a day and calls the feature a preview, so limits and terms can change.
- No notes for podcast episodes (audio is not sent). If a page cannot be read, the notes say they came from the feed excerpt only.
- Gemini can be wrong; the notes are never shown as the article.
- Earlier limits still apply: text selection, the share sheet, the export folder, page watch, recommendations, podcasts and videos are untested on a phone.
