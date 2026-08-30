package kr.tmtn.app.designsystem

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 무언가 잘못됐을 때 보여 주는 화면의 공통 골격. (H02 · H03 · H04 · H07)
 *
 * 읽는 순서를 **무슨 일 → 왜 → 무엇을 하면 되는지** 로 고정한다.
 * 제목만 크게 두고 설명은 한 단계 낮춰, 급히 볼 때 제목만 읽어도 뜻이 통하게 한다.
 *
 * 세 가지를 지킨다 —
 * 1. **사용자 탓을 하지 않는다.** "잘못 눌렀어요" 대신 "연결이 끊겼어요".
 * 2. **할 수 있는 일을 준다.** 버튼이 없으면 언제 다시 오면 되는지라도 밝힌다.
 * 3. 빨강을 함부로 쓰지 않는다. 대부분은 잠깐의 일이라 먹색으로 차분히 알린다.
 */
@Composable
fun TmtnMessageView(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    note: String? = null,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = TmtnSpace.ScreenMargin, vertical = TmtnSpace.S32),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TmtnSpace.S12),
    ) {
        Text(
            title,
            style = TmtnText.Title,
            color = TmtnColor.OnSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            body,
            style = TmtnText.Body,
            color = TmtnColor.OnSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        if (note != null) {
            Spacer(Modifier.height(TmtnSpace.S4))
            NoteBox(body = note)
        }

        if (primaryLabel != null && onPrimary != null) {
            Spacer(Modifier.height(TmtnSpace.S8))
            TmtnFilledButton(text = primaryLabel, onClick = onPrimary)
        }
        if (secondaryLabel != null && onSecondary != null) {
            TmtnQuietButton(secondaryLabel, onClick = onSecondary)
        }
    }
}

/* ------------------------------------------------------ H01 · 스켈레톤 */

/**
 * 불러오는 동안 자리를 잡아 두는 회색 덩어리.
 *
 * 빙글빙글 도는 동그라미 대신 **실제 화면과 같은 모양**으로 둔다.
 * 내용이 들어찰 때 자리가 튀지 않아 눈이 덜 피로하다.
 */
@Composable
fun SkeletonBlock(
    height: Dp,
    modifier: Modifier = Modifier,
    widthFraction: Float = 1f,
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            // 느리게 숨 쉬는 정도. 빠르게 깜빡이면 시선을 뺏고 어지럽다.
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )

    Box(
        modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(TmtnShape.SmallCard)
            .alpha(alpha)
            .background(TmtnColor.OutlineVariant),
    )
}

/** 카드 한 장 모양의 스켈레톤 */
@Composable
fun SkeletonCard(lines: Int = 2, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(TmtnShape.Card)
            .background(TmtnColor.Surface)
            .padding(TmtnSpace.S16),
        verticalArrangement = Arrangement.spacedBy(TmtnSpace.S8),
    ) {
        SkeletonBlock(height = 20.dp, widthFraction = 0.45f)
        repeat(lines) {
            SkeletonBlock(height = 14.dp, widthFraction = if (it == lines - 1) 0.7f else 1f)
        }
    }
}

/**
 * 탭 화면이 불러오는 중일 때 통째로 얹는 스켈레톤. (H01)
 *
 * 화면 낭독기에는 덩어리를 하나하나 읽히지 않는다 —
 * 바깥에서 [label] 한 마디만 읽어 준다. 회색 상자 열 개를 읽어 봐야 소용이 없다.
 */
@Composable
fun TmtnLoadingSkeleton(
    label: String = "불러오는 중이에요",
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = TmtnSpace.ScreenMargin)
            .clearAndSetSemantics { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(TmtnSpace.S16),
    ) {
        Spacer(Modifier.height(TmtnSpace.S8))
        SkeletonCard(lines = 1)
        SkeletonCard(lines = 2)
        SkeletonCard(lines = 3)
    }
}
