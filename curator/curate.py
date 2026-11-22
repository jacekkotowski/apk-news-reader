#!/usr/bin/env python3
"""Stash Reader curator: runs daily on GitHub Actions, off the phone.

It asks free, key-less APIs (Hacker News, arXiv, Hugging Face daily papers, GitHub) for new items about the
topics in topics.json, scores them with a plain explainable rule, and writes one Atom feed (curator.xml) plus a
JSON Feed (feed.json). The phone adds curator.xml as one ordinary source.

Uses only the Python standard library. No paid service, no key (GitHub's own GITHUB_TOKEN is used when present
to raise the rate limit). Usage: python3 curate.py --out DIR [--offline-fixtures DIR]
"""
import argparse
import datetime as dt
import html
import json
import math
import os
import re
import sys
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

AGENT = "StashReaderCurator/1.0 (+https://github.com/jacekkotowski/apk-news-reader)"
MAX_ITEMS = 60
MAX_AGE_DAYS = 21
HERE = os.path.dirname(os.path.abspath(__file__))


# ---------------------------------------------------------------- text helpers
def tokens(text):
    out = []
    for w in re.findall(r"[a-z0-9]+", (text or "").lower()):
        if len(w) > 3 and w.endswith("s") and not w.endswith("ss"):
            w = w[:-1]
        if len(w) >= 2:
            out.append(w)
    return out


def strip_tags(s):
    return re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]*>", " ", s or ""))).strip()


def norm_url(u):
    u = re.sub(r"^https?://(www\.)?", "", (u or "").strip().lower())
    u = re.sub(r"#.*$", "", u)
    u = re.sub(r"[?&](utm_[a-z]+|fbclid|gclid)=[^&]*", "", u)
    return u.rstrip("/?&")


def parse_date(s):
    if not s:
        return None
    s = s.strip().replace("Z", "+00:00")
    try:
        d = dt.datetime.fromisoformat(s)
    except ValueError:
        return None
    if d.tzinfo is None:
        d = d.replace(tzinfo=dt.timezone.utc)
    return d.astimezone(dt.timezone.utc)


def item(uid, title, url, summary, published, source, points=0):
    return {"uid": uid, "title": strip_tags(title), "url": url, "summary": strip_tags(summary)[:600],
            "published": published, "source": source, "points": points}


# ---------------------------------------------------------------- source parsers (pure, tested offline)
def parse_hn(data):
    out = []
    for h in data.get("hits", []):
        oid = str(h.get("objectID", ""))
        url = h.get("url") or "https://news.ycombinator.com/item?id=" + oid
        pub = dt.datetime.fromtimestamp(h.get("created_at_i", 0), dt.timezone.utc) if h.get("created_at_i") else None
        summary = "%s points and %s comments on Hacker News. %s" % (h.get("points", 0), h.get("num_comments", 0), h.get("story_text") or "")
        out.append(item("hn:" + oid, h.get("title", ""), url, summary, pub, "Hacker News", h.get("points", 0) or 0))
    return out


def parse_hf(data):
    out = []
    for x in data:
        p = x.get("paper") or x
        pid = p.get("id", "")
        title = p.get("title") or x.get("title", "")
        summary = p.get("summary") or x.get("summary", "")
        pub = parse_date(p.get("publishedAt") or x.get("publishedAt"))
        out.append(item("hf:" + pid, title, "https://huggingface.co/papers/" + pid if pid else "", summary, pub,
                        "Hugging Face daily papers", p.get("upvotes", 0) or 0))
    return out


def parse_github(data):
    out = []
    for r in data.get("items", []):
        desc = r.get("description") or ""
        title = r.get("full_name", "") + (": " + desc if desc else "")
        summary = "%s stars%s. %s" % (r.get("stargazers_count", 0), ", " + r["language"] if r.get("language") else "", desc)
        out.append(item("gh:%s" % r.get("id"), title[:140], r.get("html_url", ""), summary, parse_date(r.get("created_at")),
                        "GitHub", r.get("stargazers_count", 0) or 0))
    return out


def parse_arxiv(xml_text):
    ns = {"a": "http://www.w3.org/2005/Atom"}
    out = []
    root = ET.fromstring(xml_text)
    for e in root.findall("a:entry", ns):
        link = ""
        for l in e.findall("a:link", ns):
            if l.get("rel") in (None, "alternate"):
                link = l.get("href", "")
                break
        link = re.sub(r"^http://", "https://", link)
        out.append(item("arxiv:" + (e.findtext("a:id", "", ns) or link), e.findtext("a:title", "", ns), link,
                        e.findtext("a:summary", "", ns), parse_date(e.findtext("a:published", "", ns)), "arXiv"))
    return out


# ---------------------------------------------------------------- scoring
def score(it, topics, now):
    """Explainable score: topic words found (title counts double), a little for popularity, a little for freshness."""
    title_t, all_t = set(tokens(it["title"])), set(tokens(it["title"] + " " + it["summary"]))
    topic_score, matched = 0.0, []
    for t in topics:
        terms = tokens(t["phrase"])
        if terms and all(w in all_t for w in terms):
            topic_score += t.get("weight", 1) * (2.0 if all(w in title_t for w in terms) else 1.0)
            matched.append(t["phrase"])
    if not matched:
        return 0.0, []
    age = max(0.0, (now - it["published"]).total_seconds() / 86400.0) if it["published"] else 30.0
    return topic_score + 0.3 * math.log1p(it.get("points", 0)) + 1.5 / (1 + age / 3.0), matched


