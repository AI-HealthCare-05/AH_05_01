package com.tmtn.app

import android.app.Application
import android.content.Context

/**
 * ⚠️ 2026-08-31 수정: 예전에 온보딩 화면 만들기 전, 테스트용으로 넣어뒀던
 * "앱 시작 시 테스트 계정으로 자동 로그인" 코드를 여기서 제거함.
 *
 * 이게 남아있어서 실제로 벌어진 문제: 앱 데이터를 지우거나 앱을 재설치해도,
 * TmtnApplication.onCreate()가 MainActivity.onCreate()보다 먼저 실행되면서
 * 매번 테스트 계정으로 새로 로그인시켜버렸음. 그래서 온보딩을 한 번도 안 거쳤는데도
 * TokenHolder.accessToken이 항상 채워져 있어서, MainActivity가 "이미 로그인된 상태"로
 * 판단해 곧바로 홈 화면(MAIN)으로 건너뛰었던 것.
 *
 * 지금은 실제 온보딩(A03~A16)에서 진짜 로그인/회원가입을 하므로 이 자동 로그인이
 * 필요 없음 — 특별히 다른 초기화 작업이 생기기 전까지는 이 클래스가 거의 빈 채로
 * 있어도 정상.
 */
class TmtnApplication : Application() {

    companion object {
        // ⚠️ 2026-09-17 추가(QA #3 후속 - 저장 대기 복원) - CardHomeState는 Context를 갖고
        // 있지 않아서(순수 상태 클래스), 프로세스 종료 후에도 남아있어야 하는 로컬 대기
        // 요청(PendingExerciseAction, Room DB)에 접근하려면 앱 전역 Context가 필요함.
        // 여기 Application.onCreate에서 한 번만 채워두고 AppDatabase.getInstance() 호출에
        // 재사용한다. TmtnAudio가 하던 것과 같은 패턴.
        lateinit var appContext: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        com.tmtn.app.audio.TmtnAudio.initialize(this)
    }
}
