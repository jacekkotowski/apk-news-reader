## Stash Reader 1.6: listen to anything

**Read aloud**
- Articles have a **▶ Play** button in the reader. It reads the title, then the text, with the phone's own text-to-speech voice. If only the feed summary is available, it says so first ("This is only the summary from the feed, not the full article") and the button is in the ⋯ row as "Read the summary aloud".
- It keeps going with the **screen off**, as a foreground service with a media notification (pause, next) and lock-screen controls. It pauses for a call or other audio, and when headphones are unplugged.
- A small player bar appears above the tabs while it plays: title and sentence number, pause or play, next, stop.
- When an article ends, it goes on with the next one in the **play queue**.

**Play queue**
- In the Stash, **▶ Play queue (N articles)** plays your kept articles that you have not heard to the end, newest kept first. Videos and podcasts are not in it.
- The place is remembered **per article, by sentence**: press Play on an article you stopped halfway and it goes on from that sentence. An article heard to the end starts from the top next time.

**Speed and language**
- Sources, Settings: **Reading aloud speed** (0.8x to 1.8x). A change applies at once.
- Text that looks Polish is read with the Polish voice, other text with the phone's language. If that voice is not installed, you get a message and the English one is used.

**Try on the phone**
1. Open an article card with full text and press ▶ Play. You should hear the title, then the text. Lock the screen: it should go on, and the notification should have Pause and Next.
2. Pause, close the app, come back and press Play on the same article: it should go on from where it stopped.
3. Stash, ▶ Play queue: it should move on to the next article by itself when one ends.
4. Sources, Settings: change the speed while it is reading.
5. On Android 13 or newer the first Play asks for the notification permission; reading aloud works if you say no, but the notification will not show.

**Known limits**
- Nothing here has been tested on a real phone, and nothing was heard: the emulator test only checks that the app still starts. The service, the notification, audio focus and the speech engine were not run. The PC tests cover the text-to-sentences cutting, the language guess, the speed choices and where playback restarts.
- The voice is whatever the phone has: quality, languages and speed limits depend on it.
- Position is by sentence and is saved when a sentence starts, so after a pause it repeats the sentence that was being read.
- Earlier limits still apply.