def curate(items, topics, now):
    seen, ranked = set(), []
    for it in items:
        key = norm_url(it["url"]) or it["uid"]
        if not it["title"] or not it["url"] or key in seen:
            continue
        if it["published"] and (now - it["published"]).days > MAX_AGE_DAYS:
            continue
        s, matched = score(it, topics, now)
        if s <= 0:
            continue
        seen.add(key)
        it = dict(it, score=s, matched=matched)
        ranked.append(it)
    ranked.sort(key=lambda x: (-x["score"], -(x["published"].timestamp() if x["published"] else 0)))
    return ranked[:MAX_ITEMS]


# ---------------------------------------------------------------- output
def to_atom(items, now):
    ns = "http://www.w3.org/2005/Atom"
    ET.register_namespace("", ns)
    feed = ET.Element("{%s}feed" % ns)
    ET.SubElement(feed, "{%s}title" % ns).text = "Stash Reader curator"
    ET.SubElement(feed, "{%s}id" % ns).text = "urn:stashreader:curator"
    ET.SubElement(feed, "{%s}updated" % ns).text = now.strftime("%Y-%m-%dT%H:%M:%SZ")
    for it in items:
        e = ET.SubElement(feed, "{%s}entry" % ns)
        ET.SubElement(e, "{%s}title" % ns).text = it["title"]
        ET.SubElement(e, "{%s}id" % ns).text = it["uid"]
        ET.SubElement(e, "{%s}link" % ns, {"rel": "alternate", "href": it["url"]})
        pub = it["published"] or now
        ET.SubElement(e, "{%s}published" % ns).text = pub.strftime("%Y-%m-%dT%H:%M:%SZ")
        ET.SubElement(e, "{%s}updated" % ns).text = pub.strftime("%Y-%m-%dT%H:%M:%SZ")
        why = "Curator: %s (%s)." % (", ".join(it["matched"][:3]), it["source"])
        ET.SubElement(e, "{%s}summary" % ns).text = "<p>%s</p><p>%s</p>" % (html.escape(it["summary"]), html.escape(why))
    return ET.tostring(feed, encoding="unicode", xml_declaration=False)


def to_json_feed(items, now):
    return json.dumps({
        "version": "https://jsonfeed.org/version/1.1", "title": "Stash Reader curator",
        "items": [{"id": it["uid"], "url": it["url"], "title": it["title"], "content_text": it["summary"],
                   "date_published": (it["published"] or now).strftime("%Y-%m-%dT%H:%M:%SZ"),
                   "tags": it["matched"], "_source": it["source"], "_score": round(it["score"], 2)} for it in items]
    }, indent=1, ensure_ascii=False)


# ---------------------------------------------------------------- fetching
def get(url, accept="application/json", token=None):
    req = urllib.request.Request(url, headers={"User-Agent": AGENT, "Accept": accept})
    if token:
        req.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(req, timeout=30) as r:
        return r.read().decode("utf-8", "replace")


def requests_for(config, now):
    """The request addresses and their parsers, one per query."""
    q = urllib.parse.quote_plus
    since = (now - dt.timedelta(days=60)).strftime("%Y-%m-%d")
    out = []
    for query in config.get("hn", []):
        out.append(("hn " + query, "https://hn.algolia.com/api/v1/search_by_date?query=%s&tags=story&numericFilters=%s&hitsPerPage=40"
                    % (q(query), urllib.parse.quote("points>=20")), "application/json", lambda t: parse_hn(json.loads(t))))
    for query in config.get("arxiv", []):
        out.append(("arxiv " + query, "https://export.arxiv.org/api/query?search_query=%s&sortBy=submittedDate&sortOrder=descending&max_results=40"
                    % q('all:"%s"' % query), "application/atom+xml", parse_arxiv))
    if config.get("hf", True):
        out.append(("hf daily papers", "https://huggingface.co/api/daily_papers?limit=50", "application/json", lambda t: parse_hf(json.loads(t))))
    for query in config.get("github", []):
        out.append(("github " + query, "https://api.github.com/search/repositories?q=%s&sort=stars&order=desc&per_page=20"
                    % q("%s created:>%s" % (query, since)), "application/vnd.github+json", lambda t: parse_github(json.loads(t))))
    return out


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--config", default=os.path.join(HERE, "topics.json"))
    args = ap.parse_args(argv)
    config = json.load(open(args.config, encoding="utf-8"))
    now = dt.datetime.now(dt.timezone.utc)
    items, ok, failed = [], 0, 0
    token = os.environ.get("GITHUB_TOKEN")
    for name, url, accept, parser in requests_for(config, now):
        try:
            items += parser(get(url, accept, token if "api.github.com" in url else None))
            ok += 1
        except Exception as exc:  # one bad source must not stop the others
            failed += 1
            print("source failed: %s: %s" % (name, exc), file=sys.stderr)
        time.sleep(3 if "arxiv" in url else 1)
    if ok == 0:
        print("every source failed", file=sys.stderr)
        return 1
    ranked = curate(items, config["topics"], now)
    os.makedirs(args.out, exist_ok=True)
    with open(os.path.join(args.out, "curator.xml"), "w", encoding="utf-8") as f:
        f.write('<?xml version="1.0" encoding="UTF-8"?>\n' + to_atom(ranked, now) + "\n")
    with open(os.path.join(args.out, "feed.json"), "w", encoding="utf-8") as f:
        f.write(to_json_feed(ranked, now) + "\n")
    print("%d of %d requests ok, %d items kept of %d seen" % (ok, ok + failed, len(ranked), len(items)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
