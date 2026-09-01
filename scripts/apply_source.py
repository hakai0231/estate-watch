# -*- coding: utf-8 -*-
"""청약홈 데이터 수집 공용 모듈.

원 출처는 한국부동산원 청약홈(공공데이터포털) 이며,
같은 데이터를 서버사이드 렌더링으로 정리해 두는 '오늘청약'(cheongyak.today)을
파싱해서 쓴다. 자세한 배경은 docs/DATA_SOURCES.md 참고.
"""
from __future__ import annotations

import html
import json
import re
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from datetime import date, datetime, timedelta, timezone

BASE = "https://cheongyak.today"
APPLYHOME_LIST = "https://www.applyhome.co.kr/ai/aia/selectAPTLttotPblancListView.do"
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) estate-watch/1.0"
KST = timezone(timedelta(hours=9))


def fetch(url: str, timeout: int = 30, retries: int = 3) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Language": "ko-KR,ko"})
    last: Exception | None = None
    for attempt in range(retries):
        try:
            with urllib.request.urlopen(req, timeout=timeout) as r:
                return r.read().decode("utf-8", "replace")
        except urllib.error.HTTPError as exc:
            if exc.code in (404, 400):        # 재시도해도 소용없는 응답
                raise
            last = exc
        except Exception as exc:              # 타임아웃·연결 끊김 등
            last = exc
        time.sleep(1.5 * (attempt + 1))
    raise last  # type: ignore[misc]


def strip_tags(fragment: str) -> str:
    s = re.sub(r"<!--.*?-->", "", fragment, flags=re.S)
    s = re.sub(r"<(script|style)[\s\S]*?</\1>", " ", s)
    s = re.sub(r"<[^>]+>", " ", s)
    return re.sub(r"\s+", " ", html.unescape(s)).strip()


def month_keys(today: date, back: int, forward: int) -> list[str]:
    keys, y, m = [], today.year, today.month
    for delta in range(-back, forward + 1):
        mm = m + delta
        yy = y + (mm - 1) // 12
        mm = (mm - 1) % 12 + 1
        keys.append(f"{yy:04d}-{mm:02d}")
    return keys


def calendar_notice_ids(month: str) -> list[tuple[str, str]]:
    """/calendar/YYYY-MM 한 장에 그 달 공고가 전부 실려 있다. [(접수일, 공고번호)]."""
    try:
        page = fetch(f"{BASE}/calendar/{month}")
    except Exception:
        return []   # 아직 열리지 않은 달은 404
    out, seen = [], set()
    for section in re.split(r'<section id="d-', page)[1:]:
        day = section[:10]
        if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", day):
            continue
        for nid in re.findall(r'href="/notice/(\d+)"', section):
            if nid not in seen:
                seen.add(nid)
                out.append((day, nid))
    return out


def open_notice_ids() -> list[str]:
    """접수중·접수예정 공고 목록(전체 공고 페이지 상단)."""
    page = fetch(f"{BASE}/notices")
    seen, ids = set(), []
    for nid in re.findall(r'href="/notice/(\d+)"', page):
        if nid not in seen:
            seen.add(nid)
            ids.append(nid)
    return ids


# ---------------------------------------------------------------- 상세 파싱

_PRICE_ROW = re.compile(r"<tr[\s\S]*?</tr>")
_CELL = re.compile(r"<t[hd][\s\S]*?</t[hd]>")
_UNIT_TYPE = re.compile(r"전용\s*([\d.]+)\s*㎡\s*([A-Z]{0,2})")
_COMP_ROW = re.compile(
    r"전용\s*([\d.]+)\s*㎡\s*([A-Z]{0,2})\s*(1순위|2순위)\s*·\s*(해당지역|기타지역)"
    r"\s*([\d.]+|-)\s*:\s*1\s*신청\s*([\d,]+)"
)


def _int(text: str) -> int:
    digits = re.sub(r"[^\d]", "", text or "")
    return int(digits) if digits else 0


def _label(size: str, suffix: str) -> str:
    """전용 84.95㎡ A -> '84㎡A' (시장에서 부르는 타입명)."""
    return f"{int(float(size))}㎡{suffix}".strip()


def _price_table(page: str) -> list[dict]:
    tables = re.findall(r"<table[\s\S]*?</table>", page)
    for table in tables:
        rows = []
        for tr in _PRICE_ROW.findall(table):
            cells = [strip_tags(c) for c in _CELL.findall(tr)]
            if len(cells) >= 5:
                rows.append(cells)
        if not rows or "주택형" not in rows[0][0]:
            continue
        out = []
        for cells in rows[1:]:
            m = _UNIT_TYPE.match(cells[0])
            if not m:
                continue
            counts = cells[2]
            general = re.search(r"일반\s*([\d,]+)", counts)
            special = re.search(r"특별\s*([\d,]+)", counts)
            total = re.search(r"총\s*([\d,]+)", counts)
            out.append({
                "type": _label(m.group(1), m.group(2)),
                "sizeM2": float(m.group(1)),
                "suffix": m.group(2),
                "units": _int(total.group(1)) if total else 0,
                "generalUnits": _int(general.group(1)) if general else (
                    _int(total.group(1)) if total and "일반공급" in counts else 0),
                "specialUnits": _int(special.group(1)) if special else 0,
                "price": cells[3],
                "priceManwon": _price_to_manwon(cells[3]),
                "pyeongPrice": cells[4],
            })
        if out:
            return out
    return []


