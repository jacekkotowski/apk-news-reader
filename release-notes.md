## Stash Reader 1.7: an endless Feed

**Changed after your test**
- **The Feed is endless.** It shows 15 cards and loads 15 more when you scroll to the end, best match first, down to 300. The frozen list for the day and "Done for today" are gone; the 5 / 15 / 30 min plan is removed.
- **Time is now a filter.** The three buttons **≤ 5 min / ≤ 15 min / ≤ 30 min** show only items that take at most that long (a video counts 10 min, a podcast 20). Tap the active one again to switch it off. Off by default.
- **Surprises stay.** About every fifth card (the 5th, 10th ...) is a surprise: short, fresh or like what you kept, matching none of your topics, with a "Surprise: why" line. **Less like this** on a surprise also lowers surprises from that source. With a search or a filter on, there are no surprises.
- The line under the buttons says how many cards are shown ("Showing 15 of 120").

**Sources**
- **Reddit 404 fixed.** The app asked for a subreddit's feed at an address Reddit does not serve (`/new/.rss`). It now asks for `/new.rss`. Reddit often answers HTTP 429 (slow down) to apps that are not browsers; the app then waits and tries again later, so a 429 on Reddit is expected sometimes.
- **Semantic Scholar 429 is not fixed.** It shares one free pool between all users who have no key and often says "slow down". The app already waits at least an hour before asking again. A real fix needs a free Semantic Scholar API key (they give one on request); say if you want a place to paste it.

**Try on the phone**
1. Open the Feed and scroll down: more cards should appear by themselves, with "That is everything for now" at the very end.
2. Press ≤ 15 min: only short items remain. Press it again: all items are back.
3. Find a card whose line starts with "Surprise:" (about every fifth one).
4. Sources: the Reddit line should no longer say 404 after a Refresh (it may say it was asked to slow down).

**Known limits**
- Nothing here has been tested on a real phone. The PC tests cover the order with surprises and the time limit rule (446 checks); the scrolling was not run in the emulator.
- The Feed is rebuilt when you open it or refresh, so a card that arrives in the background can move the list while you are at the top.
- Earlier limits still apply.
