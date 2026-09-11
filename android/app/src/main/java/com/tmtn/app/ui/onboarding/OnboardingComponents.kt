package com.tmtn.app.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import com.tmtn.app.ui.theme.tmtnPressFeedback
import com.tmtn.app.ui.theme.AccessibilitySettingsHolder
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType
import kotlinx.coroutines.launch

/** Figma: 뒤로가기 + 타이틀만 있는 상단바(높이 64). */
@Composable
fun TmtnTopBar(title: String, onBack: (() -> Unit)?, trailing: (@Composable () -> Unit)? = null) {
    val colors = LocalTmtnColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(colors.background),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = colors.onSurface)
        } else Spacer(Modifier.width(20.dp))
        Text(title, style = TmtnType.title, color = colors.onSurface, modifier = Modifier.weight(1f).padding(vertical = 14.dp))
        trailing?.invoke()
    }
}

/** Every action has a 52dp minimum, grows with text and preserves its original callback. */
@Composable
fun TmtnPrimaryButton(
    text: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier,
    disabledReason: String? = null,
) {
    val colors = LocalTmtnColors.current
    val interactions = remember { MutableInteractionSource() }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = onClick, enabled = enabled, interactionSource = interactions,
            modifier = Modifier.fillMaxWidth().heightIn(min = if (AccessibilitySettingsHolder.largeControlsEnabled.value) 60.dp else 52.dp)
                .tmtnPressFeedback(interactions, enabled),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary,
                contentColor = androidx.compose.ui.graphics.Color.White,
                disabledContainerColor = colors.disabledContainer, disabledContentColor = colors.onSurfaceVariant),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        ) { Text(text, style = TmtnType.label, textAlign = TextAlign.Center) }
        if (!enabled && disabledReason != null) {
            Text(disabledReason, style = TmtnType.caption, color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun TmtnTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalTmtnColors.current
    val interactions = remember { MutableInteractionSource() }
    TextButton(onClick = onClick, interactionSource = interactions, modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).tmtnPressFeedback(interactions),
        colors = ButtonDefaults.textButtonColors(contentColor = colors.onSurface)) {
        Text(text, style = TmtnType.label, textAlign = TextAlign.Center)
    }
}

@Composable
fun TmtnOutlinedButton(text: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val colors = LocalTmtnColors.current
    val interactions = remember { MutableInteractionSource() }
    OutlinedButton(onClick = onClick, enabled = enabled, interactionSource = interactions,
        modifier = modifier.fillMaxWidth().heightIn(min = if (AccessibilitySettingsHolder.largeControlsEnabled.value) 60.dp else 52.dp)
            .tmtnPressFeedback(interactions, enabled),
        shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, colors.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurface, disabledContentColor = colors.onSurfaceVariant),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) { Text(text, style = TmtnType.label, textAlign = TextAlign.Center) }
}

/**
 * Google 브랜드 가이드에 맞춘 로그인 버튼 (2026-09-11 추가, 디자인 핸드오프
 * TMTN-google-signin-handoff-20260910 android-reference/res 기준).
 *
 * ⚠️ 앱 테마(라이트/다크)를 따르지 않고 색상을 고정함 - Google 공식 "Neutral" 버튼
 * 스타일은 배경/테두리/글자색을 브랜드 규정대로 고정하는 게 원칙(구글 로고 옆에
 * 임의의 앱 색을 섞으면 안 됨). 흰 배경(#FFFFFF) · 테두리(#747775, 1dp) ·
 * 글자색(#1F1F1F) 전부 디자인 핸드오프의 colors.xml 값 그대로.
 */
