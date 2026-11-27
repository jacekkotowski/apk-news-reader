## Stash Reader 1.18: swipe open articles, OpenAlex, a finer learner, lighter video notes

**Swipe an open article**
- An open Feed card swipes like a closed one: right keeps it, left asks for less, with Undo.
- The swipe does not start on a code block, a table, a text field or a button, and not while text is selected. Code and tables still scroll sideways.
- A kept card still cannot be swiped left. A note you are typing is saved before the card leaves.

**OpenAlex, a new place to search**
- In **Sources**, "Add a search or watch a page" now lists **OpenAlex**: papers from the last 365 days, newest first, with abstract (when OpenAlex has one) and citation count.
- It works without a key. A free key from openalex.org gives a larger daily budget: paste it under **Settings, OpenAlex (optional)**. It is sent only to OpenAlex and stays on the phone.
- Records dated in the future are left out. A live search showed that without this limit the whole first page was dated ahead, up to a year.

**A finer learner**
- The Feed now also learns from the **author** (when the feed names one) and from an article's **length** (under 4 minutes, 4 to 12, over 12). So it can tell a good article from a poor one on the same subject from the same source.
- A card may say "You keep a lot by ..." or "Lowered: you often swipe away items by ...".

**Lighter video notes**
- **Make notes** on a video asks Gemini to read it at low resolution and to take the notes mainly from what is said. This uses fewer tokens of your free limit.

**Try on the phone**
1. Open a card with a long article, swipe it right, then press Undo. Try to scroll a code block sideways, and select some text.
2. Add an OpenAlex search in Sources, for example "causal inference". Look at what arrives.
3. Make notes for a video and check that they are still good.

**Known limits**
- Not tested on a real phone. The swipe rules are tested with stand-in page elements, not by touch.
- OpenAlex was asked live from a PC, not from the app. It searches titles, abstracts and full texts, newest first, so some results are only loosely related. Many of the newest records have no abstract.
- The new video setting was not sent to Gemini; only the request text is tested.
- Authors come from RSS and Atom feeds (blogs, arXiv, Reddit, YouTube). Other searches name none. Items already on the phone get an author only if their feed still lists them.
- A card's length comes from the text the app has: an item that arrives as a summary counts as short until the full article is loaded. Videos and podcasts have no length for the learner.
- The database moves to version 17 the first time 1.18 starts. There is no way back to an older version without reinstalling.
- Earlier limits still apply.
