## Stash Reader 1.9: recall cards come to the Feed

**Recall in the Feed**
- Recall cards that are due now appear **inside the Feed**, one after about every 4 articles (a short list puts them at the end). Tap **Show answer**, then **Got it** or **Again tomorrow**. The card goes away when you answer and does not come back on the next redraw.
- The old **Recall: N cards** button in the Stash is gone. The schedule is unchanged: at most 5 cards a day, "Got it" waits longer next time, "Again" asks again tomorrow.
- Recall cards show only in the plain Feed, not when a filter, a search or a reading-time chip is on. While a recall answer is open, new items do not redraw the list under you.

**Try on the phone**
1. Keep an item with a highlight and wait a day (its card is due after one day). Open the Feed: a Recall card should sit after the fourth article.
2. Press **Show answer**, then **Got it**: the card disappears and stays gone after a refresh.
3. Scroll down so the Feed loads more: the next due card should come after the eighth article.
4. Open the Stash: there is no Recall button.

**Known limits**
- Nothing here has been tested on a real phone. The PC tests cover where the cards are placed (positions, daily cap, no cards means no change). The Feed with recall cards was not driven in the emulator, only started by the CI smoke test.
- Earlier limits still apply.
