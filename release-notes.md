## Stash Reader 1.4.2: a Feed that fits your time, and a few surprises

This is release 1.4. The tags v1.4 and v1.4.1 never became releases: the emulator test failed, first because the day's list was made before the sources had loaded (fixed in the app), then because the test tapped the first card at a height that now falls on the new time buttons (fixed in the test). Use this one.

**Fit My Time**
- The Feed bar now has **5 min / 15 min / 30 min** instead of "Top 15". Pick the time you have and the Feed shows a list that adds up to it: about "12 of 15 min, 5 items". Reading times are the app's estimate (a video counts 10 min, a podcast episode 20 min).
- With 15 or 30 minutes the list mixes **one longer article** (8 min or more) with short ones. With 5 minutes it is short items only. If nothing is short enough, you get the shortest of the best ones rather than an empty list.
- The list is made once a day and stays the same while you read: new items arriving in the background do not reshuffle it. When you have handled everything on it, the Feed says **Done for today** and stops. Pick a time again to get more.
- The ⋯ filters and the search work as before and show all matches; choosing a time clears them.

**Surprise Me**
- About one slot in five is a **surprise**: a recent or familiar-looking item that matches none of your topics, with a "Surprise: why" line.
- **Less like this** on a surprise also lowers surprises from that source. After three, that source gives none.

**Try on the phone**
1. Open the Feed: press 5, 15 and 30 min. The list should grow, and the line under the buttons should say how many minutes it fills.
2. In the 15 or 30 min list, find a card whose line starts with "Surprise:".
3. Hide or Keep everything on the list: you should see "Done for today". Press 15 min again for more.
4. Press ⋯ and type a word in the search: all matches show, and the time buttons go grey.

**Known limits**
- Nothing here has been tested on a real phone. The PC tests cover the planner (time never exceeded, one long item, surprise rate and rules, the turn-down rule, the fallback). The database part and the new buttons were not run in the emulator beyond starting the app, and the surprise quality rule (score plus length, at least 0.4) is my guess, not tuned on real data.
- A podcast or video card has no real length in the app, so the 10 and 20 minute figures are fixed estimates.
- Earlier limits still apply.
