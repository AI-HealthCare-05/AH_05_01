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

/** 하단 탭 5개 — 기록 · 댐 · 홈 · 참고 · 마이 (가운데가 홈, 맨 오른쪽이 마이) */
enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Record(Route.RECORD, "기록", TmtnIcons.Record),
    Dam(Route.DAM, "댐", TmtnIcons.Dam),
    Home(Route.HOME, "홈", TmtnIcons.Home),
    Reference(Route.REFERENCE, "참고", TmtnIcons.Reference),
    My(Route.MY, "마이", TmtnIcons.MyPage),
}
