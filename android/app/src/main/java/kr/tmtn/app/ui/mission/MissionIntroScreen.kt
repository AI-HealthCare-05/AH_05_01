package kr.tmtn.app.ui.mission

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kr.tmtn.app.designsystem.*
import kr.tmtn.app.domain.ml.BlockReason
import kr.tmtn.app.domain.ml.ModelRegistry
import kr.tmtn.app.domain.ml.RecognizerAvailability
import kr.tmtn.app.domain.model.MissionType
import kr.tmtn.app.ui.TodayViewModel
import kr.tmtn.app.ui.nav.Route

/**
 * ▣ 모델 측정형 미션의 시작 전 안내 화면.
 *
 * 자가 수행형에는 이 화면이 없다. 자동 측정을 켜기 전에
 * "무엇을 읽고 무엇을 읽지 않는지" 를 먼저 밝히기 위한 화면이기 때문이다.
 */
@Composable
fun MissionIntroScreen(today: TodayViewModel, nav: NavHostController) {
    val card = today.picked ?: run { nav.popBackStack(); return }

    var availability by remember { mutableStateOf(ModelRegistry.activityRecognizer.availability()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { availability = ModelRegistry.activityRecognizer.availability() }

    val reads = when (card.type) {
        MissionType.MODEL_ACTIVE_TIME -> "움직인 시간 · 걸음 수"
        MissionType.MODEL_DISTANCE -> "이동 거리 · 걸음 수 · 움직인 시간"
        MissionType.MODEL_STAIR_COUNT -> "계단 칸수 · 걸음 수 · 높이 변화"
        else -> "움직인 시간"
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TmtnTopBar("오늘의 행동", onBack = { nav.popBackStack() }, actionLabel = "중단", onAction = { nav.popBackStack() })

        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(card.title, style = TmtnText.Headline, color = TmtnColor.OnSurface)
            StatusBadge(BadgeState.Info, "이 미션은 자동으로 측정해요")

            TmtnCardBox {
                Text("무엇을 읽고 무엇을 읽지 않나요", style = TmtnText.Label, color = TmtnColor.OnSurface)
                Spacer(Modifier.height(4.dp))
                Text("읽습니다", style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant)
                Text(reads, style = TmtnText.Body, color = TmtnColor.OnSurface)
                Spacer(Modifier.height(6.dp))
                Text("읽지 않습니다", style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant)
                Text("위치 기록 · 심박 · 연락처 · 사진", style = TmtnText.Body, color = TmtnColor.OnSurface)
            }

            TmtnCardBox {
                Text("이렇게 동작해요", style = TmtnText.Label, color = TmtnColor.OnSurface)
                Text("· 시작 버튼을 누른 뒤부터 측정하고, 끝내기를 누르면 멈춰요.", style = TmtnText.Body, color = TmtnColor.OnSurfaceVariant)
                Text("· 움직임이 멈추면 시간이 자동으로 쉬고, 다시 움직이면 이어서 세요.", style = TmtnText.Body, color = TmtnColor.OnSurfaceVariant)
                Text("· 목표를 다 채우면 완료 버튼을 누르지 않아도 기록돼요.", style = TmtnText.Body, color = TmtnColor.OnSurfaceVariant)
            }

            val info = ModelRegistry.activityRecognizer.info
            if (info.isPlaceholder) {
                NoteBox(tone = NoteTone.Notice, title = "아직 샘플 측정이에요", body = info.note)
            }

            when (val a = availability) {
                is RecognizerAvailability.Ready -> {
                    NoteBox(body = "절전 모드나 앱을 강제로 종료하면 측정이 멈출 수 있습니다. 배터리는 조금 더 씁니다.")
                    TmtnFilledButton("측정 시작", onClick = { nav.navigate(Route.MISSION_MODEL) })
                    TmtnQuietButton("직접 체크로 할래요") { nav.navigate(Route.MISSION_SELF) }
                }

                is RecognizerAvailability.Blocked -> {
                    TmtnCardBox(background = TmtnColor.ErrorContainer, border = null) {
                        Text("이 기기에서는 자동 측정을 쓸 수 없어요", style = TmtnText.Label, color = TmtnColor.OnSurface)
                        Spacer(Modifier.height(4.dp))
                        Text("왜 안 되나요", style = TmtnText.Caption, color = TmtnColor.OnSurfaceVariant)
                        a.reasons.forEach { StatRow(it.label, it.detail) }
                    }

                    if (a.reasons.contains(BlockReason.NO_ACTIVITY_PERMISSION) &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ) {
                        TmtnFilledButton("활동 권한 허용하기", onClick = {
                            permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                        })
                    }

                    NoteBox(
                        title = "직접 체크로 진행할 수 있어요",
                        body = "미션 내용과 받는 재료는 그대로입니다. ${card.title} 뒤에 완료 버튼을 눌러 주세요.",
                    )
                    TmtnTonalButton("직접 체크로 진행하기") { nav.navigate(Route.MISSION_SELF) }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
