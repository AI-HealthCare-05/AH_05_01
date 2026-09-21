package com.tmtn.app.ui.cardhome

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tmtn.app.R
import com.tmtn.app.ui.onboarding.TmtnPrimaryButton
import com.tmtn.app.ui.theme.*

/** 운동 완료 API가 확인한 그날의 횟수. 센서 목표 도달이나 저장 대기는 포함하지 않는다. */
data class HomeExerciseProgress(val serviceDate: String, val completed: Int, val remaining: Int)

internal enum class HomeCelebration { CARD, EXTRA_ONE, EXTRA_TWO }

internal fun homeCelebration(serviceDate: String?, progress: HomeExerciseProgress?): HomeCelebration = when {
    progress == null || serviceDate == null || progress.serviceDate != serviceDate -> HomeCelebration.CARD
    progress.completed >= 2 -> HomeCelebration.EXTRA_TWO
    progress.completed == 1 -> HomeCelebration.EXTRA_ONE
    else -> HomeCelebration.CARD
}

/** 완료한 카드는 보조 링크로 남기고, 홈의 중심은 틈튼이의 응원으로 전환한다. */
@Composable
internal fun HomeCompletionPanel(state: CardHomeState, onViewCard: () -> Unit, onOpenDam: () -> Unit) {
    val celebration = homeCelebration(state.cardServiceDate.value, state.homeExerciseProgress.value)
    val allDone = celebration == HomeCelebration.EXTRA_TWO
    val progressKnown = state.homeExerciseProgress.value?.serviceDate == state.cardServiceDate.value && state.cardServiceDate.value != null
    val active = state.activeExerciseSession.value != null
    val title = if (allDone) "오늘의 실천, 모두 해냈어!" else "오늘도 고생했어!"
    val message = when {
        allDone -> "틈새운동 두 번까지 해냈어!\n오늘은 충분히 멋졌어."
        active -> "하던 운동도 네 속도로 이어가자.\n내가 여기서 응원할게!"
        celebration == HomeCelebration.EXTRA_ONE -> "틈새운동까지 한 번 해냈네!\n한 번 더 해봐도 좋고, 쉬어도 좋아."
        !progressKnown -> "오늘의 미션을 해냈어.\n차곡차곡 잘하고 있어!"
        else -> "틈새운동도 가볍게 도전해볼래?\n오늘은 여기까지여도 충분해."
    }
    Column(Modifier.fillMaxWidth().testTag("home-completion")
        .background(ColorBackground, RoundedCornerShape(24.dp))
        .border(1.dp, TmtnHomeColor.Border, RoundedCornerShape(24.dp)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (allDone) "오늘의 미션 + 틈새운동 2회 완료" else "오늘의 미션 완료",
            style = TmtnType.label, color = TmtnHomeColor.Forest, textAlign = TextAlign.Center)
        Image(painterResource(if (allDone) R.drawable.beaver_sensor_complete else R.drawable.beaver_cheer),
            if (allDone) "두 번의 틈새운동까지 마친 나를 축하하는 틈튼이" else "오늘의 실천을 응원하는 틈튼이",
            Modifier.fillMaxWidth().height(160.dp).testTag("home-completion-beaver"))
        Text(title, style = TmtnType.title, color = TmtnHomeColor.Forest, textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() })
        Text(message, style = TmtnType.body, color = ColorOnSurface, textAlign = TextAlign.Center)
        if (allDone) TmtnPrimaryButton("내 댐 보러 가기", onOpenDam)
        TextButton(onClick = onViewCard, enabled = !state.isLoading.value, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("완료한 카드 보기", style = TmtnType.label, color = ColorOnSurfaceVariant)
        }
    }
}