@Composable
fun TmtnGoogleButton(onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val interactions = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick, enabled = enabled, interactionSource = interactions,
        modifier = modifier.fillMaxWidth()
            .heightIn(min = if (AccessibilitySettingsHolder.largeControlsEnabled.value) 60.dp else 52.dp)
            .tmtnPressFeedback(interactions, enabled),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF747775)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White, contentColor = Color(0xFF1F1F1F),
            disabledContainerColor = Color.White, disabledContentColor = Color(0xFF747775),
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Image(
                painter = painterResource(com.tmtn.app.R.drawable.google_g_logo),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text("Google로 계속하기", style = TmtnType.label, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun TmtnTonalButton(text: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val colors = LocalTmtnColors.current
    val interactions = remember { MutableInteractionSource() }
    Button(onClick = onClick, enabled = enabled, interactionSource = interactions,
        modifier = modifier.fillMaxWidth().heightIn(min = if (AccessibilitySettingsHolder.largeControlsEnabled.value) 60.dp else 52.dp)
            .tmtnPressFeedback(interactions, enabled), shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = colors.surface, contentColor = colors.onSurface,
            disabledContainerColor = colors.disabledContainer, disabledContentColor = colors.onSurfaceVariant),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) { Text(text, style = TmtnType.label, textAlign = TextAlign.Center) }
}

/** Figma outlined text field: 라운드 12, floating label. Material3 OutlinedTextField가 이 패턴을 기본 지원. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TmtnTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    isPassword: Boolean = false,
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
    imeAction: androidx.compose.ui.text.input.ImeAction = androidx.compose.ui.text.input.ImeAction.Default,
    onImeAction: (() -> Unit)? = null,
) {
    val colors = LocalTmtnColors.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    // 비밀번호 필드는 keyboardType을 무조건 Password로 강제함 — 이게 지정돼 있어야
    // 폰 키보드가 "이건 영어/숫자만 쓰는 칸이구나" 인식해서 한글 대신 영문으로 자동 전환됨.
    // (isPassword=true인데 keyboardType=Text로 남아있던 게 실제 버그였음)
    val effectiveKeyboardType = if (isPassword) androidx.compose.ui.text.input.KeyboardType.Password else keyboardType

    // ⚠️ 2026-09-04 반영: imePadding()만으로는 키보드가 올라올 때 스크롤 위치를 자동으로
    // 안 옮겨줘서, 폼 아래쪽 칸을 누르면 여전히 키보드에 가려지는 경우가 있었음. 이 칸이
    // 포커스를 받는 순간 스스로 "나를 보이는 영역으로 스크롤해줘"라고 요청하게 함 - 부모가
    // 스크롤 가능한 Column(verticalScroll)이기만 하면 별도 설정 없이 여기 한 곳에서 전부 해결됨.
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    // ⚠️ 2026-09-06 QA(레이아웃) 반영: 비밀번호 필드 3곳 전부 표시/숨김 토글이 없었음.
    // material-icons-extended 의존성이 없어서(눈 아이콘은 그 확장 세트 소속) 텍스트
    // 토글("보기"/"숨기기")로 구현 - 시니어 사용자에게는 오히려 아이콘보다 명확함.
    var showPassword by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = TmtnType.caption) },
        textStyle = TmtnType.body,
        supportingText = supportingText?.let { { Text(it, style = TmtnType.caption) } },
        visualTransformation = if (isPassword && !showPassword) {
            androidx.compose.ui.text.input.PasswordVisualTransformation()
        } else {
            androidx.compose.ui.text.input.VisualTransformation.None
        },
        trailingIcon = if (isPassword) {
            {
                Text(
                    if (showPassword) "숨기기" else "보기",
                    style = TmtnType.caption, color = colors.onSurfaceVariant,
                    modifier = Modifier.clickable { showPassword = !showPassword }.padding(12.dp),
                )
            }
        } else {
            null
        },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = effectiveKeyboardType, imeAction = imeAction,
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onNext = { if (onImeAction != null) onImeAction() else focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) },
            onDone = { if (onImeAction != null) onImeAction() else keyboardController?.hide() },
        ),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.primary,
            unfocusedBorderColor = colors.outline,
            focusedLabelColor = colors.primary,
            unfocusedLabelColor = colors.onSurfaceVariant,
        ),
        singleLine = true,
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusEvent {
                if (it.isFocused) {
                    coroutineScope.launch { bringIntoViewRequester.bringIntoView() }
                }
            },
    )
}

/** Figma a07-chip / a08-freq: pill 모양 선택 칩. 선택되면 secondaryContainer 배경 + primary 2px 테두리. */
@Composable
fun TmtnChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalTmtnColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) colors.outlineVariant else colors.background)
            .border(
                width = 1.dp,
                color = if (selected) colors.primary else colors.outlineVariant,
                shape = RoundedCornerShape(999.dp),
            )
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, style = TmtnType.label,
            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
        )
    }
}

