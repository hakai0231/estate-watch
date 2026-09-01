# -*- coding: utf-8 -*-
"""부동산 뉴스 후보 수집 → data/news_candidates.json

이 스크립트는 '기사 목록'만 긁어온다.
5개를 고르고 본문을 요약하는 일은 Claude가 한다 (docs/DAILY_BRIEF.md 참고).

사용법:  python scripts/fetch_news.py
"""
from __future__ import annotations

import html
import json
import pathlib
import re
import sys
from datetime import datetime

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import apply_source as src  # noqa: E402

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "data" / "news_candidates.json"

SECTIONS = [
    ("한국경제", "https://www.hankyung.com/realestate",
     r'<a[^>]+href="(https://www\.hankyung\.com/article/[^"]+)"[^>]*>([\s\S]{0,300}?)</a>'),
    ("매일경제", "https://www.mk.co.kr/news/realestate/",
     r'<a[^>]+href="(https://www\.mk\.co\.kr/news/realestate/\d+)"[^>]*>([\s\S]{0,300}?)</a>'),
    ("연합뉴스", "https://www.yna.co.kr/economy/real-estate",
     r'<a[^>]+href="(https?://www\.yna\.co\.kr/view/AKR[^"?]+(?:\?[^"]*)?)"[^>]*>([\s\S]{0,300}?)</a>'),
]

NOISE = re.compile(r"^\d+\s*")


def clean(fragment: str) -> str:
    text = re.sub(r"<[^>]+>", " ", fragment)
    text = re.sub(r"\s+", " ", html.unescape(text)).strip()
    return NOISE.sub("", text).strip()


def collect() -> list[dict]:
    items, seen = [], set()
    for outlet, url, pattern in SECTIONS:
        try:
            page = src.fetch(url)
        except Exception as exc:
            print(f"  ! {outlet} 목록 수집 실패: {exc}")
            continue
        found = 0
        for link, raw in re.findall(pattern, page):
            title = clean(raw)
            if len(title) < 12 or link in seen:
                continue
            seen.add(link)
            items.append({"outlet": outlet, "title": title, "url": link})
            found += 1
            if found >= 25:
                break
        print(f"  {outlet}: {found}건")
    return items


def main() -> None:
    print(f"[뉴스] {datetime.now(src.KST):%Y-%m-%d %H:%M} 후보 수집")
    items = collect()
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps({
        "collectedAt": datetime.now(src.KST).isoformat(timespec="seconds"),
        "candidates": items,
    }, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"  총 {len(items)}건 → {OUT}")


if __name__ == "__main__":
    main()
