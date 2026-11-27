## Stash Reader 1.19: notes on keep, and Learn fast

**Notes on their own after a keep**
- When you keep something (a swipe right once Undo has passed, Keep, a highlight, a shared link), the app asks Gemini for short notes in the background, with one recall question.
- One request a minute, at most 30 notes a day. When Gemini says its free limit is reached, notes stop until the next day. Podcasts are left out, and so is anything that already has notes.
- A short article is loaded from its page first, as Make notes does. Make notes stays on the three dots for items kept before 1.19, and still asks for three questions.
- New recall cards from notes are spread out: at most 5 of them become due on one day, the rest wait for later days.
- Sources, Settings shows one line: "Gemini today: N notes, M path searches".

**Learn fast: a learning path for any topic**
- In **Topics**, tap a topic, then **Make a learning path for ...**. It works for a topic with nothing kept yet: add the topic (for example Meshtastic), tap it, press the button.
- Gemini plans 5 to 8 steps from zero to useful, from free material: YouTube first, then free courses, then official documentation. Things you already kept on the topic are reused.
- **Every link is checked before it is shown.** Videos through YouTube's own oEmbed answer (real title and channel), pages by loading them and reading their title. Dead links and links whose title does not fit are left out.
- Gemini without Google Search makes up most YouTube addresses (in a test on 27 November all 16 of them were fake), so the app also looks for each step's video on the search page of YouTube channels that it checked. A step with nothing checked shows a "Search YouTube" link instead.
- The path is a row in **Sources** ("Learning path: topic"). Its steps are normal cards: open, keep, swipe, notes, recall, week note and Builds on all work. They are not counted as new, do not expire while the path is on, and do not appear in the Feed list.
- In the plain Feed, the next step of the path you used last appears once, after about 6 articles, labelled "Next step in ...". Swipe right keeps it; swipe left skips that resource and does not teach the Feed that you dislike the topic.
- The path view in Topics shows the steps in order, with a tick for each kept item (tap the circle to tick it). Under the path, channels that appear twice or more have an **Add channel** button.
- Making a path again archives the old one: kept items stay in the Stash, and the old .md note stays in your folder (path notes now have the date in their name).

**Also**
- Pages that answer with a permanent redirect (HTTP 308, used by some documentation sites) now load.

**Try on the phone**
1. Keep two articles and one video, wait a few minutes, and look for "AI notes" on them and for "Gemini today" in Sources.
2. Add the topic "Meshtastic" in Topics, tap it, and make a learning path. Open a step, keep it, and look at the ticks.
3. Scroll the Feed: after about 6 articles there should be "Next step in Meshtastic".

**Known limits**
- Not tested on a real phone. The new screens were looked at in a desktop browser with stand-in data.
- **Google Search inside Gemini is not in the free tier** of the Gemini 3 models (Google's price list, and a live test on 27 November that answered HTTP 429). The app tries it, and after that answer asks without it for a week. With a paid key the app would get real links from Google Search; that path was only tested with a hand-written answer.
- Without Google Search, many steps end with documentation pages and fewer videos than planned. A video is taken from a channel's search page only when its title has the topic and words of the step, so some steps show only the "Search YouTube" link.
- Reading a channel's search page depends on how YouTube builds that page today (both the desktop and the mobile page are read); if YouTube changes it, steps get fewer videos, never wrong ones.
- Gemini's free tier, its daily limits and the search grounding rules can change at any time.
- Notes made on their own run while the app is open (or in memory); a keep made just before closing the app is noted at the next start.
- The database moves to version 18 the first time 1.19 starts. There is no way back to an older version without reinstalling.
- Earlier limits still apply.
