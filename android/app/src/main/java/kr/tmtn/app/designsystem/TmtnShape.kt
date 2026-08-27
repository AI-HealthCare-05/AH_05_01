package kr.tmtn.app.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** DESIGN.md: 입력·작은 카드 12 / 카드 16 / 오늘 카드·시트 24 / 버튼 14. pill 은 chip 에만. */
object TmtnShape {
    val Input = RoundedCornerShape(12.dp)
    val SmallCard = RoundedCornerShape(12.dp)
    val Card = RoundedCornerShape(16.dp)
    val TodayCard = RoundedCornerShape(24.dp)
    val Sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val Button = RoundedCornerShape(14.dp)
    val Chip = RoundedCornerShape(percent = 50)
}

internal val TmtnShapes = Shapes(
    extraSmall = TmtnShape.Input,
    small = TmtnShape.SmallCard,
    medium = TmtnShape.Card,
    large = TmtnShape.TodayCard,
    extraLarge = TmtnShape.TodayCard,
)
