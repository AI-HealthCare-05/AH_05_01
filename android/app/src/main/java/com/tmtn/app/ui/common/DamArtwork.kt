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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.tmtn.app.R
import kotlin.math.roundToInt

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
    val scene = stage.coerceIn(0, 5)
    val cellWidth = art.width / 3
    val cropTop = if (scene < 3) 170 else 60
    val cropHeight = 300
    Canvas(modifier.fillMaxWidth().aspectRatio(cellWidth.toFloat() / cropHeight).then(
        if (description != null) Modifier.semantics { contentDescription = description } else Modifier
    )) {
        drawImage(art, srcOffset = IntOffset(scene % 3 * cellWidth, cropTop),
            srcSize = IntSize(cellWidth, cropHeight),
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
    }
}

fun damRepairLabel(stage: Int): String = when (stage) {
    0 -> "함께 메울 작은 빈틈"
    1 -> "첫 빈틈 받치기"
    2 -> "벌어진 기둥 잇기"
    3 -> "몸통 연결하기"
    4 -> "물이 새는 이음 메우기"
    else -> "단단하게 마무리"
}