def _price_to_manwon(text: str) -> int:
    """'13억 7,330만' -> 137330 (만원 단위)."""
    if not text:
        return 0
    eok = re.search(r"([\d,]+)\s*억", text)
    man = re.search(r"억\s*([\d,]+)\s*만", text) or (
        re.search(r"^([\d,]+)\s*만", text.strip()) if "억" not in text else None)
    total = _int(eok.group(1)) * 10000 if eok else 0
    total += _int(man.group(1)) if man else 0
    return total


def _competition(flat: str) -> list[dict]:
    rows = []
    for size, suffix, rank, area, rate, applied in _COMP_ROW.findall(flat):
        rows.append({
            "type": _label(size, suffix),
            "rank": rank,
            "area": area,
            "rate": None if rate == "-" else float(rate),
            "applied": _int(applied),
        })
    return rows


def parse_notice(nid: str) -> dict | None:
    page = fetch(f"{BASE}/notice/{nid}")
    flat = strip_tags(page)

    name = re.search(r"<h1[^>]*>([\s\S]*?)</h1>", page)
    name = strip_tags(name.group(1)) if name else ""
    name = re.sub(r"\s*청약 일정.*$", "", name).strip()
    if not name:
        return None

    def field(label: str, pattern: str = r"([^ ]+)") -> str:
        m = re.search(re.escape(label) + r"\s*" + pattern, flat)
        return m.group(1).strip() if m else ""

    region = field("공급 지역")
    supply_type = ""
    m = re.search(r"공급 유형\s*아파트\s*([^\s]+)", flat)
    if m:
        supply_type = m.group(1)
    total_units = _int(field("총 공급 세대", r"([\d,]+)\s*세대"))
    m = re.search(r"총 공급 세대\s*[\d,]+\s*세대\s*공급면적[^분]*분양가\s*([\d억,만\s~]*?원)", flat)
    price_range = m.group(1).strip() if m else ""
    m = re.search(r"공급 위치\s*(\S[^ ]*(?:\s+\S+)*?)\s*공급 세대수", flat)
    address = m.group(1).strip() if m else ""

    schedule = re.search(r"일반공급 접수\s*(\d+월 \d+일\([^)]+\))(?:\s*~\s*(\d+월 \d+일\([^)]+\)))?", flat)
    receipt = ""
    if schedule:
        receipt = schedule.group(1) + (f" ~ {schedule.group(2)}" if schedule.group(2) else "")
    iso = re.search(r"청약 접수는\s*(\d{4}-\d{2}-\d{2})부터\s*(\d{4}-\d{2}-\d{2})", flat)
    start_date = iso.group(1) if iso else ""
    end_date = iso.group(2) if iso else start_date
    if not start_date:
        one = re.search(r"모집공고일\s*(\d{4}-\d{2}-\d{2})", flat)
        start_date = end_date = one.group(1) if one else ""

    status = "마감"
    if "접수중" in flat[:1200]:
        status = "접수중"
    elif "접수예정" in flat[:1200]:
        status = "접수예정"

    types = _price_table(page)
    comps = _competition(flat)
    top = re.search(r"최고 경쟁률\s*([\d.]+)\s*:\s*1\s*·\s*총 신청\s*([\d,]+)", flat)

    return {
        "id": nid,
        "name": name,
        "region": region,
        "address": address,
        "supplyType": supply_type,
        "status": status,
        "totalUnits": total_units,
        "priceRange": price_range,
        "receipt": receipt,
        "startDate": start_date,
        "endDate": end_date,
        "types": types,
        "competition": comps,
        "maxRate": float(top.group(1)) if top else None,
        "totalApplicants": _int(top.group(2)) if top else 0,
        "detailUrl": f"{BASE}/notice/{nid}",
        "movein": field("입주 예정", r"([\d]{4}년\s*\d+월)"),
        "builder": (re.search(r"시공사\s*(.+?)\s*사업주체", flat).group(1).strip()
                    if re.search(r"시공사\s*(.+?)\s*사업주체", flat) else ""),
    }


def parse_many(ids: list[str], workers: int = 8) -> list[dict]:
    out = []
    with ThreadPoolExecutor(max_workers=workers) as pool:
        for res in pool.map(_safe_parse, ids):
            if res:
                out.append(res)
    return out


def _safe_parse(nid: str):
    try:
        return parse_notice(nid)
    except Exception as exc:  # 개별 공고 실패가 전체를 막지 않게
        print(f"  ! {nid} 파싱 실패: {exc}")
        return None
