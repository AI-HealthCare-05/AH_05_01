package com.tmtn.app.ui.record

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType

/** D01~D08 "기록" 탭 전체를 관리하는 최상위 컴포저블. 다른 탭 흐름과 동일한 패턴. */
@Composable
fun RecordFlow(onGoPickCard: () -> Unit) {
    val colors = LocalTmtnColors.current
    val state = remember { RecordState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        state.loadMonthly()
    }

    // 시스템 뒤로가기 - 열린 바텀시트가 있으면 그것부터 닫음(D06 → D03 → 목록).
    BackHandler(enabled = state.showRestSheet.value || state.showDaySheet.value) {
        if (state.showRestSheet.value) {
            state.closeRestSheet()
        } else {
            state.closeDaySheet()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (state.tab.value) {
            RecordTab.MONTHLY -> MonthlyCalendarScreen(state, scope, onGoPickCard)
            RecordTab.WEEKLY -> WeeklyReportScreen(state, scope, onGoPickCard)
        }

        if (state.showDaySheet.value) {
            DayDetailSheet(state, scope)
        }

        state.errorMessage.value?.let { message ->
            Surface(
                color = colors.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp),
            ) {
                Text("⚠️ $message", style = TmtnType.caption, color = colors.error, modifier = Modifier.padding(12.dp))
            }
        }

        if (state.isLoading.value) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary)
            }
        }
    }
}
