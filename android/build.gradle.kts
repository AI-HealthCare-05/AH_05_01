// AGP 9.0 부터 Kotlin 이 AGP 안에 내장됐다.
// org.jetbrains.kotlin.android 를 선언하면 빌드가 실패한다.
// Compose 컴파일러 플러그인은 내장이 아니라서 계속 따로 선언한다.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
