# -*- coding: utf-8 -*-
"""주소 → 좌표. 앱 안에서 지도를 그리려면 위도·경도가 필요하다.

두 곳을 순서대로 물어본다.

  1) 카카오 로컬 API — 지번까지 정확하다. `.env` 의 KAKAO_REST_API_KEY 를 쓴다.
     이 키는 PC 에만 두고 앱(APK)에는 넣지 않는다.
  2) OpenStreetMap Nominatim — 키가 없을 때의 대비책. 동 단위까지만 잡힌다.

청약 공고 주소는 '…번지 일원', '…지구 내 B-1BL' 처럼 지도에 없는 표현이 섞여 있어서
번지 → 동 → 시군구 순으로 단계적으로 떼면서 찾는다.

한 번 찾은 주소는 data/geocache.json 에 남겨 다시 묻지 않는다.
"""
from __future__ import annotations

import json
import pathlib
import re
import sys
import time
import urllib.parse
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
CACHE = ROOT / "data" / "geocache.json"
ENV = ROOT / ".env"

KAKAO_ENDPOINT = "https://dapi.kakao.com/v2/local/search"
OSM_ENDPOINT = "https://nominatim.openstreetmap.org/search"
OSM_UA = "estate-watch/1.0 (personal real-estate briefing; https://github.com/hakai0231/estate-watch)"
OSM_PAUSE = 1.1          # Nominatim 은 초당 1회 제한


def kakao_key() -> str:
    if not ENV.exists():
        return ""
    m = re.search(r"^KAKAO_REST_API_KEY\s*=\s*(.+)$", ENV.read_text(encoding="utf-8"), re.M)
    return m.group(1).strip() if m else ""


# ── 캐시 ──────────────────────────────────────────────────────────────────

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


# ── 검색어 만들기 ─────────────────────────────────────────────────────────

def candidates(address: str) -> list[str]:
    """번지 → 동 → 시군구 순으로 넓혀 가며 검색어를 만든다."""
    base = re.sub(r"\s*(일원|일대|번지|내)\s*", " ", address)
    base = re.sub(r"\([^)]*\)", " ", base)
    base = re.sub(r"\s+", " ", base).strip()

    out = [base]
    m = re.match(r"^(\S*(?:특별시|광역시|특별자치시|특별자치도|도))\s+(.*)$", base)
    if m:
        province, rest = m.group(1), m.group(2)
        keep: list[str] = []
        for token in rest.split():
            keep.append(token)
            if re.search(r"[동읍면리]$", token):
                break
        if keep:
            out.append(f"{province} {' '.join(keep)}")
            dong = [t for t in keep if re.search(r"[동읍면리]$", t)]
            if dong:
                out.append(f"{province} {keep[0]} {dong[-1]}" if len(keep) > 1 else f"{province} {dong[-1]}")

    seen, uniq = set(), []
    for c in out:
        c = c.strip()
        if c and c not in seen:
            seen.add(c)
            uniq.append(c)
    return uniq


# ── 조회 ──────────────────────────────────────────────────────────────────

def _kakao(text: str, key: str) -> tuple[float, float, str] | None:
    """주소 검색 → 실패하면 장소(키워드) 검색."""
    for path in ("address", "keyword"):
        url = f"{KAKAO_ENDPOINT}/{path}.json?size=1&query={urllib.parse.quote(text)}"
        request = urllib.request.Request(url, headers={"Authorization": f"KakaoAK {key}"})
        try:
            with urllib.request.urlopen(request, timeout=15) as response:
                docs = json.load(response).get("documents", [])
        except Exception as exc:
            print(f"    ! 카카오 조회 실패({text}): {exc}")
            return None
        if docs:
            d = docs[0]
            label = d.get("address_name") or d.get("place_name") or text
            return round(float(d["y"]), 6), round(float(d["x"]), 6), label
    return None


def _osm(text: str) -> tuple[float, float, str] | None:
    url = f"{OSM_ENDPOINT}?format=json&limit=1&countrycodes=kr&q={urllib.parse.quote(text)}"
    request = urllib.request.Request(url, headers={"User-Agent": OSM_UA, "Accept-Language": "ko"})
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            data = json.load(response)
    except Exception as exc:
        print(f"    ! OSM 조회 실패({text}): {exc}")
        return None
    finally:
        time.sleep(OSM_PAUSE)
    if not data:
        return None
    return round(float(data[0]["lat"]), 6), round(float(data[0]["lon"]), 6), data[0].get("display_name", text)


def locate(address: str, cache: dict, key: str = "") -> dict | None:
    """{'lat','lon','matched','source'} 또는 None. 못 찾은 것도 캐시에 남긴다."""
    text_key = address.strip()
    if not text_key:
        return None
    cached = cache.get(text_key)
    # 예전에 OSM(동 단위)으로 잡아둔 것은 카카오가 생겼으면 다시 찾는다.
    if text_key in cache and not (key and cached and cached.get("source") == "osm"):
        return cached

    for text in candidates(text_key):
        if key:
            found = _kakao(text, key)
            if found:
                result = {"lat": found[0], "lon": found[1], "matched": found[2], "source": "kakao"}
                cache[text_key] = result
                return result
        found = _osm(text)
        if found:
            result = {"lat": found[0], "lon": found[1], "matched": found[2], "source": "osm"}
            cache[text_key] = result
            return result

    cache[text_key] = None
    return None


def attach(rows: list[dict]) -> tuple[int, int]:
    """공고에 lat/lon 을 채우고 (찾은 건수, 그중 지번 정확도) 를 돌려준다."""
    cache = _load_cache()
    key = kakao_key()
    if not key:
        print("  ! .env 에 KAKAO_REST_API_KEY 가 없어 OSM(동 단위)만 씁니다")

    found = precise = 0
    for row in rows:
        spot = locate(row.get("address", ""), cache, key)
        if spot:
            row["lat"] = spot["lat"]
            row["lon"] = spot["lon"]
            row["mapMatched"] = spot["matched"]
            found += 1
            if spot.get("source") == "kakao":
                precise += 1
        else:
            row["lat"] = None
            row["lon"] = None
    _save_cache(cache)
    return found, precise


if __name__ == "__main__":
    cache = _load_cache()
    key = kakao_key()
    for address in sys.argv[1:]:
        print(address, "->", locate(address, cache, key))
    _save_cache(cache)
