package kr.tmtn.app.domain.ml

import android.content.Context
import kr.tmtn.app.domain.ml.placeholder.PlaceholderTmtnIndexScorer
import kr.tmtn.app.domain.ml.placeholder.PlaceholderWaistEstimator
import kr.tmtn.app.domain.ml.placeholder.SensorActivityRecognizer
import kr.tmtn.app.domain.ml.placeholder.SimulatedActivityRecognizer

/**
 * ★★★ 모델을 갈아끼우는 곳은 이 파일 하나다. ★★★
 *
 * 화면·ViewModel 은 ModelRegistry 를 통해서만 모델을 받는다.
 * 진짜 모델이 준비되면 아래 세 줄만 바꾸면 앱 전체가 그 모델을 쓴다.
 *
 *   waistEstimator     = MyTfliteWaistEstimator(context)
 *   activityRecognizer = MyActivityModel(context)
 *   indexScorer        = MyKnhanesIndexScorer(context)
 *
 * .tflite / .onnx 파일은 app/src/main/assets/models/ 에 두고,
 * 필요한 의존성은 android/gradle/libs.versions.toml 에 추가한다.
 */
object ModelRegistry {

    private var initialized = false

    lateinit var waistEstimator: WaistEstimator
        private set

    lateinit var activityRecognizer: ActivityRecognizer
        private set

    lateinit var indexScorer: TmtnIndexScorer
        private set

    fun init(context: Context) {
        if (initialized) return
        val appContext = context.applicationContext

        // ── 모델 1: 허리둘레 추정 ─────────────────────────────────
        waistEstimator = PlaceholderWaistEstimator()

        // ── 모델 2: 센서 기반 행동 인식 ───────────────────────────
        // 권한은 화면에서 물어본다. 여기서는 "센서가 아예 없는 기기인가" 만 본다.
        // (권한 미허용은 Blocked 로 화면에 안내되고 '직접 체크' 로 내려간다)
        val sensor = SensorActivityRecognizer(appContext)
        activityRecognizer = if (sensor.hasHardware()) sensor else SimulatedActivityRecognizer()

        // ── 모델 3: 틈튼지수 ──────────────────────────────────────
        indexScorer = PlaceholderTmtnIndexScorer()

        initialized = true
    }

    /** 화면에 "아직 샘플 값이에요" 안내를 띄울지 판단한다. */
    fun anyPlaceholder(): Boolean =
        waistEstimator.info.isPlaceholder ||
            activityRecognizer.info.isPlaceholder ||
            indexScorer.info.isPlaceholder
}
