package com.tmtn.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.theme.TmtnType
import com.tmtn.app.ui.theme.LocalTmtnColors

/** 움직임 인식 미션의 분류. 센서 종류나 측정 규칙은 바꾸지 않는다. */
fun isModelRecognitionExecType(execType: String?): Boolean = execType in setOf(
    "SENSOR_WALKING_DURATION",
    "SENSOR_RUNNING_DURATION",
    "SENSOR_RUNNING_DISTANCE",
)

/** 미션 종류는 문구로 안내한다. 센서 전용 테두리와 살구색 배경은 사용하지 않는다. */
@Composable
fun ModelMissionBadge() {
    val colors = LocalTmtnColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .background(colors.surface, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .semantics { contentDescription = "움직임 인식 모델로 측정하는 미션" },
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.size(6.dp).clip(CircleShape).background(colors.primary),
        )
        Text("틈튼 움직임 인식", style = TmtnType.caption, color = colors.primary)
    }
}
