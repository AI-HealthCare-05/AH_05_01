// TMTN Android — 저장소 루트의 Python `app/` 과 이름이 겹치지 않도록
// 안드로이드 프로젝트는 android/ 아래에 독립된 Gradle 프로젝트로 둔다.
// Android Studio 에서는 이 android/ 폴더를 열어야 한다. (저장소 루트를 열면 안 된다)

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "TMTN"
include(":app")
