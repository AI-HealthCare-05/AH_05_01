package kr.tmtn.app.domain.model

/** 미션 하나를 끝낸 기록. 서버가 생기면 그대로 올라갈 모양이다. */
data class DailyRecord(
    val date: String,            // 2026-08-27
    val missionId: String,
    val title: String,
    val axis: Axis,
    val type: MissionType,
    val targetNumber: Int,
    val unit: String,
    /** 실제로 채운 양 (분·m·칸·회) */
    val achieved: Int,
    val completedAtLabel: String,
    val rewardName: String,
    val rewardHint: String,
    /** 모델이 측정한 기록인지, 사용자가 직접 확인한 기록인지 */
    val measuredByModel: Boolean,
    /** 모델이 아직 임시 구현일 때 true. 화면에 샘플 표시가 붙는다. */
    val fromPlaceholderModel: Boolean = false,
)
