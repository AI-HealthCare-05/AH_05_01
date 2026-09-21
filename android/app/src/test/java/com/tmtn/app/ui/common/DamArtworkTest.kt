package com.tmtn.app.ui.common

import androidx.compose.ui.unit.IntRect
import org.junit.Assert.assertEquals
import org.junit.Test

class DamArtworkTest {
    @Test fun allSixScenesUseTheirOwnMeasuredCellInTheShippedSheet() {
        // 원본 1536×1024 이미지에서 확인한 여섯 장면의 실제 좌표다.
        val expected = listOf(
            IntRect(0, 170, 512, 470), IntRect(512, 170, 1024, 470), IntRect(1024, 170, 1536, 470),
            IntRect(0, 572, 512, 872), IntRect(512, 572, 1024, 872), IntRect(1024, 572, 1536, 872),
        )
        expected.forEachIndexed { stage, rectangle ->
            assertEquals("댐 ${stage}단계의 원본 영역", rectangle, damArtworkSourceRect(stage, 1536, 1024))
        }
    }
}
