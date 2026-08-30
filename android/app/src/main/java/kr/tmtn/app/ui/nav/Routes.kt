package kr.tmtn.app.ui.nav

import androidx.compose.ui.graphics.vector.ImageVector
import kr.tmtn.app.designsystem.TmtnIcons

object Route {
    const val LOGIN = "login"
    const val ONBOARDING = "onboarding"

    const val HOME = "home"
    const val RECORD = "record"
    const val DAM = "dam"
    const val REFERENCE = "reference"
    const val MY = "my"

    const val CARD_PICK = "card_pick"
    const val CARD_FRONT = "card_front"
    const val MISSION_INTRO = "mission_intro"
    const val MISSION_SELF = "mission_self"
    const val MISSION_MODEL = "mission_model"
    const val COMPLETE = "complete"
}

/**
 * 하단 탭 5개 — 기록 · 댐 · 홈 · 틈튼지수 · 내 정보 (가운데가 홈, 맨 오른쪽이 내 정보)
 *
 * 라벨은 화면에 그대로 나온다. **"마이" 라는 말은 쓰지 않는다 — "내 정보" 다.**
 * 틈튼지수는 이 앱의 핵심이라 자기 탭을 가진다 (`E01`).
 * (enum 이름 `Reference` `My` 는 예전 이름이 남은 것이다. 라벨과 헷갈리지 말 것.)
 */
enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Record(Route.RECORD, "기록", TmtnIcons.Record),
    Dam(Route.DAM, "댐", TmtnIcons.Dam),
    Home(Route.HOME, "홈", TmtnIcons.Home),
    Reference(Route.REFERENCE, "틈튼지수", TmtnIcons.Reference),
    My(Route.MY, "내 정보", TmtnIcons.MyPage),
}
