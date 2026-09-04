package com.tmtn.app.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType

/**
 * ⚠️ 기록(D그룹)·댐(G그룹)·참고(E그룹)·마이(F그룹) 화면은 아직 안 만들어서,
 * 하단 내비 탭 이동 자체는 되도록 임시로 채워둔 화면. 각 그룹 화면 만들면 교체할 것.
 * 백엔드 API는 이미 다 있음(GET /companion, GET/PATCH /records 계열, /assessments 등) — 화면만 없음.
 */
@Composable
fun PlaceholderTabScreen(tab: MainTab) {
    val colors = LocalTmtnColors.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("${tab.label} 화면", style = TmtnType.title, color = colors.onSurface)
            Text(
                "아직 준비 중이에요.",
                style = TmtnType.body, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
            )
        }
    }
}
