package com.tmtn.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntRect
import com.tmtn.app.R
import kotlin.math.roundToInt

const val DamArtworkAspectRatio = 512f / 300f

/** ⚠️ 2026-09-18 교체(UI/UX 핸드오프 DM01~09) - 새 시트(dam_water_repair_sheet,
 * 1536×1024, 3열×2행 각 512×512)로 교체. 각 셀의 실제 콘텐츠 바운딩 박스를 픽셀
 * 단위로 측정해서(0~2단계 Y194~446, 3~5단계 Y82~328) 여유를 두고 통일된 crop
 * 영역(높이 300)을 잡음 - 두 행의 높이가 같아야 Crossfade 전환 시 크기가 안 흔들림.
 * stage·재료 수 자체는 그대로 서버 값을 쓰고(계약: "새 임계값이 아니다"), 이 파일은
 * 순수하게 그림 표시만 바뀜.
 */
@Composable
fun DamArtwork(stage: Int, modifier: Modifier = Modifier, description: String? = null) {
    val art = ImageBitmap.imageResource(R.drawable.dam_water_repair_sheet)
    val source = damArtworkSourceRect(stage, art.width, art.height)
    Canvas(modifier.fillMaxWidth().aspectRatio(source.width.toFloat() / source.height).semantics {
        contentDescription = description ?: "내 댐 ${stage.coerceIn(0, 5)}단계"
        stateDescription = damWaterDescription(stage)
    }) {
        drawImage(art, srcOffset = source.topLeft,
            srcSize = IntSize(source.width, source.height),
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
    }
}

/** 3~5단계는 시트의 두 번째 행이다. 셀 안의 여백만 빼면 첫 행을 다시 읽게 된다. */
internal fun damArtworkSourceRect(stage: Int, width: Int, height: Int): IntRect {
    val scene = stage.coerceIn(0, 5)
    val cellWidth = width / 3
    val left = scene % 3 * cellWidth
    val top = scene / 3 * (height / 2) + if (scene < 3) 170 else 60
    return IntRect(left, top, left + cellWidth, top + 300)
}

fun damWaterDescription(stage: Int): String = when (stage.coerceIn(0, 5)) {
    0 -> "댐의 빈틈으로 나온 물이 모래 위로 넓게 흐르고 있어요."
    1 -> "첫 재료를 더한 자리에서 물줄기가 조금 줄었어요."
    2 -> "빈틈을 이어 붙여 모래 위 물길이 한결 좁아졌어요."
    3 -> "댐을 이으니 작은 틈으로만 물이 흐르고 있어요."
    4 -> "마지막 작은 틈에 물방울만 남았어요."
    else -> "틈이 메워져 물은 댐 안에, 모래는 보송하게 남았어요."
}


fun damRepairLabel(stage: Int): String = when (stage) {
    0 -> "함께 메울 작은 빈틈"
    1 -> "첫 빈틈 받치기"
    2 -> "벌어진 기둥 잇기"
    3 -> "몸통 연결하기"
    4 -> "물이 새는 이음 메우기"
    else -> "단단하게 마무리"
}
