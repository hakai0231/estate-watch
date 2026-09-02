# -*- coding: utf-8 -*-
"""data/*.json 을 세 곳에 내보낸다.

  1. data/brief.json                            — 안드로이드 앱이 매일 받아가는 파일
  2. android-app/.../assets/brief.json          — 앱에 같이 넣는 스냅샷(오프라인 대비)
  3. dist/index.html                            — 웹 브리핑 한 파일

사용법:  python scripts/build.py
"""
from __future__ import annotations

import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
TEMPLATE = ROOT / "web" / "template.html"
HTML_OUT = ROOT / "dist" / "index.html"
BRIEF_OUT = ROOT / "data" / "brief.json"
ASSET_OUT = ROOT / "android-app" / "app" / "src" / "main" / "assets" / "brief.json"

SOURCES = {"news": ROOT / "data" / "news.json", "apply": ROOT / "data" / "apply.json"}

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import fetch_news  # noqa: E402


def load(path: pathlib.Path) -> dict:
    if not path.exists():
        print(f"  ! {path.name} 없음 — 빈 값으로 채웁니다")
        return {}
    return json.loads(path.read_text(encoding="utf-8"))


def main() -> None:
    payload = {key: load(path) for key, path in SOURCES.items()}

    # 1·2) 앱용 JSON. 들여쓰기 없이 저장해 전송량을 줄인다.
    compact = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
    for target in (BRIEF_OUT, ASSET_OUT):
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(compact, encoding="utf-8")

    # 3) 웹 페이지. </script> 가 데이터 안에 있으면 스크립트 태그가 조기 종료된다.
    blob = compact.replace("</", r"<\/")
    html = TEMPLATE.read_text(encoding="utf-8")
    if "__DATA__" not in html:
        raise SystemExit("template.html 에 __DATA__ 자리표시자가 없습니다")
    HTML_OUT.parent.mkdir(parents=True, exist_ok=True)
    HTML_OUT.write_text(html.replace("__DATA__", blob), encoding="utf-8")

    # 오늘 고른 제목을 기록해 둔다. 내일 같은 사안을 다시 고르지 않기 위해서다.
    today = payload.get("news", {})
    if today.get("date") and today.get("items"):
        fetch_news.remember(today["date"], [i.get("title", "") for i in today["items"]])
        print(f"  · 오늘 선택 {len(today['items'])}건을 news_history.json 에 기록")

    news = len(payload.get("news", {}).get("items", []))
    res = len(payload.get("apply", {}).get("results", []))
    up = len(payload.get("apply", {}).get("upcoming", []))
    print(f"[빌드] 뉴스 {news}건 · 분양결과 {res}건 · 청약예정 {up}건")
    print(f"  → {BRIEF_OUT}   ({BRIEF_OUT.stat().st_size:,} bytes)  앱이 받아갈 파일")
    print(f"  → {ASSET_OUT}   앱 번들 스냅샷")
    print(f"  → {HTML_OUT}  ({HTML_OUT.stat().st_size:,} bytes)")


if __name__ == "__main__":
    main()
