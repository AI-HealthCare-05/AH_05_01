package kr.tmtn.app.domain.model

/**
 * 댐 재료 5종.
 *
 * 화면에 보이는 이름과 설명은 **피그마 G01 · G02 를 따른다.**
 * 재료 색은 `TmtnColor.Material*` 에 있고, 아이콘은 `MaterialIcon()` 이 그린다.
 *
 * TODO(기획): 데이터 쪽(`build_missions_json.py` · `seed_from_csv.py`) 은
 *  다짐흙을 "식사 · 수면 · 생활리듬", 새잎을 "기록" 으로 분류하고 있어 이 화면 문구와 다르다.
 *  어느 쪽이 맞는지 정해지면 한쪽을 고쳐야 한다. (지금은 화면 문구 = 피그마 기준)
 */
enum class DamMaterial(val displayName: String, val area: String) {
    BRANCH("나뭇가지", "움직임 · 유산소"),
    STONE("받침돌", "근력"),
    WATERWAY("물길", "수분"),
    SOIL("다짐흙", "생활리듬"),
    LEAF("새잎", "식사 · 기록"),
    ;

    companion object {
        /**
         * 기록에 적힌 보상 이름을 재료로 바꾼다.
         * 코드(`BRANCH`) 와 한글 이름(`나뭇가지`) 을 **둘 다** 받는다 —
         * `TmtnComponents.materialKey()` 와 같은 규칙이다.
         */
        fun from(raw: String?): DamMaterial? {
            val v = raw?.trim().orEmpty()
            if (v.isEmpty()) return null
            val upper = v.uppercase()
            return when {
                upper == "BRANCH" || v.contains("나뭇가지") -> BRANCH
                upper == "STONE" || v.contains("받침돌") -> STONE
                upper == "WATERWAY" || upper == "WATER" || v.contains("물길") -> WATERWAY
                upper == "SOIL" || upper == "EARTH" || v.contains("다짐흙") -> SOIL
                upper == "LEAF" || v.contains("새잎") -> LEAF
                else -> null
            }
        }

        /** 화면에 늘 이 순서로 나온다 (G01 · G02 · D02 공통) */
        val displayOrder: List<DamMaterial> = entries.toList()
    }
}

/**
 * 댐이 자란 정도.
 *
 * 문턱값은 피그마 **G03 · 댐이 자라는 순서** 그대로다.
 * **단계는 내려가지 않는다.** 쉬어도 쌓인 재료는 줄지 않는다.
 */
data class DamStage(
    /** 0 = 아직 1단계 전, 1~5 */
    val index: Int,
    /** 단계 이름. `index` 가 0 이면 null */
    val name: String?,
    /** 지금까지 모은 재료 수 */
    val collected: Int,
    /** 이번 단계가 시작된 재료 수 */
    val from: Int,
    /** 다음 단계 문턱. 마지막 단계면 null */
    val nextAt: Int?,
) {
    /** 다음 단계까지 남은 재료 수. 마지막 단계면 null */
    val toNext: Int? get() = nextAt?.let { (it - collected).coerceAtLeast(0) }

    /** 이번 단계 안에서의 진행률 0f ~ 1f. `MeterBar(progress)` 에 그대로 넣으면 된다 */
    val progress: Float
        get() {
            val to = nextAt ?: return 1f
            val span = (to - from).coerceAtLeast(1)
            return ((collected - from).toFloat() / span).coerceIn(0f, 1f)
        }

    /** "3단계 · 몸통 연결하기". 아직 1단계 전이면 null — 화면에서 다른 문구를 쓴다 */
    val label: String? get() = name?.let { "${index}단계 · $it" }

    /** "41 / 70". 마지막 단계면 "120" */
    val ratioLabel: String get() = nextAt?.let { "$collected / $it" } ?: "$collected"
}

object DamStages {
    /** G03 기준. 재료 5개부터 1단계, 15개 2단계, 35개 3단계, 70개 4단계, 120개 5단계 */
    val thresholds = listOf(5, 15, 35, 70, 120)
    val names = listOf("물길 터 잡기", "기둥 세우기", "몸통 연결하기", "물길 안정화", "튼튼한 댐 완성")

    fun of(collected: Int): DamStage {
        val safe = collected.coerceAtLeast(0)
        val index = thresholds.count { safe >= it }        // 0 ~ 5
        return DamStage(
            index = index,
            name = names.getOrNull(index - 1),
            collected = safe,
            from = if (index == 0) 0 else thresholds[index - 1],
            nextAt = thresholds.getOrNull(index),
        )
    }
}
