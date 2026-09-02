# 안드로이드 앱

## 설치

빌드된 APK 위치:

```text
android-app/app/build/outputs/apk/debug/app-debug.apk
```

이 파일을 휴대폰으로 옮겨 설치한다. (개발 확인용 Debug APK. 처음 설치할 때
"출처를 알 수 없는 앱" 허용이 필요할 수 있다.)

## 다시 빌드

```powershell
.\scripts\build-android.ps1
```

`data/*.json` → 앱 assets 복사 → APK 빌드까지 한 번에 한다.

Android Studio에서 열려면 `android-app` 폴더를 **Open** 하면 된다.

## 앱이 데이터를 받아오는 순서

```
1. 내려받아 저장해 둔 최신본   (내부 저장소 brief.json)
2. 없으면 APK 에 넣어둔 스냅샷 (assets/brief.json)
```

앱을 켜면 브리핑 날짜가 오늘이 아닐 때 자동으로 한 번 받아온다.
받아오기에 실패해도 **기존에 보던 내용은 그대로 남는다.** 받아온 JSON이
깨졌거나 비어 있으면 저장하지 않는다.

주소는 앱 안의 **설정**에서 바꿀 수 있다. 기본값은
`app/build.gradle.kts` 의 `DEFAULT_BRIEF_URL` 이다.

## 화면

| 탭 | 내용 |
|---|---|
| 뉴스 | 카드 목록 → 탭하면 전문 화면(핵심 → 본문 → 원문 링크) |
| 청약홈 | 분양결과(판정 뱃지 + 지역/평형/가격/경쟁률) · 예정단지. 카드마다 `평형별 상세` · `지도 보기` 버튼 |
| 정비사업 | 기획안 |

## 지도

상세 화면 **맨 위에 단지 위치 지도**가 뜬다. 손가락으로 확대·이동된다.

- 지도는 **카카오맵**. `.env` 의 `KAKAO_JS_KEY` 가 채워져 있을 때만 쓴다.
  키가 비어 있으면 **OpenStreetMap** 으로 자동 대체된다(키 불필요).
- 좌표는 앱이 아니라 **수집 단계에서** 붙인다 (`scripts/geocode.py`).
  카카오 로컬 API 로 **지번까지 정확하게** 찾고, 키가 없으면 OSM(동 단위)으로 내려간다.
  카카오 키는 PC 의 `.env` 에만 있고 APK 에는 들어가지 않는다.
  현재 적중률은 17건 중 15건이며 전부 지번 정확도다.
- 못 찾은 공고는 지도 자리에 안내가 뜨고, 아래 **네이버 지도 / 카카오맵** 버튼으로 넘어갈 수 있다.
- 한 번 찾은 주소는 `data/geocache.json` 에 쌓여 다시 조회하지 않는다.

라이트/다크는 시스템 설정을 따라간다. 색과 간격은 웹 브리핑 페이지와 같은 값을 쓴다.

## 카카오맵 켜기

1. [developers.kakao.com](https://developers.kakao.com) > 내 애플리케이션 > (기존 앱) > **앱 설정 > 플랫폼 키**
   에서 **JavaScript 키**를 복사한다. 새 앱을 만들지 않는다 — 새 앱은 무료 쿼터를 못 받는다.
2. **앱 설정 > 플랫폼 > Web > 사이트 도메인**에 `https://estate-watch.local` 을 추가한다.
   실제 존재하는 주소일 필요는 없다. 앱의 WebView 가 이 주소인 척 지도를 띄운다.
3. 프로젝트 루트 `.env` 에 값을 넣는다.

   ```
   KAKAO_JS_KEY=복사한_JavaScript_키
   KAKAO_MAP_ORIGIN=https://estate-watch.local
   ```

4. `.\scriptsuild-android.ps1` 로 다시 빌드한다.

JS 키는 APK 안에 들어가지만 **등록한 도메인에서만 동작**하므로 남이 가져가도 쓸 수 없다.
REST 키(주소→좌표)는 APK 에 들어가지 않는다.

## 구조

외부 라이브러리 없이 Android 플랫폼 API와 Kotlin으로만 작성했다.
(테스트에서만 `org.json` 실제 구현을 쓴다. 안드로이드의 `org.json` 은 JVM 테스트에서 빈 껍데기이기 때문.)

```text
android-app/app/src/main/java/com/estatewatch/app/
├── MainActivity.kt            # 탭 3개, 목록/상세 화면, 새로고침, 설정
├── data/Models.kt             # JSON → 데이터 모델
├── data/BriefRepository.kt    # 네트워크 · 캐시 · 번들 스냅샷 · 설정
└── ui/UiKit.kt                # 카드, 칩, 판정 뱃지, 데이터 띠
```

## 테스트

```powershell
cd android-app
.\gradlew.bat :app:testDebugUnitTest
```

실제 배포되는 `assets/brief.json` 을 그대로 읽어서 파싱한다.
수집 스크립트가 형식을 바꾸면 여기서 먼저 깨진다.

특히 **미달 주택형의 경쟁률이 `null` 로 유지되는지** 검사한다.
`0.0` 으로 뭉개지면 화면에서 마감처럼 보이기 때문이다.
