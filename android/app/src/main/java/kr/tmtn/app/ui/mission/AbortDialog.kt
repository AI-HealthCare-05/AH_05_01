package kr.tmtn.app.ui.mission

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kr.tmtn.app.designsystem.*

/**
 * C19 · 챌린지 중단 확인.
 *
 * 진행 중이던 것을 버리는 일이라 한 번 묻는다.
 * 다만 **탓하지 않는다** — 그만두는 것도 사용자의 선택이다.
 *
 * 무게는 "이어서 하기" 쪽에 둔다. 실수로 나가는 쪽이 손해가 크기 때문이다.
 * 그만두더라도 오늘 카드는 그대로 남아 다시 들어올 수 있다는 점을 밝힌다.
 */
@Composable
fun AbortConfirmDialog(onContinue: () -> Unit, onAbort: () -> Unit) {
    AlertDialog(
        onDismissRequest = onContinue,
        containerColor = TmtnColor.Background,
        shape = TmtnShape.Dialog,
        title = { Text("그만둘까요?", style = TmtnText.Title, color = TmtnColor.OnSurface) },
        text = {
            Text(
                "지금까지 센 것은 사라져요. 오늘 카드는 그대로 있으니 이따 다시 시작할 수 있어요.",
                style = TmtnText.Body,
                color = TmtnColor.OnSurfaceVariant,
            )
        },
        confirmButton = { TmtnQuietButton("이어서 하기", fillWidth = false, onClick = onContinue) },
        dismissButton = { TmtnQuietButton("그만두기", fillWidth = false, onClick = onAbort) },
    )
}
