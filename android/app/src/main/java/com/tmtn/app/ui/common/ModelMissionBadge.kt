package com.tmtn.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tmtn.app.ui.theme.TmtnType

/**
 * ⚠️ 2026-09-11 추가 - TMtn_UI_V17 디자인 핸드오프 반영.
 *
 * "실제 움직임 인식 모델로 측정하는 미션"에만 주황(#FF7A1A) 강조를 준다. 대상은 정확히
 * SENSOR_WALKING_DURATION · SENSOR_RUNNING_DURATION · SENSOR_RUNNING_DISTANCE 세 가지 —
 * 케이던스(걷기/뛰기 속도)나 GPS로 "진짜 움직이고 있는지"를 판정하는 유형들이다.
 *
 * TIMER, CHECK, 그리고 걸음수·계단처럼 순수 하드웨어 카운터만 쓰는 유형
 * (SENSOR_STEPS, SENSOR_STEPS_IN_PLACE, SENSOR_FLOORS_CLIMBED)에는 붙이지 않는다 -
 * 이것들은 케이던스/GPS 같은 "모델 판정" 없이 그냥 센서 값을 그대로 카운트만 한다.
 *
 * V17 README 원칙: "모델 표시를 제외한 일반 미션·정보 카드에 주황 테두리를 일괄
 * 적용하지 않는다" - 즉 이 세 exec_type이 아니면 절대 테두리/배지를 안 그려야 한다.
 */
fun isModelRecognitionExecType(execType: String?): Boolean = execType in setOf(
    "SENSOR_WALKING_DURATION",
    "SENSOR_RUNNING_DURATION",
    "SENSOR_RUNNING_DISTANCE",
)

/** V17 스펙: 1.5dp #FF7A1A 테두리, 미션 카드 모서리 24dp. 모델 인식 대상이 아니면 아무
 * 것도 안 그림(테두리 없는 원래 modifier를 그대로 반환) - 호출부에서 매번 분기 안 써도 됨. */
fun Modifier.modelMissionBorder(execType: String?, cornerDp: androidx.compose.ui.unit.Dp = 24.dp): Modifier =
    if (isModelRecognitionExecType(execType)) {
        this.border(1.5.dp, Color(0xFFFF7A1A), RoundedCornerShape(cornerDp))
    } else {
        this
    }

/**
 * V17 스펙: "연한 살구색 면 + 짙은 라벨 + 주황 점". 접근성 읽기는 문구 자체가 이미
 * "움직임 인식 모델로 측정하는 미션"이라 별도 contentDescription 문구를 안 겹치게 함.
 * 인식 중이라는 상태(ACTIVE/AUTO_WAIT 등)는 이 배지가 아니라 화면의 별도 상태 문구로
 * 표시함 - 배지는 "이 미션의 종류"만 알리고, 지금 인식되고 있는지는 다른 곳에서 말함.
 */
@Composable
fun ModelMissionBadge() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .background(Color(0xFFFDECDD), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .semantics { contentDescription = "움직임 인식 모델로 측정하는 미션" },
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFFF7A1A)),
        )
        Text("틈튼 움직임 인식", style = TmtnType.caption, color = Color(0xFF7A3B10))
    }
}
