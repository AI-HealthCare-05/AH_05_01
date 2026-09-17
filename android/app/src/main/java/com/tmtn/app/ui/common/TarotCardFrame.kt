package com.tmtn.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tmtn.app.R
import com.tmtn.app.ui.theme.ColorBackground
import com.tmtn.app.ui.theme.ColorBrandForest

/**
 * ⚠️ 2026-09-14 추가 - TMTN_V19 카드 리디자인이 "Noto Sans KR 기준으로 제작"했다고
 * 명시(README.md 41행). 가변 폰트(VF) 1개 파일(10MB)로 여러 굵기를 커버함.
 *
 * 앱 전체 폰트(Pretendard, TmtnType.kt)를 이걸로 바꾸진 않음 - V19가 실제로 디자인한
 * 범위는 카드 화면뿐이고, 전체 앱 폰트 교체는 파급력이 커서 팀 승인 없이 임의로 바꿀
 * 사안이 아님. 카드 전용 폰트로 범위를 좁혀서 NoteCard 안 텍스트에만 적용함.
 */
val TarotCardFontFamily = FontFamily(
    Font(R.font.noto_sans_kr_vf, FontWeight.Normal),
    Font(R.font.noto_sans_kr_vf, FontWeight.Medium),
    Font(R.font.noto_sans_kr_vf, FontWeight.SemiBold),
    Font(R.font.noto_sans_kr_vf, FontWeight.Bold),
)

/**
 * ⚠️ 2026-09-12 추가 - TMTN_V19 카드 리디자인. android/TarotFrameDrawable.kt(디자인
 * 핸드오프 참고 코드, View Drawable용)를 Compose Canvas로 그대로 포팅함.
 *
 * 원화(920×1710)를 3구간으로 나눠서 위·아래 장식 구간은 원래 비율 그대로, 가운데만
 * 세로로 늘려서 긴 카드 문구도 프레임이 안 깨지게 함(9-patch와 같은 원리).
 * - 상단 578px(→350dp 카드 기준 220dp)
 * - 가운데 992px(→나머지 전부, 여기만 늘어남)
 * - 하단 140px(→350dp 카드 기준 53dp)
 */
@Composable
fun TarotCardFrame(
    modifier: Modifier = Modifier,
    isBack: Boolean = false,
    content: @Composable () -> Unit = {},
) {
    // 완료 카드 앞면도 흰색·숲색 기준으로 통일하고, 센서 여부에 따른 테두리를 두지 않는다.
    if (!isBack) {
        val shape = RoundedCornerShape(24.dp)
        Box(modifier.background(ColorBackground, shape).border(1.dp, ColorBrandForest, shape)) { content() }
        return
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val drawableId = R.drawable.tarot_card_back
    // ⚠️ 2026-09-13 버그 수정: androidx.compose.ui.res.imageResource가 이 프로젝트의
    // Compose 버전에서 Unresolved reference로 빌드 실패함(모듈/버전 차이로 추정) -
    // 표준 android.graphics.BitmapFactory로 우회. drawableId가 바뀔 때만 다시 디코드.
    val artwork = remember(drawableId) {
        android.graphics.BitmapFactory.decodeResource(context.resources, drawableId).asImageBitmap()
    }
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val scale = size.width / 350.dp.toPx()
            val topDp = 220f * scale
            val bottomDp = 53f * scale
            if (size.height <= topDp + bottomDp) return@Canvas

            val cornerRadius = CornerRadius(20f * scale, 20f * scale)
            val clip = Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        androidx.compose.ui.geometry.Rect(Offset.Zero, size), cornerRadius,
                    ),
                )
            }
            clipPath(clip) {
                drawArtworkRegion(artwork, srcYStart = 0, srcYEnd = 578, dstTop = 0f, dstBottom = topDp)
                drawArtworkRegion(artwork, srcYStart = 578, srcYEnd = 1570, dstTop = topDp, dstBottom = size.height - bottomDp)
                drawArtworkRegion(artwork, srcYStart = 1570, srcYEnd = 1710, dstTop = size.height - bottomDp, dstBottom = size.height)
            }
        }
        content()
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArtworkRegion(
    artwork: ImageBitmap, srcYStart: Int, srcYEnd: Int, dstTop: Float, dstBottom: Float,
) {
    val srcTop = (srcYStart / 1710f * artwork.height).toInt()
    val srcBottom = (srcYEnd / 1710f * artwork.height).toInt()
    drawImage(
        image = artwork,
        srcOffset = androidx.compose.ui.unit.IntOffset(0, srcTop),
        srcSize = androidx.compose.ui.unit.IntSize(artwork.width, (srcBottom - srcTop).coerceAtLeast(1)),
        dstOffset = androidx.compose.ui.unit.IntOffset(0, dstTop.toInt()),
        dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), (dstBottom - dstTop).toInt().coerceAtLeast(1)),
    )
}
