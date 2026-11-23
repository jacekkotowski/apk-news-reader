## Stash Reader 1.7.1: the text size works

**Fix**
- **A− smaller / A+ larger did nothing you could see.** It changed the reader text by one point at a time and left everything else alone. It now scales all text in the app (Feed, Stash, notes, buttons) by 10% a step, from 70% to 170%, and shows the number (Sources, Settings, "Text size 100%"). It is remembered.

**Try on the phone**
1. Sources, Settings: press A− a few times. The Feed titles and the reader text should shrink; the number should drop (90%, 80% ...).
2. Close and open the app: the size should stay.

**Known limits**
- Nothing here has been tested on a real phone. At 170% some buttons may wrap onto two lines.
- Earlier limits still apply.
