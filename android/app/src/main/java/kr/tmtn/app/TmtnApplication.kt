package kr.tmtn.app

import android.app.Application
import kr.tmtn.app.data.AppContainer
import kr.tmtn.app.domain.ml.ModelRegistry

class TmtnApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // 모델 3종을 여기서 한 번만 연결한다. 교체 지점은 ModelRegistry 한 곳뿐이다.
        ModelRegistry.init(this)
        container = AppContainer(this)
    }
}
