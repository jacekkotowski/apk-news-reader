## Stash Reader 1.6.1: Play works

**Fix**
- **Play** said "There is no text to read aloud" for every article, and the play queue was always empty. The app took ordinary articles for something that is not an article. Fixed; Play and Play queue now find the text.

**Try on the phone**
1. Open an article card and press ▶ Play: you should hear the title, then the text.
2. Stash, ▶ Play queue: it should list your kept articles and start the newest.

**Known limits**
- Nothing here has been tested on a real phone. The PC tests now check which items count as articles; the speech service itself was still not run anywhere.
- Earlier limits still apply (see the 1.6 notes).