/** Figma a08-intensity: 세로 2줄(제목+설명) 카드형 선택지. */
@Composable
fun TmtnIntensityCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTmtnColors.current
    val interactions = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .tmtnPressFeedback(interactions)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.outlineVariant else colors.background)
            .border(
                width = 1.dp,
                color = if (selected) colors.primary else colors.outlineVariant,
                shape = RoundedCornerShape(12.dp),
            )
            .heightIn(min = 48.dp)
            .selectable(selected = selected, interactionSource = interactions,
                indication = androidx.compose.material3.ripple(), role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = TmtnType.label, color = colors.onSurface, textAlign = TextAlign.Center)
            if (description.isNotBlank()) Text(description, style = TmtnType.caption, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

/** Figma a03-steps: 진행 단계 바(연속된 막대 여러 개, 완료분만 primary색). */
@Composable
fun TmtnStepBars(totalSteps: Int, currentStep: Int, modifier: Modifier = Modifier) {
    val colors = LocalTmtnColors.current
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(totalSteps) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (index < currentStep) colors.primary else colors.outlineVariant),
            )
        }
    }
}

/** Figma a07-progress: 균등 분할 진행바(전체 너비를 단계 수로 나눔). */
@Composable
fun TmtnEqualProgressBar(totalSteps: Int, currentStep: Int, modifier: Modifier = Modifier) {
    val colors = LocalTmtnColors.current
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(totalSteps) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (index < currentStep) colors.primary else colors.outlineVariant),
            )
        }
    }
}

/** Figma a04-digit: 인증번호 한 자리 입력칸(50x60, 라운드 12). */
@Composable
fun TmtnDigitField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalTmtnColors.current
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 1 && it.all(Char::isDigit)) onValueChange(it) },
        modifier = modifier.heightIn(min = 60.dp),
        textStyle = TmtnType.title.copy(textAlign = TextAlign.Center),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
        ),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.primary,
            unfocusedBorderColor = colors.outline,
        ),
        singleLine = true,
    )
}

/** Figma a08-stepper: -/+ 버튼과 큰 숫자. captionText=null이면 "일주일 기준" 문구 생략(A07 생년월일용). */
@Composable
fun TmtnStepper(
    value: Int,
    unit: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    minValue: Int = 0,
    captionText: String? = "일주일 기준",
) {
    val colors = LocalTmtnColors.current
    val scale = LocalDensity.current.fontScale * com.tmtn.app.ui.theme.LocalTmtnTextScale.current
    val decrease = remember { MutableInteractionSource() }
    val increase = remember { MutableInteractionSource() }
    val valueContent: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("$value", style = if (captionText == null) TmtnType.label else TmtnType.display, color = colors.onSurface)
                Text(unit, style = TmtnType.body, color = colors.onSurfaceVariant)
            }
            captionText?.let { Text(it, style = TmtnType.caption, color = colors.onSurfaceVariant) }
        }
    }
    val minus: @Composable () -> Unit = {
        IconButton(onClick = onDecrement, enabled = value > minValue, interactionSource = decrease,
            modifier = Modifier.size(48.dp).tmtnPressFeedback(decrease, value > minValue)) {
            Box(Modifier.size(28.dp).semantics { contentDescription = "${unit} 줄이기" }, contentAlignment = Alignment.Center) {
                Box(Modifier.width(18.dp).height(2.dp).background(if (value > minValue) colors.onSurface else colors.onDisabled))
            }
        }
    }
    val plus: @Composable () -> Unit = {
        IconButton(onClick = onIncrement, interactionSource = increase,
            modifier = Modifier.size(48.dp).tmtnPressFeedback(increase)) {
            Icon(Icons.Default.Add, "${unit} 늘리기", Modifier.size(28.dp), tint = colors.onSurface)
        }
    }
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth()) {
        if (scale > 1.3f && maxWidth < 360.dp) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                valueContent()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { minus(); plus() }
            }
        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            minus()
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { valueContent() }
            plus()
        }
    }
}

