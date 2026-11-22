"""Offline tests for the curator: saved answers from tests/fixtures, no network."""
import datetime as dt
import json
import os
import unittest
import xml.etree.ElementTree as ET

import curate

FIX = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "tests", "fixtures")
NOW = dt.datetime(2026, 11, 22, 12, 0, tzinfo=dt.timezone.utc)


def read(name):
    with open(os.path.join(FIX, name), encoding="utf-8") as f:
        return f.read()


class Parsers(unittest.TestCase):
    def test_hn(self):
        items = curate.parse_hn(json.loads(read("hn.json")))
        self.assertEqual(len(items), 4)
        no_url = [i for i in items if i["title"].startswith("Show HN: Mastra Factory")][0]
        self.assertTrue(no_url["url"].startswith("https://news.ycombinator.com/item?id="))
        self.assertGreater(no_url["points"], 20)

    def test_hf(self):
        items = curate.parse_hf(json.loads(read("hf.json")))
        self.assertEqual(len(items), 3)
        self.assertTrue(items[0]["url"].startswith("https://huggingface.co/papers/2611."))

    def test_github(self):
        items = curate.parse_github(json.loads(read("github.json")))
        self.assertEqual(len(items), 3)
        self.assertTrue(items[0]["url"].startswith("https://github.com/"))

    def test_arxiv(self):
        items = curate.parse_arxiv(read("arxiv.xml"))
        self.assertEqual(len(items), 3)
        self.assertTrue(all(i["url"].startswith("https://arxiv.org/abs/") for i in items))
        self.assertNotIn("\n", items[0]["title"])


class Scoring(unittest.TestCase):
    TOPICS = [{"phrase": "tidymodels", "weight": 3}, {"phrase": "Power BI", "weight": 2}]

    def make(self, title, summary="", days=1, points=0, url=None):
        return curate.item("u:" + title, title, url or "https://e.org/" + title.replace(" ", "-"), summary,
                           NOW - dt.timedelta(days=days), "test", points)

    def test_topic_match_beats_none(self):
        a, b = self.make("Tuning tidymodels"), self.make("Pasta at home")
        sa, ma = curate.score(a, self.TOPICS, NOW)
        sb, mb = curate.score(b, self.TOPICS, NOW)
        self.assertGreater(sa, 0)
        self.assertEqual(ma, ["tidymodels"])
        self.assertEqual(sb, 0)

    def test_phrase_needs_all_words(self):
        self.assertEqual(curate.score(self.make("Power plants"), self.TOPICS, NOW)[0], 0)
        self.assertGreater(curate.score(self.make("Learning Power BI"), self.TOPICS, NOW)[0], 0)

    def test_title_counts_double(self):
        in_title = curate.score(self.make("tidymodels tips"), self.TOPICS, NOW)[0]
        in_text = curate.score(self.make("Tips", "about tidymodels"), self.TOPICS, NOW)[0]
        self.assertGreater(in_title, in_text)

    def test_curate_filters_dedupes_and_orders(self):
        items = [
            self.make("Old tidymodels post", days=40),
            self.make("Fresh tidymodels post", days=1, url="https://e.org/a?utm_source=x"),
            self.make("Fresh tidymodels post again", days=1, url="https://www.e.org/a/"),
            self.make("Unrelated cooking", days=0),
            self.make("Power BI tips", days=2),
        ]
        ranked = curate.curate(items, self.TOPICS, NOW)
        self.assertEqual([i["title"] for i in ranked], ["Fresh tidymodels post", "Power BI tips"])

    def test_cap(self):
        items = [self.make("tidymodels number %d" % n, url="https://e.org/%d" % n) for n in range(100)]
        self.assertEqual(len(curate.curate(items, self.TOPICS, NOW)), curate.MAX_ITEMS)


class Output(unittest.TestCase):
    def test_atom_round_trip(self):
        topics = json.load(open(os.path.join(os.path.dirname(curate.__file__), "topics.json"), encoding="utf-8"))["topics"]
        items = curate.parse_hn(json.loads(read("hn.json"))) + curate.parse_github(json.loads(read("github.json")))
        ranked = curate.curate(items, topics, NOW - dt.timedelta(days=0))
        atom = curate.to_atom(ranked, NOW)
        root = ET.fromstring(atom)
        self.assertTrue(root.tag.endswith("feed"))
        entries = [e for e in root if e.tag.endswith("entry")]
        self.assertEqual(len(entries), len(ranked))
        for e in entries:
            self.assertTrue(any(c.tag.endswith("link") and c.get("href", "").startswith("https://") for c in e))
            self.assertTrue(any(c.tag.endswith("summary") and "Curator:" in (c.text or "") for c in e))
        jf = json.loads(curate.to_json_feed(ranked, NOW))
        self.assertEqual(len(jf["items"]), len(ranked))

    def test_atom_escapes_markup(self):
        it = curate.item("x", "A <b>bold</b> & title", "https://e.org/x", "S <script>x</script>", NOW, "t")
        it.update(score=1.0, matched=["tidymodels"])
        root = ET.fromstring(curate.to_atom([it], NOW))
        titles = [e.text for e in root.iter() if e.tag.endswith("title")]
        self.assertIn("A bold & title", titles)
        summary = [e.text for e in root.iter() if e.tag.endswith("summary")][0]
        self.assertNotIn("<script>", summary)


if __name__ == "__main__":
    unittest.main()
