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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 점선 테두리. 달력의 "미완료" 와 같은 뜻으로 쓴다 — **아직 아닌 것 · 빠진 것**.
 * 실선은 "내가 고른 것 · 확정된 것". 이 대비를 색이 아니라 선 종류로 유지한다.
 */
private fun Modifier.dashedBorder(
    color: Color,
    width: Dp = TmtnCalendar.RingWidth,
    radius: Dp = 0.dp,
    pill: Boolean = false,
): Modifier = drawBehind {
    val stroke = width.toPx()
    val r = if (pill) (size.height - stroke) / 2f else radius.toPx()
    drawRoundRect(
        color = color,
        topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
        style = Stroke(
            width = stroke,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(TmtnCalendar.Dash.dp.toPx(), TmtnCalendar.Gap.dp.toPx()),
            ),
        ),
    )
}

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

    /** 상태가 아니라 그냥 한 줄 안내를 칩 모양으로 보여줄 때 */
    Info("안내"),
}

/**
 * 상태 칩.
 *
 * v5 팔레트에서 `SecondaryContainer` `WoodContainer` 가 카드 바탕(`Surface`)과 같은 `#F7F4EE`
 * 가 되면서, 바탕색만으로 칩을 구분하던 v4 방식은 칩이 통째로 사라지는 문제가 있었다.
 * 그래서 **채움 + 선 종류**로 구분한다. 달력 4상태와 같은 언어다.
 *
 *   완료   = 먹색 꽉 참        (달력 '실천' 과 같다)
 *   진행 중 = 주황 채움 + 먹색 실선 (지금 = 오늘이라 주황을 쓴다)
 *   쉼     = 먹색 **실선** 테두리  (내가 고른 것)
 *   멈춤·시작 전 = 회색 **점선** 테두리 (아직 아닌 것)
 *
 * 칩은 흰 화면 위에도, 크림색 카드 위에도 놓인다. 그래서 테두리 색은
 * 두 바탕 모두에서 3:1 을 넘는 `OnSurface` / `OnSurfaceVariant` 만 쓴다.
 * `Outline` 은 흰 바탕에서 2.1:1 이라 테두리로 쓰면 안 된다.
 */
@Composable
fun StatusBadge(state: BadgeState, text: String = state.label) {
    val bg = when (state) {
        BadgeState.Done -> TmtnColor.Primary
        BadgeState.Running -> TmtnColor.Secondary
        BadgeState.Rest, BadgeState.Paused, BadgeState.Info -> TmtnColor.DisabledContainer
        BadgeState.NotStarted -> TmtnColor.Background
    }
    val fg = when (state) {
        BadgeState.Done -> TmtnColor.OnPrimary
        BadgeState.Running -> TmtnColor.OnReward
        BadgeState.Rest -> TmtnColor.OnSurface
        else -> TmtnColor.OnSurfaceVariant
    }
    val base = Modifier.clip(TmtnShape.Chip).background(bg)
    val outlined = when (state) {
        // 실선 — 내가 고른 것 · 확정된 것
        BadgeState.Rest ->
            base.border(TmtnCalendar.RingWidth, TmtnColor.OnSurface, TmtnShape.Chip)
        BadgeState.Running ->
            base.border(TmtnCalendar.RingWidth, TmtnColor.OnSurface, TmtnShape.Chip)
        // 점선 — 아직 아닌 것 · 빠진 것
        BadgeState.Paused, BadgeState.NotStarted ->
            base.dashedBorder(TmtnColor.OnSurfaceVariant, pill = true)
        else -> base
    }
    Row(
        outlined.padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = TmtnText.Caption, color = fg)
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

/**
 * 화면 안의 안내 박스. 실패를 탓하지 않는 문장만 넣는다.
 *
 * 박스는 흰 화면 위에도, 크림색 카드 위에도 놓인다. 바탕색만으로는 두 곳 모두에서
 * 경계가 보이지 않아(1.1:1) **왼쪽 강조 바**로 경계를 만든다.
 * Notice 바탕이 v5 에서 진한 앰버(`#FFB400`)로 바뀌었으므로 본문 글자는
 * `OnSurfaceVariant`(3.05:1, AA 미달) 가 아니라 `OnSurface`(9.97:1) 를 쓴다.
 */
@Composable
fun NoteBox(tone: NoteTone = NoteTone.Neutral, title: String? = null, body: String) {
    val bg = when (tone) {
        NoteTone.Neutral -> TmtnColor.DisabledContainer
        NoteTone.Notice -> TmtnColor.RewardContainer
        NoteTone.Warning -> TmtnColor.ErrorContainer
    }
    val accent = when (tone) {
        NoteTone.Neutral -> TmtnColor.OnSurfaceVariant
        NoteTone.Notice -> TmtnColor.OnSurface
        NoteTone.Warning -> TmtnColor.Error
    }
    // 진한 앰버 위에서는 보조색 글자가 AA 에 못 미친다. 본문도 먹색으로 올린다.
    val bodyColor = if (tone == NoteTone.Notice) TmtnColor.OnSurface else TmtnColor.OnSurfaceVariant

    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min)
            .clip(TmtnShape.SmallCard).background(bg),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
        Column(
            Modifier.weight(1f).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val icon: ImageVector = if (tone == NoteTone.Warning) TmtnIcons.Warning else TmtnIcons.Info
                    Icon(icon, contentDescription = null, tint = bodyColor, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(title, style = TmtnText.Label, color = TmtnColor.OnSurface)
                }
            }
            Text(body, style = TmtnText.Body, color = bodyColor)
        }
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
            .background(TmtnColor.DisabledContainer)
            // 점선 = 아직 없는 것. 주황은 "오늘" 에만 쓰므로 자리표시자에는 쓰지 않는다.
            .dashedBorder(TmtnColor.OnSurfaceVariant, radius = 16.dp)
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
