package kr.tmtn.app.domain.model

/** 미션 유형. CSV `유형` 칸과 1:1. 화면 분기의 기준이 되는 가장 중요한 값이다. */
enum class MissionType {
    /** 사용자가 직접 "했어요" 를 눌러야 기록된다. 타이머 없음. (125장) */
    SELF_CHECK,

    /** 사용자가 시작을 누르면 앱이 시간만 센다. 일시정지 버튼 있음. (42장) */
    SELF_TIMER,

    /** 모델이 움직임을 인식한 동안에만 시간이 흐른다. (17장) */
    MODEL_ACTIVE_TIME,

    /** 모델이 이동 거리를 측정한다. (6장) */
    MODEL_DISTANCE,

    /** 모델이 오른 계단 칸수를 측정한다. (10장) */
    MODEL_STAIR_COUNT,

    ;

    /** true 면 모델 측정 화면(ModelMissionScreen), false 면 자가 수행 화면으로 간다. */
    val isModelMeasured: Boolean
        get() = this == MODEL_ACTIVE_TIME || this == MODEL_DISTANCE || this == MODEL_STAIR_COUNT

    companion object {
        fun from(raw: String): MissionType =
            entries.firstOrNull { it.name == raw } ?: SELF_CHECK
    }
}

/** 오행 축. 색으로 구분하지 않는다 — 한자 + 라벨 + 고정 순서(木火土金水)로 식별한다. */
enum class Axis(val hanja: String, val label: String, val order: Int) {
    WOOD("木", "뻗는 기운", 1),
    FIRE("火", "타오르는 기운", 2),
    EARTH("土", "다지는 기운", 3),
    METAL("金", "가다듬는 기운", 4),
    WATER("水", "흐르는 기운", 5),
    ;

    /** 접근성: 화면에서도 TalkBack 에서도 색만으로 구분하지 않는다. */
    fun accessibleText(): String = "$hanja $label"

    companion object {
        fun from(raw: String): Axis = entries.firstOrNull { it.name == raw } ?: WOOD
        val displayOrder: List<Axis> = entries.sortedBy { it.order }
    }
}

/** assets/missions.json 한 줄. 카드의 원본 템플릿이다. */
data class Mission(
    val id: String,
    val axis: Axis,
    val area: String,
    val action: String,
    val type: MissionType,
    val modelBacked: Boolean,
    val measureKeys: List<String>,
    val startCondition: String,
    val completeRule: String,
    val places: List<String>,
    val numMin: Int,
    val numMax: Int,
    val numStep: Int,
    val unit: String,
    val timeSlots: List<String>,
    val fortune: String,
    val lineTemplate: String,
    val safetyNote: String,
    val safetyTag: String,
    val seniorSafe: Boolean,
    val rewardMaterial: String,
    val rewardName: String,
    val rewardHint: String,
)

/**
 * 템플릿에 실제 숫자·장소를 채운 "오늘의 카드 한 장".
 * 숫자는 항상 목표치다 (미션템플릿설계서 2.2).
 */
data class MissionCard(
    val mission: Mission,
    val targetNumber: Int,
    val place: String,
) {
    val id: String get() = mission.id
    val type: MissionType get() = mission.type

    /** "{place}에서 {num}{unit} 천천히 걷기!" 를 채운 문장 */
    val oneLine: String
        get() = mission.lineTemplate
            .replace("{place}", place)
            .replace("{num}", targetNumber.toString())
            .replace("{unit}", mission.unit)

    val title: String get() = "$place ${targetNumber}${mission.unit} ${mission.action}"
    val goalText: String get() = "목표 ${targetNumber}${mission.unit}"

    /** 시간 기반 미션의 목표 초. 시간 미션이 아니면 0. */
    val targetSeconds: Int
        get() = when (mission.unit) {
            "분" -> targetNumber * 60
            "초" -> targetNumber
            else -> 0
        }

    /** 거리 기반 미션의 목표 미터. */
    val targetMeters: Int get() = if (mission.unit == "m") targetNumber else 0

    /** 계단 미션의 목표 칸수. */
    val targetStairs: Int get() = if (mission.unit == "칸") targetNumber else 0
}
