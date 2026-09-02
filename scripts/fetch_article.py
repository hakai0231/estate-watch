# -*- coding: utf-8 -*-
"""기사 본문 추출기.

Claude가 뉴스 5건을 요약할 때 쓴다. WebFetch 는 작은 모델이 압축한 결과를 주지만,
이건 원문 문단을 그대로 꺼내 주므로 사실·숫자를 빠뜨리지 않고 정리할 수 있다.

사용법:
    python scripts/fetch_article.py <URL> [<URL> ...]
    python scripts/fetch_article.py --json <URL> ...   # JSON 으로 출력
"""
from __future__ import annotations

import html
import json
import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import apply_source as src  # noqa: E402

# 매체별 본문 컨테이너. 순서대로 시도한다.
BODY_PATTERNS = [
    r'<div[^>]*id="articletxt"[^>]*>([\s\S]*?)</div>\s*</div>',          # 한국경제
    r'<div[^>]*itemprop="articleBody"[^>]*>([\s\S]*?)</div>\s*</div>',
    r'<div[^>]*class="[^"]*article-body[^"]*"[^>]*>([\s\S]*?)</div>\s*</div>',
]

# 여는 태그와 닫는 태그가 정규식으로 짝이 안 맞는 곳은 시작·끝 표시로 잘라낸다.
# (연합뉴스는 <div class="story-news article"> 안에 div 가 여러 겹 들어 있다)
BODY_ANCHORS = [
    ('class="story-news', '</article>'),        # 연합뉴스
]

DROP = re.compile(
    r"<(figure|figcaption|script|style|aside|iframe|table)[\s\S]*?</\1>|"
    r'<div[^>]*class="[^"]*(ad|banner|related|share|reporter|promotion)[^"]*"[\s\S]*?</div>',
    re.I,
)


def paragraphs(url: str) -> list[str]:
    page = src.fetch(url)
    body = ""
    for pattern in BODY_PATTERNS:
        m = re.search(pattern, page)
        if m and len(m.group(1)) > 400:
            body = m.group(1)
            break

    if not body:
        for start, end in BODY_ANCHORS:
            i = page.find(start)
            if i < 0:
                continue
            j = page.find(end, i)
            chunk = page[i:j if j > 0 else len(page)]
            # 이 구간의 <p> 만 모으면 기자 프로필·관련기사 목록이 걸러진다.
            paras = re.findall(r"<p[^>]*>([\s\S]*?)</p>", chunk)
            if paras:
                body = "</p><p>".join(paras)
                break

    if not body:
        return []

    body = DROP.sub(" ", body)
    body = re.sub(r"<br\s*/?>\s*<br\s*/?>", "\n\n", body, flags=re.I)
    body = re.sub(r"</p>", "\n\n", body, flags=re.I)
    body = re.sub(r"<br\s*/?>", "\n", body, flags=re.I)
    body = re.sub(r"<[^>]+>", " ", body)
    body = html.unescape(body)

    out = []
    for chunk in body.split("\n\n"):
        text = re.sub(r"[ \t\xa0]+", " ", chunk).strip()
        text = re.sub(r"\n+", " ", text)
        if len(text) < 25:
            continue
        # 사진 설명·저작권 안내·제보 안내 같은 상투 문구는 본문이 아니다.
        if re.match(r"^(사진|자료|그래픽|ⓒ|저작권|무단|기자|<|\[)", text):
            continue
        if re.search(r"제보는 카카오톡|무단 전재|재배포 금지|AI 학습", text):
            continue
        out.append(text)
    return out


def headline(url: str) -> str:
    page = src.fetch(url)
    m = re.search(r'<meta property="og:title" content="([^"]+)"', page)
    return html.unescape(m.group(1)).strip() if m else ""


def main() -> None:
    args = sys.argv[1:]
    as_json = "--json" in args
    urls = [a for a in args if a != "--json"]
    if not urls:
        raise SystemExit(__doc__)

    result = []
    for url in urls:
        try:
            paras = paragraphs(url)
        except Exception as exc:
            print(f"!! {url} 실패: {exc}", file=sys.stderr)
            paras = []
        result.append({"url": url, "paragraphs": paras, "chars": sum(len(p) for p in paras)})

    if as_json:
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return
    for item in result:
        print("=" * 70)
        print(item["url"], f"({len(item['paragraphs'])}문단 / {item['chars']}자)")
        print("=" * 70)
        for p in item["paragraphs"]:
            print(p)
            print()


if __name__ == "__main__":
    main()
