import java.util.Properties

plugins {
    id("com.android.application")
}

/**
 * 프로젝트 루트의 .env 에서 값을 읽는다. .env 는 .gitignore 에 있어 저장소에 올라가지 않는다.
 * 키가 없으면 빈 문자열이 들어가고, 앱은 OpenStreetMap 으로 대체 동작한다.
 */
fun envValue(name: String): String {
    val file = rootProject.file("../.env")
    if (!file.exists()) return ""
    return Properties().apply { file.inputStream().use { load(it) } }.getProperty(name, "").trim()
}

android {
    namespace = "com.estatewatch.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.estatewatch.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"

        // 매일 아침 갱신된 브리핑을 받아오는 주소. 앱 설정에서 바꿀 수 있다.
        // 앱 안 지도. 네이버 -> 카카오 -> OSM 순으로 있는 것을 쓴다.
        buildConfigField("String", "NAVER_MAP_CLIENT_ID", "\"" + envValue("NAVER_MAP_CLIENT_ID") + "\"")
        buildConfigField("String", "NAVER_MAP_ORIGIN", "\"" + (envValue("NAVER_MAP_ORIGIN").ifBlank { "https://estate-watch.local" }) + "\"")
        // 카카오맵 JavaScript 키. 도메인 제한이 걸린 키이며, 없으면 OSM 지도를 쓴다.
        buildConfigField("String", "KAKAO_JS_KEY", "\"" + envValue("KAKAO_JS_KEY") + "\"")
        // 카카오 개발자 콘솔에 등록해야 하는 도메인. WebView 가 이 주소인 척 지도를 띄운다.
        buildConfigField("String", "KAKAO_MAP_ORIGIN", "\"" + (envValue("KAKAO_MAP_ORIGIN").ifBlank { "https://estate-watch.local" }) + "\"")

        buildConfigField(
            "String",
            "DEFAULT_BRIEF_URL",
            "\"https://raw.githubusercontent.com/hakai0231/estate-watch/master/data/brief.json\""
        )
    }

    buildFeatures { buildConfig = true }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    // 안드로이드의 org.json 은 JVM 테스트에서 빈 껍데기라 실제 구현이 필요하다.
    testImplementation("org.json:json:20240303")
}
