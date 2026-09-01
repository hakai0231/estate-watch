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
| 청약홈 | 분양결과(판정 뱃지 + 지역/평형/가격/경쟁률) · 예정단지 → 탭하면 주택형별 표 |
| 정비사업 | 기획안 |

라이트/다크는 시스템 설정을 따라간다. 색과 간격은 웹 브리핑 페이지와 같은 값을 쓴다.

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
