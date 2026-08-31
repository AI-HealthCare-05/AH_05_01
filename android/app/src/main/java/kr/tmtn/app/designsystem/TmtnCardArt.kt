package kr.tmtn.app.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 카드 아트 전용 토큰 — 브랜드 시안 `04 틈 노트` 를 그대로 옮긴 값.
 *
 * **이 색들은 앱 UI 팔레트가 아니다.**
 * `TmtnColor` 의 순백+먹색과 별개로, 카드 한 장이 "물건" 처럼 보이게 하는 색이다.
 * 뒷면(딥그린 + 앰버 TMTN)과 한 세트라 앞면도 같은 계열을 쓴다.
 *
 * CLAUDE.md 가 폐기한 v4 초록 팔레트와 값이 겹치지만 쓰임이 다르다 —
 * 그쪽은 화면 전체의 바탕색이었고, 이쪽은 카드 그림 안에서만 산다.
 * **그래서 여기 색을 화면 UI(버튼·배경·탭)로 끌어 쓰면 안 된다.**
 *
 * 출처: brand-identity/card-front-concepts-2026-08-29 (styles.css `:root`, `.card-04`)
 */
object TmtnCardArt {
    /** 카드 바탕 딥그린 */
    val Ink = Color(0xFF0C3B2E)

    /** 카드 위 글자·하단 박스 바탕 */
    val Cream = Color(0xFFFFF8ED)

    /** 포인트 — 가운데 TMTN, 행운의 숫자 */
    val Amber = Color(0xFFFFBA00)

    /** 재료 스탬프 바탕, "오늘의 한 줄" 라벨 */
    val Wood = Color(0xFFBB8A52)

    /* 크림을 옅게 쓴 단계들. 시안의 rgba(255,248,237, α) 를 그대로 옮겼다. */
    val CreamStrong = Cream.copy(alpha = 0.76f)   // 날짜
    val CreamLabel = Cream.copy(alpha = 0.56f)    // 항목 라벨
    val CreamFaint = Cream.copy(alpha = 0.50f)    // 재료명
    val CreamRule = Cream.copy(alpha = 0.22f)     // 가운데 구분선
    val CreamRow = Cream.copy(alpha = 0.12f)      // 항목 사이 선
    val CreamFrame = Cream.copy(alpha = 0.18f)    // 카드 안쪽 테두리

    /* ── 치수 ────────────────────────────────────────── */

    val Padding = 22.dp
    val Radius = 20.dp
    val InnerInset = 10.dp
    val InnerRadius = 16.dp
    val StampSize = 64.dp
    val StampRadius = 18.dp
    val LineBoxRadius = 15.dp

    /* ── 글자 ────────────────────────────────────────── */

    /**
     * 시안은 360×576 웹 카드라 라벨이 9~10px 이다.
     * 앱은 손에 쥐고 보는 화면이고 만성질환 사용자를 기준으로 삼으므로
     * **라벨을 12sp 아래로 내리지 않는다.** 나머지 비율은 시안을 따른다.
     */
    val TitleSize = 20.sp      // "오늘의 틈"
    val DateSize = 12.sp
    val MaterialSize = 12.sp
    val LabelSize = 12.sp      // 오늘의 운세 · 행운의 행동 …
    val FortuneSize = 22.sp
    val MarkSize = 11.sp       // TMTN
    val ValueSize = 17.sp      // 항목 값
    val NumberSize = 26.sp     // 행운의 숫자
    val LineSize = 15.sp       // 오늘의 한 줄
}
