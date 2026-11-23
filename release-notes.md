## Stash Reader 1.8.1: Semantic Scholar key

**New**
- **Sources, Settings, "Semantic Scholar (optional)":** paste a free Semantic Scholar API key and press Save. The app then sends it with every Semantic Scholar request (searches, recommendations from your kept papers, citations), which ends the "slow down" (HTTP 429) answers. Saving the key also cancels the earlier waiting time and asks again straight away. **Remove key** goes back to the shared pool.
- The key is sent **only** to `api.semanticscholar.org` (the PC tests check that no other address gets it) and stays on the phone. Get a free key at semanticscholar.org/product/api.

**Try on the phone**
1. Request a key at semanticscholar.org/product/api (they send it by e-mail), paste it in Settings, Save.
2. Sources: the Semantic Scholar lines should refresh and stop saying 429 after a minute.

**Known limits**
- Nothing here has been tested on a real phone, and the key has not been tried against the real service (no key is available to me). If a request is refused with HTTP 401 or 403, the key is wrong or not yet active.
- Reddit may still answer 429; that has no key option. Earlier limits still apply.
