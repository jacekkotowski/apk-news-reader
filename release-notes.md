## Stash Reader 1.20: learning paths with real YouTube videos, no paid services

**What changed**
- A learning path now gets its videos from a **real YouTube search**, one search per step. Before, Gemini suggested videos from memory, and most of those links were fake, so few videos were left.
- Gemini now only plans the steps and suggests free courses and official documentation. Every page it names is still loaded and checked before it is shown.
- The app no longer tries Google Search inside Gemini. That is a paid feature, and nothing in the app uses a paid service now.

**What you do once: a free YouTube key**
1. Open console.cloud.google.com and sign in with your Google account.
2. Make a project (any name).
3. Open "APIs and services", then "Enable APIs", search for "YouTube Data API v3" and enable it.
4. Open "Credentials", then "Create credentials", then "API key". Copy the key (it starts with AIza).
5. In Stash Reader: **Sources**, **Settings**, **YouTube (optional, free)**, paste the key, **Save**.

No card and no payment. The free amount is 100 searches a day; one learning path uses about 6. The key is sent only to YouTube and stays on the phone.

**Without the key**
- Learning paths still work: steps with courses and docs, and a **Search YouTube** link on each step that opens YouTube with the right words.

**Try on the phone**
1. Save the YouTube key, then in **Topics** tap a topic (for example duckdb) and press **Make it again** or **Make a learning path**.
2. Each step should show a video first, then pages. Tap a video to watch it in YouTube.

**Known limits**
- Not tested on a phone, and the YouTube search was not tried with a real key: no key was available on the PC. The answers it reads were written by hand in the form Google documents. If the key is refused, the path is still made and the message says so.
- A video is only taken when its title has the topic and a word of the step, so a step can still end with just the search link.
- YouTube's free daily amount and Gemini's free tier can change.
- Everything from 1.19 still applies (notes on keep, the path as a source, the next step in the Feed).
