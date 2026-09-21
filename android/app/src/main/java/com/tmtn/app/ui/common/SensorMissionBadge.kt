package com.tmtn.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.theme.ColorSensorLabel
import com.tmtn.app.ui.theme.LocalTmtnColors
import com.tmtn.app.ui.theme.TmtnType

/** 모델 사용 여부와 별개로 하드웨어 측정 미션 전체를 표시한다. */
fun isSensorMissionExecType(execType: String?): Boolean = execType in setOf(
    "SENSOR_STEPS", "SENSOR_STEPS_IN_PLACE", "SENSOR_WALKING_DURATION",
    "SENSOR_RUNNING_DURATION", "SENSOR_RUNNING_DISTANCE", "SENSOR_FLOORS_CLIMBED",
)

@Composable
fun SensorMissionBadge() {
    val colors = LocalTmtnColors.current
    Text("센서형 · 움직임 측정", style = TmtnType.caption,
        color = ColorSensorLabel,
        modifier = Modifier.background(colors.secondaryContainer, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp))
}