/** A07 생년월일용 - 좌우로 나란히 두 개 놓아야 해서 TmtnStepper보다 작고 컴팩트한 버전. */
@Composable
fun TmtnCompactStepper(
    value: Int,
    unit: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalTmtnColors.current
    Box(
        modifier = modifier
            .background(colors.surface, RoundedCornerShape(12.dp))
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        TmtnStepper(value, unit, onDecrement, onIncrement, captionText = null, minValue = Int.MIN_VALUE)
    }
}

/** Figma a08-card .band: 저/중/고강도 배지. */
@Composable
fun TmtnIntensityBand(text: String, containerColor: androidx.compose.ui.graphics.Color) {
    val colors = LocalTmtnColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = TmtnType.caption, color = colors.onSurface)
    }
}

/** Figma a06 체크박스 행. */
@Composable
fun TmtnCheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    optional: Boolean = false,
    onViewClick: (() -> Unit)? = null,
) {
    val colors = LocalTmtnColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Checkbox(checked = checked, onCheckedChange = null,
                modifier = Modifier.padding(horizontal = 12.dp))
            Text(label, style = TmtnType.body,
                color = if (optional) colors.onSurfaceVariant else colors.onSurface,
                modifier = Modifier.weight(1f))
        }
        onViewClick?.let {
            TextButton(onClick = it, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("보기", style = TmtnType.label, color = colors.primary)
            }
        }
    }
}

/**
 * 스크롤 휠 하나. 가운데 칸이 "선택된 값"이고, 스크롤해서 맞추는 방식(iOS 스타일 피커).
 * Compose 기본 LazyColumn + snap 기능만 써서 만듦 (새 라이브러리 추가 안 함).
 */
