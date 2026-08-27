package kr.tmtn.app.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * DESIGN.md 아이콘 규칙: 2px 둥근 선.
 * 의료 십자·심전도·타로/점술 기호는 쓰지 않는다.
 *
 * Material 아이콘 세트를 쓰지 않는 이유: 위 규칙을 만족하는 아이콘만 골라 쓰기보다
 * 필요한 12개만 직접 그리는 편이 의존성도 적고 규칙을 어길 여지도 없다.
 */
private fun stroked(name: String, d: String): ImageVector =
    ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }.build()

private fun filled(name: String, d: String): ImageVector =
    ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        addPath(pathData = PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.Black))
    }.build()

object TmtnIcons {
    /** 하단 탭 5개 — 기록 · 댐 · 홈 · 참고 · 마이 */
    val Record: ImageVector = stroked("record", "M4 7h16v13H4z M8 4v5 M16 4v5 M4 12h16")
    val Dam: ImageVector = stroked("dam", "M6 7h12 M4 12h16 M7 17h10 M12 17v3")
    val Home: ImageVector = stroked("home", "M4 11l8-7 8 7 M6.5 10v10h11V10")
    val Reference: ImageVector = stroked("reference", "M3 20h18 M6.5 20v-6 M12 20V6 M17.5 20v-9")
    val MyPage: ImageVector = stroked("mypage", "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M4.5 21c0-4.1 3.4-7.5 7.5-7.5s7.5 3.4 7.5 7.5")

    val Back: ImageVector = stroked("back", "M15 4.5L7.5 12l7.5 7.5")
    val Check: ImageVector = stroked("check", "M5 12.5l4.8 4.8L19 7")
    val Play: ImageVector = filled("play", "M8 5.2L19 12 8 18.8z")
    val Pause: ImageVector = stroked("pause", "M9.5 5v14 M14.5 5v14")
    val Bell: ImageVector = stroked("bell", "M6 10a6 6 0 0 1 12 0c0 4.5 2 6 2 6H4s2-1.5 2-6z M10 19a2 2 0 0 0 4 0")
    val Walk: ImageVector = stroked("walk", "M13.5 5.4a1.9 1.9 0 1 0 0-3.8 1.9 1.9 0 0 0 0 3.8z M11 22l2.2-6.2-3-2.8L11.4 8l3 1.8 2.4 3.2 M10.2 13.4L7 17.8")
    val Leaf: ImageVector = stroked("leaf", "M5 19C5 9.5 11.6 5 19 5c0 8-6.2 14-14 14z M5.5 18.6C9.4 14.7 12.4 12.8 16 11.8")
    val Info: ImageVector = stroked("info", "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z M12 11v6 M12 7.6v.2")
    val Warning: ImageVector = stroked("warning", "M12 4L2.8 20h18.4L12 4z M12 10v4.6 M12 17.4v.2")
}
