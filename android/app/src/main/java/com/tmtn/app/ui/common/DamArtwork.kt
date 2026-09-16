package com.tmtn.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.tmtn.app.R
import com.tmtn.app.ui.theme.TmtnMotion
import com.tmtn.app.ui.theme.rememberTmtnReducedMotion
import kotlin.math.roundToInt

const val DamArtworkAspectRatio = 512f / 304f

/** 같은 댐의 틈과 모래 위 물길이 줄어드는 여섯 장면. 원본 Figma 에셋은 보존한다. */
@Composable
fun DamArtwork(stage: Int, modifier: Modifier = Modifier, description: String? = null) {
    val art = ImageBitmap.imageResource(R.drawable.dam_water_repair_sheet)
    val scene = stage.coerceIn(0, 5)
    val cellWidth = art.width / 3
    val cropHeight = (cellWidth / DamArtworkAspectRatio).roundToInt()
    val reduced = rememberTmtnReducedMotion()
    val keyboard = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    Crossfade(scene, modifier.fillMaxWidth().aspectRatio(DamArtworkAspectRatio).semantics {
        contentDescription = description ?: "내 댐 ${scene}단계"
        stateDescription = damWaterDescription(scene)
    }, animationSpec = if (keyboard) snap() else tween(
        if (reduced) TmtnMotion.SheetReducedMillis else TmtnMotion.EnterMillis,
        easing = TmtnMotion.EaseOut,
    ), label = "댐의 틈과 물길 전환") { visibleStage ->
        val cropTop = ((if (visibleStage < 3) 158f else 560f) * art.height / 1024f).roundToInt()
        Canvas(Modifier.fillMaxWidth().aspectRatio(DamArtworkAspectRatio)) {
            drawImage(art, srcOffset = IntOffset(visibleStage % 3 * cellWidth, cropTop),
                srcSize = IntSize(cellWidth, cropHeight),
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
        }
    }
}

/** 물길은 실천으로 쌓은 재료의 이야기이며 건강 점수나 질환 상태를 나타내지 않는다. */
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
