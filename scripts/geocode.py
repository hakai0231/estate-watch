# -*- coding: utf-8 -*-
"""주소 → 좌표. 앱 안에서 지도를 그리려면 위도·경도가 필요하다.

OpenStreetMap 의 Nominatim 을 쓴다. API 키가 필요 없고 무료다.
대신 초당 1회 제한이 있으므로 결과를 data/geocache.json 에 쌓아두고
한 번 찾은 주소는 다시 묻지 않는다.

청약 공고 주소는 '…번지 일원', '…지구 내 B-1BL' 처럼 지도에 없는 표현이 섞여 있어서
번지 → 동 → 시군구 순으로 단계적으로 떼면서 찾는다. 동 단위까지만 잡혀도
'이 단지가 어디쯤인가' 는 보인다.
"""
from __future__ import annotations

import json
import pathlib
import re
import sys
import time
import urllib.parse
import urllib.request

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import apply_source as src  # noqa: E402

ROOT = pathlib.Path(__file__).resolve().parent.parent
CACHE = ROOT / "data" / "geocache.json"

ENDPOINT = "https://nominatim.openstreetmap.org/search"
# Nominatim 이용약관상 연락 가능한 User-Agent 를 요구한다.
UA = "estate-watch/1.0 (personal real-estate briefing; https://github.com/hakai0231/estate-watch)"
PAUSE = 1.1  # 초당 1회 제한


def _load_cache() -> dict:
    if CACHE.exists():
        try:
            return json.loads(CACHE.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            print("  ! geocache.json 이 깨져 있어 새로 만듭니다")
    return {}


def _save_cache(cache: dict) -> None:
    CACHE.parent.mkdir(parents=True, exist_ok=True)
    CACHE.write_text(json.dumps(cache, ensure_ascii=False, indent=1, sort_keys=True), encoding="utf-8")


def candidates(address: str) -> list[str]:
    """검색어를 넓은 범위로 단계적으로 완화한다."""
    base = re.sub(r"\s*(일원|일대|번지|내)\s*", " ", address)
    base = re.sub(r"\([^)]*\)", " ", base)          # 괄호 안 부연설명 제거
    base = re.sub(r"\s+", " ", base).strip()

    out = [base]

    # 시/도 + (시/군/구) + 읍면동 까지만
    m = re.match(r"^(\S*(?:특별시|광역시|특별자치시|특별자치도|도))\s+(.*)$", base)
    if m:
        province, rest = m.group(1), m.group(2)
        tokens = rest.split()
        keep = []
        for token in tokens:
            keep.append(token)
            if re.search(r"[동읍면리]$", token):
                break
        if keep:
            out.append(f"{province} {' '.join(keep)}")
            # 번지가 붙은 동이면 동만 남기고 한 번 더
            dong = [t for t in keep if re.search(r"[동읍면리]$", t)]
            if dong:
                out.append(f"{province} {keep[0]} {dong[-1]}" if len(keep) > 1 else f"{province} {dong[-1]}")
                out.append(f"{province} {dong[-1]}")

    seen, uniq = set(), []
    for c in out:
        c = c.strip()
        if c and c not in seen:
            seen.add(c)
            uniq.append(c)
    return uniq


def _query(text: str) -> tuple[float, float] | None:
    url = f"{ENDPOINT}?format=json&limit=1&countrycodes=kr&q={urllib.parse.quote(text)}"
    request = urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Language": "ko"})
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            data = json.load(response)
    except Exception as exc:
        print(f"    ! 조회 실패({text}): {exc}")
        return None
    finally:
        time.sleep(PAUSE)
    if not data:
        return None
    return round(float(data[0]["lat"]), 6), round(float(data[0]["lon"]), 6)


def locate(address: str, cache: dict) -> dict | None:
    """{'lat':…, 'lon':…, 'matched':…} 또는 None. 못 찾은 것도 캐시에 남긴다."""
    key = address.strip()
    if not key:
        return None
    if key in cache:
        return cache[key]

    result = None
    for text in candidates(key):
        found = _query(text)
        if found:
            result = {"lat": found[0], "lon": found[1], "matched": text}
            break
    cache[key] = result          # None 도 저장해서 매일 다시 묻지 않는다
    return result


def attach(rows: list[dict]) -> int:
    """공고 목록에 lat/lon 을 채워 넣고, 좌표를 찾은 건수를 돌려준다."""
    cache = _load_cache()
    found = 0
    for row in rows:
        spot = locate(row.get("address", ""), cache)
        if spot:
            row["lat"] = spot["lat"]
            row["lon"] = spot["lon"]
            row["mapMatched"] = spot["matched"]
            found += 1
        else:
            row["lat"] = None
            row["lon"] = None
    _save_cache(cache)
    return found


if __name__ == "__main__":
    cache = _load_cache()
    for address in sys.argv[1:]:
        print(address, "->", locate(address, cache))
    _save_cache(cache)
