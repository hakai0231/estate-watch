# 매일 아침 브리핑 절차

Claude가 매일 아침 이 순서대로 실행한다. 소요 시간 3~5분.

---

## 0. 준비

```bash
cd <estate-watch 폴더>
```

## 1. 청약홈 데이터 수집 (자동)

```bash
python scripts/fetch_apply.py
```

`data/apply.json` 이 갱신된다. 사람이 손댈 것 없음.

- 최근 분양결과 10개 — 경쟁률이 공개된 단지만, 접수 마감 최근순
- 청약 예정·접수중 10개 — 입주자모집공고가 나온 단지만
- 무순위·임의공급(줍줍)과 30세대 미만 잔여세대는 제외한다. 노이즈이기 때문.

## 2. 뉴스 후보 수집 (자동)

```bash
python scripts/fetch_news.py
```

`data/news_candidates.json` 에 한국경제·매일경제·연합뉴스 부동산 섹션 헤드라인 60여 건이 쌓인다.

## 3. 뉴스 5건 선별 + 요약 (Claude가 판단)

후보 목록을 읽고 **5건**을 고른다. 고르는 기준:

1. **정책·세제** — 종부세, 대출규제, 세제개편처럼 시장 전체를 움직이는 것
2. **공급·예산** — 국토부/서울시 공급계획, 예산, 택지지구
3. **분양시장** — 이번 달 분양물량, 경쟁률 동향, 미분양
4. **정비사업** — 재건축·재개발 제도 변화, 주요 구역 진척, 시공사 수주
5. **시장지표** — 매매·전세·월세 가격, 거래량, 입주물량

한 카테고리에 몰리지 않게 분산한다. 단순 홍보성 분양기사·인사·게시판은 뺀다.

고른 5건은 `WebFetch`로 본문을 읽고 `data/news.json` 에 아래 형식으로 쓴다.

```json
{
  "date": "2026-09-01",
  "updatedAt": "2026-09-01T06:10:00+09:00",
  "editorNote": "오늘 시장의 핵심을 3~4문장으로. 개별 기사 요약이 아니라 '오늘 무슨 일이 벌어졌나'를 꿰는 문장.",
  "items": [
    {
      "rank": 1,
      "category": "세제·정책",
      "title": "기사 제목 그대로",
      "outlet": "한국경제",
      "publishedAt": "2026-09-01 16:09",
      "summary": "제목을 눌러보기 전에 보이는 한 줄. 기사의 결론.",
      "points": ["숫자가 들어간 핵심 4~5개"],
      "body": ["문단 단위로 4~6개. 원문 표현이 아니라 사실을 옮긴 요약문."],
      "url": "https://..."
    }
  ]
}
```

**지켜야 할 것**

- `body`는 기사 원문 복사가 아니라 **요약**이다. 저작권 문제도 있고, 길면 안 읽는다.
- 확인되지 않은 사실은 쓰지 않는다. 숫자는 기사에 나온 것만.
- `mk.co.kr`은 WebFetch가 막혀 있다. 매경 기사를 고르면 제목만 후보로 쓰고 본문은 다른 매체 같은 사안 기사로 대체한다.

## 4. 빌드

```bash
python scripts/build.py
```

세 곳이 한 번에 갱신된다.

- `data/brief.json` — 안드로이드 앱이 받아가는 파일
- `android-app/app/src/main/assets/brief.json` — 앱 번들 스냅샷
- `dist/index.html` — 웹 페이지

## 5. 앱으로 전송

**안드로이드 앱** — `data/brief.json` 을 커밋해서 GitHub 에 올린다.

```bash
git add data/ dist/ android-app/app/src/main/assets/brief.json
git commit -m "9월 2일 브리핑"
git push
```

폰은 `master` 브랜치의 `data/brief.json` 을 그대로 읽는다.
**앱을 다시 빌드하거나 설치할 필요는 없다. 파일만 갈아끼우면 된다.**
(raw.githubusercontent.com 은 최대 5분 정도 캐시된다.)

**웹 페이지** — `dist/index.html` 을 **같은 Artifact URL로 재배포**한다.
URL은 `docs/ARTIFACT.md` 에 적혀 있다.

---

## 권한

`.claude/settings.json` 에 이 절차가 쓰는 명령만 미리 열어 두었다.
새벽에 권한 질문으로 멈추지 않게 하기 위한 것이다.

열어 둔 것 — `python scripts/*`, `git add/commit/push/status/log/diff`,
`data/` 쓰기, 한국경제·연합뉴스·청약홈·오늘청약·raw.githubusercontent 조회.

막아 둔 것 — `git push --force`, `git reset --hard`, `rm -rf`, 그리고 **`.env` 읽기**.
카카오·네이버 키가 대화창에 노출되지 않게 하기 위한 것이다.
수집 스크립트는 파이썬 안에서 `.env` 를 직접 읽으므로 이 차단과 무관하게 동작한다.

절차에 없는 명령이 필요해지면 그때는 평소처럼 물어본다.

---

## 확인 사항

빌드 후 아래가 맞는지 본다.

- 뉴스 5건, 분양결과 10건이 채워졌는가
- 분양결과의 `verdict`(1순위 전 타입 마감 / 일부 미달 / 1순위 미달)가 경쟁률과 어긋나지 않는가
- 청약예정이 3건 미만이면 그건 실제로 공고가 없는 것이다. 억지로 채우지 않는다.

## 수집이 실패하면

`scripts/fetch_apply.py` 가 0건을 뱉으면 원본 사이트 구조가 바뀐 것이다.
그날은 [청약홈 APT 분양정보](https://www.applyhome.co.kr/ai/aia/selectAPTLttotPblancListView.do)를 직접 열어
손으로 `data/apply.json` 을 채우고, 파서는 따로 고친다.
