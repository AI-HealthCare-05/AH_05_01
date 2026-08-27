package kr.tmtn.app.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/* ---------------------------------------------------------------- 뼈대 */

@Composable
fun TmtnTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(TmtnIcons.Back, contentDescription = "뒤로", tint = TmtnColor.OnSurface)
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(title, style = TmtnText.Title, color = TmtnColor.OnSurface)
        Spacer(Modifier.weight(1f))
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, style = TmtnText.Label, color = TmtnColor.OnSurfaceVariant)
            }
        }
    }
}

/** 카드 한 장. DESIGN.md 기본은 그림자 없음 — 테두리로만 구분한다. */
@Composable
fun TmtnCardBox(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = TmtnShape.Card,
    background: Color = TmtnColor.Surface,
    border: Color? = TmtnColor.OutlineVariant,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun SectionHeader(title: String, trailing: String? = null, onTrailing: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = TmtnText.Title, color = TmtnColor.OnSurface)
        Spacer(Modifier.weight(1f))
        if (trailing != null) {
            TextButton(onClick = { onTrailing?.invoke() }) {
                Text(trailing, style = TmtnText.Label, color = TmtnColor.OnSurfaceVariant)
            }
        }
    }
}

/* ---------------------------------------------------------------- 버튼 */

@Composable
fun TmtnFilledButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** DESIGN.md: 비활성 버튼은 비활성 이유를 문장으로 함께 보여준다. */
    disabledReason: String? = null,
    onClick: () -> Unit,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = TmtnShape.Button,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TmtnColor.Primary,
                contentColor = TmtnColor.OnPrimary,
                disabledContainerColor = TmtnColor.DisabledContainer,
                disabledContentColor = TmtnColor.OnDisabled,
            ),
        ) { Text(text, style = TmtnText.Label) }

        if (!enabled && disabledReason != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                disabledReason,
                style = TmtnText.Caption,
                color = TmtnColor.OnSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun TmtnTonalButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = TmtnShape.Button,
        modifier = modifier.fillMaxWidth().height(52.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = TmtnColor.SecondaryContainer,
            contentColor = TmtnColor.OnSurface,
            disabledContainerColor = TmtnColor.DisabledContainer,
            disabledContentColor = TmtnColor.OnDisabled,
        ),
    ) { Text(text, style = TmtnText.Label) }
}

@Composable
fun TmtnOutlinedButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = TmtnShape.Button,
        modifier = modifier.fillMaxWidth().height(52.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TmtnColor.OnSurface),
    ) { Text(text, style = TmtnText.Label) }
}

@Composable
fun TmtnQuietButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Text(text, style = TmtnText.Label, color = TmtnColor.OnSurfaceVariant)
    }
}

/* ---------------------------------------------------------------- 조각 */

enum class BadgeState(val label: String) {
    NotStarted("아직 시작하지 않았어요"),
    Running("진행 중"),
    Paused("잠시 멈춰 있어요"),
    Done("완료"),
    Rest("쉼"),
}

@Composable
fun StatusBadge(state: BadgeState, text: String = state.label) {
    val bg = when (state) {
        BadgeState.Done -> TmtnColor.RewardContainer
        BadgeState.Running -> TmtnColor.SecondaryContainer
        BadgeState.Paused -> TmtnColor.WoodContainer
        else -> TmtnColor.Background
    }
    Row(
        Modifier.clip(TmtnShape.Chip).background(bg).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = TmtnText.Caption, color = TmtnColor.OnSurface)
    }
}

@Composable
fun MeterBar(progress: Float, modifier: Modifier = Modifier, track: Color = TmtnColor.OutlineVariant) {
    val p = progress.coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(TmtnShape.Chip)
            .background(track),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(p)
                .clip(TmtnShape.Chip)
                .background(TmtnColor.Primary),
        )
    }
}

enum class NoteTone { Neutral, Notice, Warning }

/** 화면 안의 안내 박스. 실패를 탓하지 않는 문장만 넣는다. */
@Composable
fun NoteBox(tone: NoteTone = NoteTone.Neutral, title: String? = null, body: String) {
    val bg = when (tone) {
        NoteTone.Neutral -> TmtnColor.Background
        NoteTone.Notice -> TmtnColor.RewardContainer
        NoteTone.Warning -> TmtnColor.ErrorContainer
    }
    Column(
        Modifier.fillMaxWidth().clip(TmtnShape.SmallCard).background(bg).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon: ImageVector = if (tone == NoteTone.Warning) TmtnIcons.Warning else TmtnIcons.Info
                Icon(icon, contentDescription = null, tint = TmtnColor.OnSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = TmtnText.Label, color = TmtnColor.OnSurface)
            }
        }
        Text(body, style = TmtnText.Body, color = TmtnColor.OnSurfaceVariant)
    }
}

/** 이름 + 값 한 줄. 측정 상세(유효 시간·제외 구간·품질)에 쓴다. */
@Composable
fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = TmtnText.Body, color = TmtnColor.OnSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, style = TmtnText.Label, color = TmtnColor.OnSurface)
    }
}

/** 댐 재료 보상 칩. */
@Composable
fun RewardChip(name: String, hint: String, count: Int = 1) {
    Row(
        Modifier.clip(TmtnShape.SmallCard).background(TmtnColor.WoodContainer).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(TmtnIcons.Leaf, contentDescription = null, tint = TmtnColor.Wood, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text("$name ${count}개", style = TmtnText.Label, color = TmtnColor.OnSurface)
            Text(hint, style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant)
        }
    }
}

/**
 * 일러스트가 들어갈 자리. Figma v4 의 `Image slot` 과 같은 규칙 —
 * 준비되지 않은 이미지를 그럴듯하게 지어내지 않고 점선 자리로 남긴다.
 */
@Composable
fun ImageSlot(tag: String, title: String, desc: String, height: Dp = 150.dp) {
    Column(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(TmtnShape.Card)
            .background(TmtnColor.SecondaryContainer)
            .border(1.dp, TmtnColor.Secondary, TmtnShape.Card)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(tag, style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Text(title, style = TmtnText.Label, color = TmtnColor.OnSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(desc, style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant, textAlign = TextAlign.Center)
    }
}
