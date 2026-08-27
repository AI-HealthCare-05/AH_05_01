plugins {
    alias(libs.plugins.android.application)
    // kotlin.android 없음 — AGP 9 내장 Kotlin 이 처리한다
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "kr.tmtn.app"
    compileSdk = 37   // Compose BOM 2026.08.00(Compose 1.12.0)이 37 이상을 요구한다. targetSdk 와는 별개.

    defaultConfig {
        applicationId = "kr.tmtn.app"
        minSdk = 28
        targetSdk = 36

        // versionCode 는 배포마다 반드시 증가. 재사용 금지.
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
            // 백엔드가 아직 없으므로 목업 모드로 시작한다.
            buildConfigField("boolean", "USE_FAKE_DATA", "true")
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2/api/v1/\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            buildConfigField("boolean", "USE_FAKE_DATA", "true")
            buildConfigField("String", "API_BASE_URL", "\"https://api.example.invalid/api/v1/\"")

            // release 서명은 여기에 넣지 않는다.
            // keystore 는 local.properties 또는 GitHub Secrets 에서 주입한다.
            // 지금은 debug APK 로 배포하므로 서명 설정이 없어도 된다.
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Kotlin 의 jvmTarget 은 위 compileOptions.targetCompatibility(17) 를 자동으로 따라간다.
// AGP 9 내장 Kotlin 의 기본 동작이라 따로 적지 않는다.

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.compose)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.foundation)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}
