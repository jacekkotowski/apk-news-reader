## Stash Reader 1.12: a note for every week

**Week note**
- On the **first start in a new week** (weeks run Monday to Sunday, in the phone's time zone) the app writes **one Markdown note about last week**, if you kept anything in it. It is named like `2026-11-16 week.md` (the Monday) and starts with `# Week 2026-11-16 to 2026-11-22`.
- Inside: what you kept, grouped by topic (a link, your highlights as `>` quotes, and your note); a **Builds on** section with pairs of items kept that week where one builds on the other (the same local matching as the "Builds on" line); and the **recall cards you answered "Again tomorrow"** that week.
- It is written on the phone with no Gemini and no button. It appears in the **Notes** view of the Stash ("Week note") and is written to your notes folder like the other notes.
- "Kept that week" is by the day you pressed Keep, not by the article's publish date. A week with nothing kept gets no note. Once a week has its note it is never rewritten.

**Try on the phone**
1. Keep an item or two, highlight a passage, add a note. The note for that week appears the next time the app starts on or after the following Monday. (To see it sooner, set the phone's date to the next Monday and start the app.)
2. Stash, Notes: a "Week note" card should be at the top. Open it and check the groups and the quotes. In your notes folder there should be a matching `... week.md` file.
3. Start the app again the same week: nothing changes and no second note is made.

**Known limits**
- Nothing here has been tested on a real phone. The PC tests cover the week bounds (including the March clock change), an empty week, duplicate titles, Polish text, the same text on a rerun, and that a written week is not due again. The database part (the new `weeks` table, reading kept items and recall answers, the upgrade from the older database) was not run on a PC.
- Only last week is written. If the app is not opened for several weeks, the weeks in between get no note.
- A recall card counts as "Again" if its last answer in that week left it at the first step, so a card answered "Again" and then "Got it" the same week is not listed.
- If the notes folder is not set up when the note is written, the note is in the Notes view only; it is not written to the folder later.
- Earlier limits still apply.
