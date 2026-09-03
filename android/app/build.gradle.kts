import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}
// ⚠️ 2026-09-02 리뷰 반영: TEST_ACCOUNT_PASSWORD가 ApiClient.kt에 평문으로 박혀 있어서
// git 히스토리에 이미 노출됐음(서버 쪽 실제 비밀번호는 별도로 교체 필요). 앞으로는 커밋되는
// 소스에 안 남게, local.properties(.gitignore에 이미 있음)에서 읽어서 BuildConfig로 주입.
// local.properties에 아래 두 줄이 없으면 빈 문자열로 채워지고, ApiClient.loginWithTestAccount()가
// 그 경우 그냥 실패 처리함 (필수 아님 - 온보딩 건너뛰고 테스트하고 싶을 때만 쓰는 편의 기능).
//   TEST_ACCOUNT_EMAIL=test@tmtn.com
//   TEST_ACCOUNT_PASSWORD=실제비밀번호
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.tmtn.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tmtn.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String", "TEST_ACCOUNT_EMAIL",
            "\"${localProperties.getProperty("TEST_ACCOUNT_EMAIL", "")}\"",
        )
        buildConfigField(
            "String", "TEST_ACCOUNT_PASSWORD",
            "\"${localProperties.getProperty("TEST_ACCOUNT_PASSWORD", "")}\"",
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // ... 기존 의존성들 그대로 두고 아래만 추가
    // 네트워크 통신 (Retrofit: HTTP 요청을 함수 호출처럼 쓰게 해주는 라이브러리)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")  // JSON ↔ Kotlin 객체 변환

    // OkHttp (Retrofit 내부에서 실제 통신을 담당. 로그 확인, 쿠키 처리에 사용)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // 비동기 처리 (코루틴은 이미 Compose 쓰면서 들어와 있을 수 있음, 없으면 추가)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
}
