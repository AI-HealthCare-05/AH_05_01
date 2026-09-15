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

/** Figma 1314:4848 / 1314:4987: exact source image and its six crop windows.
 * The same standing dam is repaired; stage zero must not become a newly built dam.
 * Presentation crop only. The downloaded Figma source bytes remain unchanged.
 */
@Composable
fun DamArtwork(stage: Int, modifier: Modifier = Modifier, description: String? = null) {
    val art = ImageBitmap.imageResource(R.drawable.figma_dam_repair_sheet)
    val scene = stage.coerceIn(0, 5)
    val cellWidth = art.width / 3
    val cropTop = if (scene < 3) 160 else 560
    val cropHeight = (cellWidth * 190f / 350f).roundToInt()
    Canvas(modifier.fillMaxWidth().aspectRatio(350f / 190f).then(
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
