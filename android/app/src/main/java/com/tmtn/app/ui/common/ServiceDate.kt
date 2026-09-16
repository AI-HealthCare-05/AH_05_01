package com.tmtn.app.ui.common

import com.tmtn.app.BuildConfig
import com.tmtn.app.network.ApiClient
import java.time.LocalDate

/**
 * ⚠️ 2026-09-04: "쉬어가기"·"회고 저장"·"오늘 회고 있는지 확인" 세 곳이 전부 안드로이드
 * 기기의 실제 날짜(LocalDate.now())를 그대로 서버에 보내고 있었음. 그런데 카드/챌린지
 * 생성은 서버의 service_today()(테스트 중엔 디버그 오프셋이 더해질 수 있음, 프로덕션은
 * 항상 실제 시각)를 기준으로 함 - 둘이 어긋나면(특히 "다음 날로" 테스트 버튼을 쓴 뒤)
 * 방금 완료한 미션은 시뮬레이션한 미래 날짜 카드에 붙고, 회고 메모는 기기의 진짜 오늘
 * 날짜에 따로 붙어서 서로 다른 날짜에 쪼개져 보이는 문제가 있었음
 * ("9월 10일에 완료했는데 회고는 9월 4일에 남아있음" 같은 증상).
 *
 * 디버그 빌드에서만 서버에 "지금 서버가 인식하는 날짜"를 물어보고 그 값을 씀 -
 * 실패하거나(엔드포인트 자체가 PROD에서 404) release 빌드면 그냥 기기 날짜를 그대로 씀
 * (지금까지의 프로덕션 동작과 완전히 동일, 추가 네트워크 호출도 없음).
 */
suspend fun currentServiceDateString(): String {
    if (BuildConfig.DEBUG) {
        runCatching { ApiClient.debugApi.getCurrentDay() }
            .getOrNull()
            ?.takeIf { it.isSuccessful }
            ?.body()
            ?.get("simulated_today")
            ?.toString()
            ?.let { return it }
    }
    return LocalDate.now().toString()
}
