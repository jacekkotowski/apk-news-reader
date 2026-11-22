## Stash Reader 0.3: topics and ranking

**New**
- **Topics tab**: words or phrases you care about, each with a weight from 0 (off) to 5. Starter topics: tidymodels, Power BI, DAX, Claude Code, machine learning, LLM, data science, causal inference, duckdb. Add your own, change weights with - and +, remove with x.
- **The Feed is now ordered by a plain score you can read**: how well the title and summary match your topics (BM25, a standard text-search formula, times the weight), plus a boost for words that appear in items you kept, minus a penalty for words in items you marked "Less like this", plus a small freshness term. No hidden model.
- **"Why this"** under every card says what put it there: the topics it matched, "Like items you kept (words)", "New today", or "No topic matched; shown by date".
- **Less like this** button on an opened card: hides it and lowers similar items from then on. **Done** just hides it.
- **Background refresh** (Sources tab): off, every 3, 6, 12 hours or daily; default every 6 hours. Android picks the exact time and needs a network. A server that answers HTTP 429 is left alone for the time it asks (at least a minute, at most a day).

**Try on the phone:** change the weight of Power BI from 3 to 5 and see the Feed order change. Keep two items about one subject and look for "Like items you kept". Press Less like this on something off-topic.

**Known limits**
- Nothing here has been tested on a real phone. The emulator smoke test only shows that the app starts, opens cards and draws the Topics and Sources tabs. I did not wait for a background refresh to fire.
- The score is only as good as the words in the title and feed summary. A feed with empty summaries ranks by title and freshness alone.
- The ranking looks at the newest 600 unread items.
