# -*- coding: utf-8 -*-
"""청약홈 탭 데이터 생성 → data/apply.json

  - 최근 분양결과 10개 : 지역 / 주요평형 / 가격 / 경쟁률
  - 청약 예정단지 10개 : 지역 / 주요평형 / 가격 / 세대수

사용법:  python scripts/fetch_apply.py
"""
from __future__ import annotations

import json
import pathlib
import sys
from datetime import date, datetime

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import apply_source as src  # noqa: E402

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "data" / "apply.json"

WANT_TYPES = {"일반분양", "공공분양"}
MIN_UNITS = 30          # 줍줍·잔여세대(1~20세대) 노이즈 제외
WANT_RESULTS = 10
WANT_UPCOMING = 10


def main_type(notice: dict) -> dict | None:
    """세대수가 가장 많은 주택형 = 그 단지의 '주력 평형'."""
    types = [t for t in notice.get("types") or [] if t["units"] > 0]
    return max(types, key=lambda t: t["units"]) if types else None


def first_rank_rates(notice: dict) -> dict[str, float | None]:
    """주택형별 1순위(해당지역 우선) 경쟁률."""
    out: dict[str, float | None] = {}
    for row in notice.get("competition") or []:
        if row["rank"] != "1순위":
            continue
        key = row["type"]
        if row["area"] == "해당지역":
            out[key] = row["rate"]
        elif key not in out or out[key] is None:
            out.setdefault(key, row["rate"])
    return out


def summarize_result(notice: dict) -> dict | None:
    mt = main_type(notice)
    if not mt:
        return None
    rates = first_rank_rates(notice)

    # 청약홈은 접수가 0인 주택형의 경쟁률 행을 아예 내려주지 않는다.
    # 따라서 "마감/미달" 판정은 경쟁률 표가 아니라 일반공급이 있는 전체 주택형을 기준으로 한다.
    offered = [t for t in notice["types"] if t["generalUnits"] > 0]
    closed = [t for t in offered if (rates.get(t["type"]) or 0) >= 1]
    short = [t for t in offered if t not in closed]

    applied = sum(r["applied"] for r in notice["competition"] if r["rank"] == "1순위")
    general = sum(t["generalUnits"] for t in offered)
    avg = round(applied / general, 2) if general else None

    if not offered:
        verdict, tone = "결과 미공개", "unknown"
    elif not short:
        verdict, tone = "1순위 전 타입 마감", "good"
    elif not closed:
        verdict, tone = "1순위 미달", "bad"
    else:
        verdict, tone = f"일부 미달 ({len(short)}/{len(offered)}타입)", "warn"

    return {
        "name": notice["name"],
        "region": notice["region"],
        "address": notice["address"],
        "builder": notice["builder"],
        "supplyType": notice["supplyType"],
        "receipt": notice["receipt"],
        "endDate": notice["endDate"],
        "mainType": mt["type"],
        "mainTypeUnits": mt["units"],
        "mainPrice": mt["price"],
        "mainPricePyeong": mt["pyeongPrice"],
        "mainRate": rates.get(mt["type"]),
        "totalUnits": notice["totalUnits"],
        "generalUnits": general,
        "applicants": notice["totalApplicants"] or applied,
        "avgRate": avg,
        "maxRate": notice["maxRate"],
        "verdict": verdict,
        "tone": tone,
        "byType": [
            {"type": t["type"], "units": t["units"], "generalUnits": t["generalUnits"],
             "price": t["price"], "pyeongPrice": t["pyeongPrice"],
             "rate": rates.get(t["type"])}
            for t in notice["types"]
        ],
        "detailUrl": notice["detailUrl"],
    }


def summarize_upcoming(notice: dict) -> dict | None:
    mt = main_type(notice)
    if not mt:
        return None
    return {
        "name": notice["name"],
        "region": notice["region"],
        "address": notice["address"],
        "builder": notice["builder"],
        "supplyType": notice["supplyType"],
        "status": notice["status"],
        "receipt": notice["receipt"],
        "startDate": notice["startDate"],
        "mainType": mt["type"],
        "mainTypeUnits": mt["units"],
        "mainPrice": mt["price"],
        "mainPricePyeong": mt["pyeongPrice"],
        "totalUnits": notice["totalUnits"],
        "priceRange": notice["priceRange"],
        "movein": notice["movein"],
        "byType": [
            {"type": t["type"], "units": t["units"], "generalUnits": t["generalUnits"],
             "price": t["price"], "pyeongPrice": t["pyeongPrice"]}
            for t in notice["types"]
        ],
        "detailUrl": notice["detailUrl"],
    }


def qualifies(notice: dict) -> bool:
    return (notice["supplyType"] in WANT_TYPES
            and notice["totalUnits"] >= MIN_UNITS)


def collect_results(today: date) -> list[dict]:
    """최근 접수 마감분부터 거슬러 올라가며 경쟁률이 나온 단지 10곳."""
    dated: list[tuple[str, str]] = []
    for month in reversed(src.month_keys(today, back=3, forward=0)):
        dated.extend(src.calendar_notice_ids(month))
    dated.sort(key=lambda x: x[0], reverse=True)

    results, seen = [], set()
    for chunk_start in range(0, len(dated), 12):
        batch = [nid for _, nid in dated[chunk_start:chunk_start + 12] if nid not in seen]
        seen.update(batch)
        for notice in sorted(src.parse_many(batch), key=lambda n: n["endDate"], reverse=True):
            if not qualifies(notice) or notice["maxRate"] is None:
                continue
            row = summarize_result(notice)
            if row:
                results.append(row)
        if len(results) >= WANT_RESULTS:
            break
    results.sort(key=lambda r: r["endDate"], reverse=True)
    return results[:WANT_RESULTS]


def collect_upcoming(today: date) -> list[dict]:
    """접수중 + 앞으로 접수 예정인 단지. 공고가 뜬 것만 잡히므로 보통 2~4주치."""
    ids = list(src.open_notice_ids())
    for month in src.month_keys(today, back=0, forward=2):
        for _, nid in src.calendar_notice_ids(month):
            if nid not in ids:
                ids.append(nid)

    rows = []
    for notice in src.parse_many(ids):
        if not qualifies(notice) or notice["status"] not in ("접수중", "접수예정"):
            continue
        row = summarize_upcoming(notice)
        if row:
            rows.append(row)
    rows.sort(key=lambda r: r["startDate"] or "9999")
    return rows[:WANT_UPCOMING]


def main() -> None:
    today = datetime.now(src.KST).date()
    print(f"[청약홈] {today} 수집 시작")
    results = collect_results(today)
    print(f"  분양결과 {len(results)}건")
    upcoming = collect_upcoming(today)
    print(f"  청약예정 {len(upcoming)}건")

    payload = {
        "updatedAt": datetime.now(src.KST).isoformat(timespec="seconds"),
        "source": {
            "name": "한국부동산원 청약홈 (공공데이터포털)",
            "url": src.APPLYHOME_LIST,
            "via": "cheongyak.today",
        },
        "results": results,
        "upcoming": upcoming,
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"  → {OUT}")


if __name__ == "__main__":
    main()
