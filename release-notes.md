## Stash Reader 1.8.3: player buttons, no code, English or Polish

**Fixes**
- **The buttons showed codes** like `\u23ed` instead of symbols: the player bar (pause, next, stop), the Back button of the page view and the pencil. They now show ⏸ ⏭ ✕ ‹ ✎.
- **Code is not read aloud.** Code blocks in an article are skipped, and a web address is read as the word "link" instead of letter by letter. (Short code inside a sentence is still read.)
- **Language.** The app picks the voice from the text: Polish when it has Polish letters (ą, ę, ł, ó, ś, ż and so on) in the title or text, otherwise **English**. Before, a text that was not Polish was read in the phone's own language, so on a Polish phone an English article got a Polish voice.

**Try on the phone**
1. Press Play: the bar should show ⏸ ⏭ ✕ symbols.
2. Play an English article that has a code block: you should not hear the code. Play a Polish one: it should use the Polish voice.

**Known limits**
- Nothing here has been tested on a real phone. The PC tests cover the language choice, dropping code blocks and the link rule; the voice itself was not heard.
- One language per article: a Polish text with English quotes is read entirely in Polish, and an English post with a long Polish quote entirely in English. If the Polish or the English voice is not installed on the phone, you get a message (Settings, Text-to-speech).
- Earlier limits still apply.
