package com.tmtn.app.ui.legal

/** Separate UI documents; existing server consent-purpose codes are preserved. */
enum class LegalDocument(val title: String, val purpose: String?) {
    TERMS("서비스 이용약관", "TERMS_OF_SERVICE"),
    PRIVACY_NOTICE("개인정보 처리방침", null),
    PERSONAL_DATA("개인정보 수집 · 이용 동의", "PRIVACY_POLICY"),
    HEALTH_DATA("건강정보 수집 · 이용 동의", "HEALTH_DATA_USAGE"),
    LOCATION("위치정보 수집 · 이용 동의", "LOCATION_DATA_USAGE"),
    ANALYSIS("틈튼지수 산출을 위한 분석", "HEALTH_REFERENCE_ANALYSIS"),
    NOTIFICATIONS("카드 · 미션 알림 수신 동의", "NOTIFICATION");

    companion object {
        fun forPurpose(purpose: String): LegalDocument? = entries.firstOrNull { it.purpose == purpose }
    }
}

data class LegalSection(val title: String, val body: String)
const val LegalOperator = "문홍주"
const val LegalContact = "salaam4040@naver.com"
private val retention = LegalSection("보유 기간", "항목별 보유·파기 기간은 운영자 확인 후 확정본에 명시할 예정이에요. 문의: $LegalContact")
private val optional = LegalSection("선택과 철회", "동의하지 않아도 기본 카드와 기록 기능을 이용할 수 있어요. 내 정보 → 동의 관리에서 선택 동의를 변경할 수 있어요.")
private val required = LegalSection("동의를 거부하면", "현재 회원가입에 필요한 필수 항목이에요. 동의하지 않으면 가입을 완료할 수 없어요.")

/** Review copy. No unverified effective date, retention period or processor claim is published. */
fun LegalDocument.sections(): List<LegalSection> = when (this) {
    LegalDocument.TERMS -> listOf(
        LegalSection("서비스 소개", "오늘의 카드로 작은 행동을 실천하고, 기록과 재료를 모아 댐을 채워요. 틈튼일보에서 한 주와 하루의 기록을 돌아볼 수 있어요."),
        LegalSection("회원과 계정", "만 14세 이상이 가입할 수 있어요. 이메일 또는 Google 계정으로 로그인하며, 내 정보에서 프로필과 운동 정보를 관리해요. 다른 사람의 계정을 사용하거나 기록을 조작하지 말아 주세요."),
        LegalSection("건강 정보의 의미", "틈튼지수와 허리둘레 추정값은 입력한 정보에 따른 참고 정보예요. 질병 진단이나 치료를 대신하지 않아요."),
        LegalSection("기록과 계정 관리", "내 정보에서 데이터 내보내기, 기록 삭제, 계정 삭제를 요청할 수 있어요. 삭제 범위는 각 확인 화면에서 안내해요."),
        LegalSection("운영 및 문의", "운영자 $LegalOperator\n문의 $LegalContact\n서비스 변경·중단, 이용 제한, 책임 범위와 분쟁 처리 조항은 운영자 검토 후 확정본에 반영할 예정이에요.")
    )
    LegalDocument.PRIVACY_NOTICE -> listOf(
        LegalSection("처리방침은 별도로 확인해요", "개인정보를 어떻게 처리하는지 설명하는 문서예요. 가입 시 선택하는 개인정보 수집·이용 동의와는 별도로 확인할 수 있어요."),
        LegalSection("계정과 기본 정보", "이메일, 이름·별명, 생년월, 성별 등 계정과 프로필 정보를 처리해요. Google 로그인에서는 계정 인증에 필요한 정보를 서버와 확인해요."),
        LegalSection("건강 정보와 실천 기록", "키, 몸무게, 주당 운동량 등 입력값과 카드·틈새 운동의 진행 및 완료 기록을 처리해요. 카드 추천, 기록 표시, 재료·댐 진행, 동의한 분석 기능에 사용해요."),
        LegalSection("내 정보 관리", "동의 관리에서 선택 동의를 변경할 수 있어요. 개인정보·내 데이터에서 내보내기와 기록 삭제를, 계정 화면에서 탈퇴를 요청할 수 있어요. 그 밖의 열람·정정·삭제·처리정지 문의는 $LegalContact 로 보내 주세요."),
        LegalSection("운영자와 개인정보 문의", "$LegalOperator\n$LegalContact"),
        LegalSection("공개 전 확인할 내용", "보유 기간과 파기 절차, 위탁·제3자 제공·국외 이전 여부, 안전성 확보 조치 및 시행일은 운영자가 확인 중이에요. 확정된 처리방침 전문을 공개할 때 다시 안내할 예정이에요.")
    )
    LegalDocument.PERSONAL_DATA -> listOf(
        LegalSection("수집·이용 목적", "회원 확인과 로그인, 프로필 관리, 카드와 실천 기록 제공에 사용해요."),
        LegalSection("수집 항목", "이메일, 이름·별명, 생년월, 성별 및 서비스 이용 기록을 처리해요. Google 로그인 시 계정 인증에 필요한 정보도 확인해요. 건강정보와 위치정보는 각각 별도 항목에서 안내해요."), retention, required
    )
    LegalDocument.HEALTH_DATA -> listOf(
        LegalSection("수집·이용 목적", "신체·운동 정보를 보관하고 카드 추천과 허리둘레 추정 등 개인화 기능에 사용해요. 틈튼지수 분석 동의는 별도 선택 항목이에요."),
        LegalSection("건강정보 항목", "키, 몸무게, 주당 근력운동 일수, 유산소 운동량 등 입력한 신체·운동 정보와 해당 정보로 산출한 값을 처리해요."), retention, required
    )
    LegalDocument.LOCATION -> listOf(
        LegalSection("어디에 쓰나요", "걷기·달리기 등 거리 미션을 측정할 때 위치를 이용해 이동 거리를 계산해요."),
        LegalSection("기기 권한은 별도예요", "이 항목에 동의해도 기기의 위치 권한이 자동으로 허용되지는 않아요. 거리 측정을 시작할 때 권한을 별도로 요청해요. 자동 측정이 어려우면 앱이 제공하는 직접 확인 흐름을 이용할 수 있어요."), retention, optional
    )
    LegalDocument.ANALYSIS -> listOf(
        LegalSection("분석 목적과 사용 정보", "입력한 프로필 및 신체·운동 정보를 바탕으로 틈튼지수와 또래 비교 참고 정보를 계산해요. 결과는 진단이나 질병이 생길 확률이 아니에요."), retention, optional
    )
    LegalDocument.NOTIFICATIONS -> listOf(
        LegalSection("어떤 알림인가요", "설정한 생활시간에 맞춘 카드 알림 등 실천을 돕는 서비스 알림이에요. 광고성 정보 수신에 대한 동의로 사용하지 않아요."),
        LegalSection("기기 알림 권한은 별도예요", "여기서 알림 수신을 선택해도 Android의 알림 권한이 자동으로 켜지지 않아요. 기기 권한과 앱의 알림 설정이 모두 허용되어야 알림을 받을 수 있어요. 진행 중인 측정 상태 알림은 측정 기능의 상태를 안내해요."),
        LegalSection("수집 동의와 구분", "이 항목은 알림 수신에 대한 선택이에요. 개인정보 수집·이용이나 위치정보 이용에 함께 동의하는 항목이 아니에요. 알림 시간은 내 정보 → 생활시간·알림에서 바꿀 수 있어요."), optional
    )
}
