## Stash Reader 1.14: swipe to keep or to clear

**Swipe a card in the Feed**
- **Right (green)**: the card is **kept** and goes to the Stash.
- **Left (red)**: **less like this**. The card goes, and similar ones are ranked lower. On a surprise card, surprises from that source also become rarer.
- A swipe counts when the finger moves at least 30% of the card width, and clearly sideways (twice as far sideways as up or down). Anything else scrolls the page as before.
- **Undo** is offered for 4 seconds. The note file of a kept card is written only after that, so an undone swipe leaves no file. Leaving the app, changing the tab or swiping again ends the Undo window.
- A card with **+N sources on this story** takes the other copies with it. Swiping it right keeps **all of them**.
- Recall cards and open cards do not swipe. A card you already kept can only be swiped right, which just clears it from the Feed.

**Fewer buttons**
- **Hide** and **Less like this** are gone from the three-dots menu. **Keep** stays in the opened card.
- A hint, "Swipe right to keep, left for less", is shown until your first swipe (or until you tap it).

**Try on the phone**
1. Scroll the Feed up and down as usual: no card should move sideways.
2. Swipe one card right and one left. Check the colours, and that Undo brings the card back.
3. Swipe right, wait a few seconds, then look in the Stash and in your notes folder.

**Known limits**
- How the swipe **feels** (distance, colours, no accidental swipes while scrolling) has not been tested on a real phone. The PC tests cover the rules with simulated finger movements, and the emulator test makes one swipe to see that the app does not crash.
- There is no longer a way to hide a card without teaching the app something: swipe it left, or leave it, and it expires after 30 days.
- If the phone kills the app during the 4 seconds without warning, a kept card has no note file until you change it or press Export all.
- Earlier limits still apply.
