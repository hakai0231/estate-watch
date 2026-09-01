plugins {
    id("com.android.application")
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
