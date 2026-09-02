# -*- coding: utf-8 -*-
"""부동산 뉴스 후보 수집 → data/news_candidates.json

기사 목록만 긁어온 뒤, 볼 필요 없는 것을 미리 걸러 둔다.

  - 광고성 분양단지 기사 : 돈 받고 쓰는 기사라 판단 재료가 안 된다
  - 어제와 겹치는 기사   : 같은 사안을 이틀 연속 읽을 이유가 없다

걸러낸 것도 버리지 않고 excluded 에 사유와 함께 남긴다. 판단이 틀렸을 수 있어서다.
5건을 고르고 요약하는 일은 Claude 가 한다 (docs/DAILY_BRIEF.md 참고).

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
NEWS = ROOT / "data" / "news.json"
HISTORY = ROOT / "data" / "news_history.json"

HISTORY_DAYS = 4          # 며칠치 지난 선택과 비교할지
SIMILAR_AT = 0.34         # 이 이상 겹치면 같은 사안으로 본다

ARTICLE_LINK = r'<a[^>]+href="({url})"[^>]*>([\s\S]{{0,300}}?)</a>'

SECTIONS = [
    ("한국경제", "https://www.hankyung.com/realestate",
     ARTICLE_LINK.format(url=r"https://www\.hankyung\.com/article/[^\"]+")),
    ("매일경제", "https://www.mk.co.kr/news/realestate/",
     ARTICLE_LINK.format(url=r"https://www\.mk\.co\.kr/news/realestate/\d+")),
    ("연합뉴스", "https://www.yna.co.kr/economy/real-estate",
     ARTICLE_LINK.format(url=r"https?://www\.yna\.co\.kr/view/AKR[^\"?]+(?:\?[^\"]*)?")),
    # 네이버 지역 뉴스. 구·시 단위 개발 소식이 여기에만 올라온다.
    # (역세권 개발, 지구단위계획, 추진위 승인 같은 것들)
    ("네이버(서울)", "https://land.naver.com/news/region.naver?city_no=1100000000",
     ARTICLE_LINK.format(url=r"https://n\.news\.naver\.com/article/[^\"]+")),
    ("네이버(경기)", "https://land.naver.com/news/region.naver?city_no=4100000000",
     ARTICLE_LINK.format(url=r"https://n\.news\.naver\.com/article/[^\"]+")),
    ("네이버(인천)", "https://land.naver.com/news/region.naver?city_no=2800000000",
     ARTICLE_LINK.format(url=r"https://n\.news\.naver\.com/article/[^\"]+")),
]

PER_SECTION = 25
NOISE = re.compile(r"^\d+\s*")

# ── 광고성 판별 ────────────────────────────────────────────────────────────
# 특정 단지를 팔기 위한 기사. 시장 상황을 알려주는 기사와 구분해야 한다.
SELLING = re.compile(
    r"모델하우스|견본주택|분양 나선|분양한다|분양 중|공급한다|선보인|"
    r"청약 돌입|청약 나선|잔여세대|선착순|계약 임박|주목받|눈길|관심 집중"
)
# 회사 홍보·인사·게시판. 업무협약·MOU 는 뺐다 — 정비사업에서는 실제 진전일 때가 있다.
PROMO = re.compile(
    r"^\[(게시판|부고|인사|신간|포토)\]|굿디자인|어워드|수상|대표 선임|대표이사|"
    r"취임|위촉|봉사활동|사회공헌|공개채용"
)
# 위 표현이 있어도 이런 건 시장 기사다
MARKET = re.compile(
    r"전국|수도권|통계|지수|경쟁률|미달|미분양|평균|전월|전년|올해|내년|분기|"
    r"만가구|만호|억원 규모|정책|대책|규제|세제|예산|금리|대출"
)


def clean(fragment: str) -> str:
    text = re.sub(r"<[^>]+>", " ", fragment)
    text = re.sub(r"\s+", " ", html.unescape(text)).strip()
    return NOISE.sub("", text).strip()


# 부동산 기사면 어디나 나오는 말들. 이것만 겹치는 건 같은 사안이 아니다.
COMMON = set("""
구역 가구 세대 규모 아파트 단지 주거 대단지 재탄생 재개발 재건축 분양 공급 사업 사업장
서울 경기 인천 일대 인근 최대 신규 확정 추진 계획 검토 발표 지정 승인 완료 착공 예정
기획 신통 대규모 주택 부지 조성 개발 시장 정부 국토부 서울시 올해 내년 지난해 위해
""".split())


def keywords(text: str) -> set[str]:
    """제목에서 그 기사만의 낱말을 뽑는다. 흔한 말은 뺀다."""
    found = {w for w in re.findall(r"[가-힣A-Za-z0-9]+", text)
             if len(w) >= 2 and w not in COMMON}
    return found or {text}


def overlap(a: str, b: str) -> float:
    """두 제목이 같은 사안인지. 낱말 단위로 봐야 '장위15구역'과 '상도15구역'이 갈린다."""
    x, y = keywords(a), keywords(b)
    return len(x & y) / len(x | y) if x | y else 0.0


def advertorial(title: str) -> bool:
    if PROMO.search(title):
        return True
    if SELLING.search(title) and not MARKET.search(title):
        return True
    return False


# ── 지난 선택 기록 ─────────────────────────────────────────────────────────

def load_history() -> list[dict]:
    entries: list[dict] = []
    if HISTORY.exists():
        try:
            entries = json.loads(HISTORY.read_text(encoding="utf-8")).get("days", [])
        except json.JSONDecodeError:
            entries = []
    # 아직 기록에 안 들어간 어제치가 news.json 에 남아 있을 수 있다
    if NEWS.exists():
        try:
            news = json.loads(NEWS.read_text(encoding="utf-8"))
            day = {"date": news.get("date", ""),
                   "titles": [i.get("title", "") for i in news.get("items", [])]}
            if day["date"] and day["date"] not in {e.get("date") for e in entries}:
                entries.append(day)
        except json.JSONDecodeError:
            pass
    entries.sort(key=lambda e: e.get("date", ""), reverse=True)
    return entries[:HISTORY_DAYS]


def remember(date: str, titles: list[str]) -> None:
    """오늘 고른 5건을 기록에 남긴다. build.py 가 호출한다."""
    days = []
    if HISTORY.exists():
        try:
            days = json.loads(HISTORY.read_text(encoding="utf-8")).get("days", [])
        except json.JSONDecodeError:
            days = []
    days = [d for d in days if d.get("date") != date]
    days.append({"date": date, "titles": titles})
    days.sort(key=lambda d: d.get("date", ""), reverse=True)
    HISTORY.write_text(
        json.dumps({"days": days[:14]}, ensure_ascii=False, indent=1), encoding="utf-8")


# ── 수집 ──────────────────────────────────────────────────────────────────

def collect() -> tuple[list[dict], list[dict]]:
    history = load_history()
    seen_urls: set[str] = set()
    seen_titles: list[str] = []
    keep: list[dict] = []
    dropped: list[dict] = []

    for outlet, url, pattern in SECTIONS:
        try:
            page = src.fetch(url)
        except Exception as exc:
            print(f"  ! {outlet} 목록 수집 실패: {exc}")
            continue

        found = 0
        for link, raw in re.findall(pattern, page):
            title = clean(raw)
            if len(title) < 12 or link in seen_urls:
                continue
            seen_urls.add(link)
            item = {"outlet": outlet, "title": title, "url": link}

            if advertorial(title):
                item["excludedFor"] = "광고성"
                dropped.append(item)
                continue

            past = next(
                ((d, t) for d in history for t in d.get("titles", [])
                 if overlap(title, t) >= SIMILAR_AT), None)
            if past:
                item["excludedFor"] = f"{past[0].get('date','전일')} 선택과 유사"
                item["similarTo"] = past[1]
                dropped.append(item)
                continue

            same = next((t for t in seen_titles if overlap(title, t) >= SIMILAR_AT), None)
            if same:
                item["excludedFor"] = "같은 사안 중복"
                item["similarTo"] = same
                dropped.append(item)
                continue

            seen_titles.append(title)
            keep.append(item)
            found += 1
            if found >= PER_SECTION:
                break
        print(f"  {outlet}: {found}건")

    return keep, dropped


def main() -> None:
    print(f"[뉴스] {datetime.now(src.KST):%Y-%m-%d %H:%M} 후보 수집")
    keep, dropped = collect()

    ad = sum(1 for d in dropped if d["excludedFor"] == "광고성")
    dup = len(dropped) - ad
    print(f"  → 후보 {len(keep)}건 (제외: 광고성 {ad}건 · 중복 {dup}건)")

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps({
        "collectedAt": datetime.now(src.KST).isoformat(timespec="seconds"),
        "recentPicks": load_history(),
        "candidates": keep,
        "excluded": dropped,
    }, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"  → {OUT}")


if __name__ == "__main__":
    main()
