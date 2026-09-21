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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType

/** D01~D08 "기록" 탭 전체를 관리하는 최상위 컴포저블. 다른 탭 흐름과 동일한 패턴. */
@Composable
fun RecordFlow(
    onGoPickCard: () -> Unit, onOpenJournal: (() -> Unit)? = null,
    state: RecordState = remember { RecordState() },
    onLoad: suspend () -> Unit = {
        if (state.tab.value == RecordTab.MONTHLY) state.loadMonthly() else state.loadWeekly()
    },
) {
    val colors = LocalTmtnColors.current
    val scope = rememberCoroutineScope()
    val pages = rememberSaveableStateHolder()

    LaunchedEffect(Unit) {
        onLoad()
    }

    // 시스템 뒤로가기 - 열린 바텀시트가 있으면 닫음(D03 → 목록).
    BackHandler(enabled = state.showDaySheet.value || state.tab.value == RecordTab.WEEKLY) {
        if (state.showDaySheet.value) state.closeDaySheet() else state.tab.value = RecordTab.MONTHLY
    }

    Box(modifier = Modifier.fillMaxSize()) {
        pages.SaveableStateProvider(state.tab.value) {
        when (state.tab.value) {
            RecordTab.MONTHLY -> MonthlyCalendarScreen(state, scope, onGoPickCard,
                onPeriodSelected = { period -> state.tab.value = period; scope.launch { onLoad() } })
            RecordTab.WEEKLY -> WeeklyReportScreen(state, scope, onGoPickCard, onOpenJournal)
        }
        }

        if (state.showDaySheet.value) {
            DayDetailSheet(state, scope)
        }

    }
}