@Composable
fun WheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onSelectedChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: androidx.compose.ui.unit.Dp = 40.dp,
) {
    val colors = LocalTmtnColors.current
    val listState = rememberLazyListState()
    val flingBehavior = rememberSnapFlingBehavior(listState)
    val density = LocalDensity.current

    // ⚠️ contentPadding으로 위아래 여백을 주는 방식은 LazyColumn의 viewport 계산이
    // 애매해서 계속 한 칸씩 어긋나는 버그가 났음. 대신 리스트 맨 앞·맨 뒤에 "빈 칸"을
    // 실제 항목으로 하나씩 끼워 넣는 훨씬 단순한 방식으로 교체 — 인덱스 계산이
    // 명확해지고, 맨 처음/맨 끝 항목도 정중앙까지 자연스럽게 스크롤됨.
    val paddedItems = remember(items) { listOf("") + items + listOf("") }

    LaunchedEffect(Unit) {
        // paddedItems 기준으로 selectedIndex(빈칸 포함 전 실제 항목 번호)를 맨 위로 스크롤하면
        // [selectedIndex, selectedIndex+1(=실제 선택값), selectedIndex+2] 3칸이 보이면서
        // 가운데(=실제 선택값)가 정확히 화면 중앙에 옴.
        listState.scrollToItem(selectedIndex)
    }

    val boxHeightPx = with(density) { (itemHeight * 3).toPx() }

    // ⚠️ 예전엔 "정중앙 항목이 뭔지"를 비동기 콜백(snapshotFlow.collect)으로만 계산해서
    // 부모 state(selectedIndex)에 반영했는데, 빠르게 스크롤하면 화면은 이미 새 위치로
    // 그려졌지만 이 계산이 한 프레임 늦게 따라와서 강조 표시가 실제 위치랑 어긋나 보였음.
    // 이제 "지금 이 순간 정중앙에 있는 항목"을 derivedStateOf로 매 스크롤 프레임마다
    // 직접 계산해서 강조 표시에 바로 씀 — 시차가 생길 수가 없는 구조.
    val centeredPaddedIndex by remember {
        derivedStateOf {
            val centerPx = boxHeightPx / 2f
            listState.layoutInfo.visibleItemsInfo.minByOrNull { info ->
                kotlin.math.abs((info.offset + info.size / 2f) - centerPx)
            }?.index
        }
    }

    // 정중앙 항목이 실제로 바뀌었을 때만 부모에게 알림 (저장용 데이터, 화면 강조 표시랑은 별개)
    LaunchedEffect(centeredPaddedIndex) {
        val paddedIndex = centeredPaddedIndex ?: return@LaunchedEffect
        val realIndex = paddedIndex - 1
        if (realIndex in items.indices && realIndex != selectedIndex) {
            onSelectedChange(realIndex)
        }
    }

    Box(modifier = modifier.height(itemHeight * 3), contentAlignment = Alignment.Center) {
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            modifier = Modifier.fillMaxHeight(),
        ) {
            itemsIndexed(paddedItems) { paddedIndex, label ->
                val isSelected = paddedIndex == centeredPaddedIndex
                Box(
                    modifier = Modifier.height(itemHeight).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (label.isNotEmpty()) {
                        Text(
                            label,
                            style = if (isSelected) TmtnType.title else TmtnType.body,
                            color = if (isSelected) colors.onSurface else colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** "HH:MM"(24시간제) <-> (오전/오후 인덱스, 시 인덱스 0~11, 분 0~59) 상호 변환. */
object TimeWheelConverter {
    val amPmOptions = listOf("오전", "오후")
    val hourOptions = (1..12).map { "${it}시" }
    val minuteOptions = (0..59).map { "%02d분".format(it) }

    fun fromTimeString(time: String): Triple<Int, Int, Int> {
        val parts = time.split(":")
        val hour24 = parts.getOrNull(0)?.toIntOrNull() ?: 7
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val amPmIndex = if (hour24 < 12) 0 else 1
        val hour12 = when {
            hour24 == 0 -> 12
            hour24 > 12 -> hour24 - 12
            else -> hour24
        }
        return Triple(amPmIndex, hour12 - 1, minute.coerceIn(0, 59))
    }

    fun toTimeString(amPmIndex: Int, hourIndex: Int, minute: Int): String {
        val hour12 = hourIndex + 1
        val hour24 = when {
            amPmIndex == 0 && hour12 == 12 -> 0 // 오전 12시 -> 00시
            amPmIndex == 0 -> hour12
            hour12 == 12 -> 12 // 오후 12시(정오) -> 12시
            else -> hour12 + 12
        }
        return "%02d:%02d".format(hour24, minute)
    }
}

/** 오전/오후 + 시 + 분, 휠 3개를 한 줄에 나란히. A10(생활시간) 화면에서 씀. */
@Composable
fun TimeWheelPicker(
    timeString: String,
    onTimeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (initAmPm, initHour, initMinute) = remember(timeString) { TimeWheelConverter.fromTimeString(timeString) }
    var amPmIndex by remember { mutableStateOf(initAmPm) }
    var hourIndex by remember { mutableStateOf(initHour) }
    var minuteIndex by remember { mutableStateOf(initMinute) }

    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        WheelPicker(
            items = TimeWheelConverter.amPmOptions, selectedIndex = amPmIndex,
            onSelectedChange = {
                amPmIndex = it
                onTimeChange(TimeWheelConverter.toTimeString(amPmIndex, hourIndex, minuteIndex))
            },
            modifier = Modifier.weight(1f),
        )
        WheelPicker(
            items = TimeWheelConverter.hourOptions, selectedIndex = hourIndex,
            onSelectedChange = {
                hourIndex = it
                onTimeChange(TimeWheelConverter.toTimeString(amPmIndex, hourIndex, minuteIndex))
            },
            modifier = Modifier.weight(1f),
        )
        WheelPicker(
            items = TimeWheelConverter.minuteOptions, selectedIndex = minuteIndex,
            onSelectedChange = {
                minuteIndex = it
                onTimeChange(TimeWheelConverter.toTimeString(amPmIndex, hourIndex, minuteIndex))
            },
            modifier = Modifier.weight(1f),
        )
    }
}
