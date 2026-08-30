package kr.tmtn.app.ui.state

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kr.tmtn.app.designsystem.*

/* ==================================================== 앱을 막는 화면들 */

/**
 * H02 · 네트워크 오류.
 *
 * 탭 안에서 뜬다 — 하단 탭은 그대로 두어 **다른 탭으로 빠져나갈 길**을 남긴다.
 * 한 화면이 안 불러와졌다고 앱 전체를 막지 않는다.
 */
@Composable
fun NetworkErrorView(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    TmtnMessageView(
        modifier = modifier,
        title = "연결이 끊겼어요",
        body = "잠시 신호가 닿지 않았어요. 잠깐 뒤에 다시 눌러 주세요.",
        note = "이미 끝낸 기록은 사라지지 않아요.",
        primaryLabel = "다시 시도",
        onPrimary = onRetry,
    )
}

/**
 * H03 · 서버 점검.
 *
 * 사용자가 할 수 있는 게 없는 상황이다. 버튼을 두지 않는 대신
 * **언제 오면 되는지**를 반드시 적는다. 기다리라고만 하고 끝내지 않는다.
 */
@Composable
fun ServerMaintenanceScreen(until: String? = null) {
    BlockingScreen {
        TmtnMessageView(
            title = "잠시 점검 중이에요",
            body = until?.let { "$it 까지 정비하고 있어요. 조금 뒤에 다시 열어 주세요." }
                ?: "곧 돌아옵니다. 조금 뒤에 다시 열어 주세요.",
        )
    }
}

/**
 * H04 · 업데이트 필요.
 *
 * 낡은 버전으로는 더 못 쓰는 상황이라 앱을 막는다.
 * 대신 **왜 막는지**를 밝힌다 — 이유 없이 막으면 강요로만 느껴진다.
 */
@Composable
fun UpdateRequiredScreen(onOpenStore: () -> Unit) {
    BlockingScreen {
        TmtnMessageView(
            title = "새 버전이 나왔어요",
            body = "기록을 안전하게 지키려면 새 버전이 필요해요.",
            primaryLabel = "스토어 열기",
            onPrimary = onOpenStore,
        )
    }
}

/**
 * H07 · 세션 만료 · 재로그인.
 *
 * **로그아웃당했다는 인상을 주지 않는다.** 사용자가 잘못한 것이 아니라
 * 시간이 지나 다시 확인이 필요한 것뿐이다.
 */
@Composable
fun SessionExpiredScreen(onLogin: () -> Unit) {
    BlockingScreen {
        TmtnMessageView(
            title = "다시 로그인해 주세요",
            body = "안전을 위해 한동안 쓰지 않으면 로그인이 풀려요.",
            note = "기록은 그대로 있어요. 로그인하면 이어서 볼 수 있습니다.",
            primaryLabel = "로그인하기",
            onPrimary = onLogin,
        )
    }
}

/**
 * 앱을 막는 화면의 껍데기. 하단 탭 없이 화면을 다 차지한다.
 *
 * 글자가 커져도 잘리지 않게 **스크롤을 열어 둔다** —
 * 시스템 글자 크기를 가장 크게 둔 기기에서는 이만한 문장도 한 화면을 넘긴다.
 */
@Composable
private fun BlockingScreen(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        // 앱바는 위에 붙어 있어야 한다. 가운데 정렬에 끌려 들어가면
        // 제목이 화면 한복판까지 내려와 어디가 위인지 알 수 없게 된다.
        TmtnTopBar("틈튼")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
        ) {
            content()
        }
    }
}

/* ==================================================== H06 · 앱 종료 확인 */

/**
 * H06 · 앱 종료 확인.
 *
 * **오늘 할 일이 남아 있을 때만** 묻는다. 다 끝낸 사람에게 또 묻는 것은 성가심이다.
 * 무게는 "더 볼래요" 쪽에 두지 않는다 — 나가려는 사람을 붙잡되 막지는 않는다.
 */
@Composable
fun ExitConfirmDialog(onStay: () -> Unit, onExit: () -> Unit) {
    AlertDialog(
        onDismissRequest = onStay,
        containerColor = TmtnColor.Background,
        shape = TmtnShape.Sheet,
        title = { Text("앱을 닫을까요?", style = TmtnText.Title, color = TmtnColor.OnSurface) },
        text = {
            Text(
                "오늘 카드를 아직 안 끝냈어요. 한 장이면 5분이면 돼요.",
                style = TmtnText.Body,
                color = TmtnColor.OnSurfaceVariant,
            )
        },
        confirmButton = { TmtnQuietButton("닫기", fillWidth = false, onClick = onExit) },
        dismissButton = { TmtnQuietButton("더 볼래요", fillWidth = false, onClick = onStay) },
    )
}

/* ==================================================== H05 · 접근성 */

/**
 * H05 · 접근성 · 큰 글자 대응.
 *
 * 앱 안에 글자 크기 조절을 따로 두지 않는다. **기기 설정을 그대로 따른다** —
 * 두 군데서 따로 조절하면 어느 쪽이 이겼는지 알 수 없고, 이미 기기를 큰 글자로
 * 맞춰 둔 사람이 앱에서 또 맞춰야 한다.
 *
 * 이 화면이 하는 일은 **지금 어떻게 보이고 있는지 알려 주고,
 * 어디서 바꾸는지 길을 알려 주는 것**이다.
 */
@Composable
fun AccessibilityScreen(onOpenSystemSettings: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TmtnTopBar("접근성", onBack = onBack)

        Column(
            Modifier.padding(horizontal = TmtnSpace.ScreenMargin),
            verticalArrangement = Arrangement.spacedBy(TmtnSpace.S16),
        ) {
            TmtnCardBox {
                Text("글자 크기", style = TmtnText.Label, color = TmtnColor.OnSurface)
                Text(
                    "틈튼은 기기에 맞춰 둔 글자 크기를 그대로 따릅니다. " +
                        "크게 키워도 화면이 잘리지 않아요.",
                    style = TmtnText.Body, color = TmtnColor.OnSurfaceVariant,
                )
                Spacer(Modifier.height(TmtnSpace.S4))
                Text("이렇게 보입니다", style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant)
                Text("오늘 카드 한 장", style = TmtnText.Title, color = TmtnColor.OnSurface)
                Text("작은 행동 하나면 충분해요", style = TmtnText.Body, color = TmtnColor.OnSurfaceVariant)
                TmtnOutlinedButton("기기 설정 열기", onClick = onOpenSystemSettings)
            }

            TmtnCardBox {
                Text("색으로만 알리지 않아요", style = TmtnText.Label, color = TmtnColor.OnSurface)
                Text(
                    "기록 달력은 색뿐 아니라 선 모양으로도 갈라 둡니다. " +
                        "쉰 날은 실선, 빠진 날은 점선이에요.",
                    style = TmtnText.Body, color = TmtnColor.OnSurfaceVariant,
                )
                Spacer(Modifier.height(TmtnSpace.S8))
                CalendarLegend()
            }

            Spacer(Modifier.height(TmtnSpace.S24))
        }
    }
}
