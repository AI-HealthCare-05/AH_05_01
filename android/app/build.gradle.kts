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

        // ⚠️ 2026-09-09 추가(구글 계정 연동 로그인).
        // 여기 들어가는 건 Google Cloud Console에서 만든 **웹 애플리케이션 유형**
        // 클라이언트 ID입니다(안드로이드 유형 아님 - 헷갈리기 쉬운 부분).
        // 서버(config.GOOGLE_CLIENT_ID)와 정확히 같은 값이어야 하고, 이 값이 구글
        // ID 토큰의 aud 클레임으로 들어가서 서버 검증의 기준이 됩니다.
        // 비밀값은 아니지만(앱에 어차피 박혀서 나감) 팀원마다 다른 값을 쓸 수 있게
        // TEST_ACCOUNT_*와 같은 방식으로 local.properties에서 읽습니다.
        //   GOOGLE_WEB_CLIENT_ID=1234567890-xxxxxxxx.apps.googleusercontent.com
        // 값이 없으면 빈 문자열이고, GoogleSignInHelper가 "설정 안 됨"으로 안내합니다.
        buildConfigField(
            "String", "GOOGLE_WEB_CLIENT_ID",
            "\"${localProperties.getProperty("GOOGLE_WEB_CLIENT_ID", "")}\"",
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

    // ⚠️ 2026-09-08 추가: 미션 미완료·쉼/포기 리마인더 로컬 알림 스케줄링용.
    // ⚠️ 2026-09-10: 구글 로그인 작업분을 얹으면서 이 줄이 지워져 있었음(compileDebugKotlin에서
    // androidx.work 미해결로 빌드 실패). notification/ 3개 파일이 이 의존성을 씁니다 -
    // MissionReminderWorker, RestGiveUpReminderWorker, NotificationScheduler.
    implementation(libs.androidx.work.runtime.ktx)

    // 구글 계정 연동 로그인 (2026-09-09)
    // ⚠️ 예전 방식인 play-services-auth의 GoogleSignIn API는 공식 deprecated이고 앞으로
    // Play Services Auth SDK에서 제거될 예정이라, 처음부터 Credential Manager로 붙임.
    // credentials-play-services-auth가 있어야 실제 구글 계정 선택 UI가 뜹니다(둘 다 필요).
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
}
