package kr.tmtn.app.domain.ml

/**
 * ┌──────────────────────────────────────────────────────────────────┐
 * │  모델을 넣는 사람에게                                             │
 * │                                                                  │
 * │  이 패키지가 "모델 자리" 입니다. 화면 코드는 여기 인터페이스만    │
 * │  알고 있고, 실제 구현이 무엇인지 모릅니다.                        │
 * │                                                                  │
 * │  넣는 방법:                                                      │
 * │   1) 아래 세 인터페이스 중 하나를 구현한 클래스를 만든다          │
 * │   2) ModelRegistry 에서 Stub 대신 그 클래스를 반환하게 바꾼다     │
 * │   3) info.isPlaceholder = false 로 둔다 (그래야 화면의            │
 * │      "샘플 값" 안내가 사라진다)                                   │
 * │                                                                  │
 * │  화면은 절대 고치지 않아도 됩니다.                                │
 * └──────────────────────────────────────────────────────────────────┘
 */

/** 지금 붙어 있는 모델이 무엇인지 화면과 로그에 정직하게 드러내기 위한 값. */
data class ModelInfo(
    val name: String,
    val version: String,
    /** true = 아직 진짜 모델이 아님. 화면에 "샘플" 안내가 뜬다. */
    val isPlaceholder: Boolean,
    val note: String = "",
)

/** 모델 호출 결과. "값이 없음" 과 "실패" 를 서로 다른 상태로 다룬다. */
sealed interface ModelResult<out T> {
    data class Ready<T>(val value: T, val info: ModelInfo) : ModelResult<T>

    /** 아직 모델이 없거나 입력이 모자라 계산하지 않은 상태. 오류가 아니다. */
    data class NotReady(val reason: String) : ModelResult<Nothing>

    /** 모델은 있는데 실행이 실패한 상태. */
    data class Failed(val reason: String) : ModelResult<Nothing>
}

enum class Sex { MALE, FEMALE }
