package kr.tmtn.app.data

import android.content.Context

/**
 * 의존성 모음. Hilt/Koin 은 Android 구조 ADR 이 정해지기 전까지 넣지 않는다
 * (docs/DEVELOPMENT_ENVIRONMENT.md — "팀원이 개인 취향으로 둘을 섞어 추가하지 않는다").
 * 지금 규모에서는 이 정도로 충분하고, 나중에 DI 로 바꿔도 화면 코드는 그대로다.
 */
class AppContainer(context: Context) {
    val catalog: MissionCatalog = MissionCatalog.load(context)
    val store: TmtnStore = TmtnStore(context)
}
