# estate-watch — 부동산 조간 브리핑

매일 아침 부동산 뉴스·청약 결과·정비사업 진척을 한 화면으로 받아보는 앱.
Claude가 데이터를 모아 파일을 만들고, 휴대폰 앱이 그 파일을 받아간다.

## 어디서 보나

| | 위치 | 갱신 방식 |
|---|---|---|
| **안드로이드 앱** | `android-app/app/build/outputs/apk/debug/app-debug.apk` | 앱이 매일 아침 갱신된 `brief.json`을 받아옴 |
| 웹 페이지 | [Artifact](https://claude.ai/code/artifact/5ce40e50-57aa-4cb6-aadf-f8ad26cea8cc) | 매일 같은 주소로 재배포 |

앱 쪽 자세한 내용은 [docs/ANDROID.md](docs/ANDROID.md).

## 탭 구성

| 탭 | 내용 | 상태 |
|---|---|---|
| **뉴스** | 그날 주요 부동산 뉴스 5건. 탭하면 핵심 정리 + 본문 전체 + 기사 원문 링크 | 완성 |
| **청약홈** | 최근 분양결과 10곳(지역/주요평형/가격/경쟁률) + 청약 예정단지(지역/주요평형/가격/세대수) | 완성 |
| **정비사업** | 어떻게 만들지에 대한 기획안 | 기획만 |

## 매일 아침 무엇이 일어나나

```bash
python scripts/fetch_apply.py    # 청약홈 수집   → data/apply.json
python scripts/fetch_news.py     # 뉴스 후보 수집 → data/news_candidates.json
#   ↑ 여기까지 자동. 아래는 Claude가 5건을 골라 요약해 data/news.json 작성
python scripts/build.py          # 합치기 → data/brief.json · 앱 assets · dist/index.html
```

그 다음 `data/brief.json` 을 앱이 받아갈 주소에 올리고, `dist/index.html` 을 Artifact로 재배포한다.

자세한 절차는 [docs/DAILY_BRIEF.md](docs/DAILY_BRIEF.md).
데이터가 어디서 오는지는 [docs/DATA_SOURCES.md](docs/DATA_SOURCES.md).

## 직접 확인하고 싶을 때

```bash
python scripts/serve.py          # 웹 미리보기 → http://localhost:8777
```

```powershell
.\scripts\build-android.ps1      # 데이터 갱신 + APK 빌드
```

## 폴더 구조

```
data/         매일 갈아끼우는 내용물
  news.json     뉴스 5건 (Claude가 작성)
  apply.json    청약홈 수집 결과 (자동)
  brief.json    위 둘을 합친 앱 배포용 파일
scripts/      수집·빌드 스크립트
android-app/  안드로이드 앱 (Kotlin, 외부 라이브러리 없음)
web/          웹 페이지 틀        ← 웹 디자인을 바꾸려면 여기
dist/         완성된 index.html   ← 자동 생성. 직접 고치지 말 것
docs/         작업 절차와 데이터 출처
```

## 원칙

- 확인되지 않은 사실은 쓰지 않는다. 숫자는 원문에 나온 것만.
- 청약 데이터에 값이 없으면 비워 둔다. 그럴듯하게 채우지 않는다.
- 뉴스 본문은 원문 복사가 아니라 요약으로 쓴다. 원문 링크는 반드시 붙인다.
- 청약 신청 판단은 반드시 입주자모집공고문 원문으로 한다. 이 앱은 참고용이다.
