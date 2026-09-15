package com.tmtn.app.network.model

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val access_token: String
)

/**
 * ⚠️ 2026-09-09 추가(구글 계정 연동 로그인).
 *
 * 구글이 서명한 ID 토큰 **원문만** 보냅니다. 이메일이나 이름을 여기 같이 담아 보내고
 * 서버가 그걸 믿는 구조면 안 됩니다 - 앱은 얼마든지 조작될 수 있으니까요.
 * 신원 정보는 서버가 토큰을 열어서 직접 꺼냅니다(app/core/oauth/google.py).
 */
data class GoogleLoginRequest(
    val id_token: String,
    /**
     * 같은 이메일로 이미 가입한 계정이 있을 때 - 서버가 409 LINK_REQUIRED로 되돌려주고,
     * 사용자가 "연결하기"를 누르면 **같은 id_token을** 이 값만 true로 바꿔서 다시 보냄.
     */
    val link_confirmed: Boolean = false,
    /**
     * ⚠️ 2026-09-10: 처음 보는 구글 계정일 때 서버는 계정을 **만들지 않고** 409
     * SIGNUP_REQUIRED를 돌려줌. 약관 동의(A06)를 받은 뒤에야 이 값을 true로 해서 다시
     * 부르고, 그때 계정이 생성됨.
     *
     * 이메일 가입이 이미 이 규칙을 지키고 있어서 맞춘 것임 - A04(인증번호)에서 계정을
     * 만들지 않고 A06 동의 후 confirmEmailVerification()을 부름(RegistrationProgress).
     * 구글만 예외로 두면 "약관 동의 전에 만들어진 계정"이 생기는데, 건강정보 이용 동의는
     * 민감정보라 그 순서가 뒤집히면 안 됨.
     */
    val signup_confirmed: Boolean = false
)

/**
 * 409 응답 본문. **로그인 실패가 아님** - 에러 배너로 띄우지 말고 code로 갈라야 함.
 *
 *  - LINK_REQUIRED   : 같은 이메일의 기존 계정 있음 -> 연결 확인 다이얼로그
 *  - SIGNUP_REQUIRED : 처음 보는 계정, **아직 생성 안 됨** -> 약관 동의(A06)부터
 */
data class GoogleActionRequiredResponse(
    val code: String,
    val email: String,
    val detail: String
)

/**
 * 구글 로그인 성공 응답. access_token은 이메일 로그인과 똑같고, is_new_user 하나가 더 붙음.
 * is_new_user=true는 "방금 동의를 마치고 계정이 만들어졌다"는 뜻이라 SIGNUP_COMPLETE로 감.
 */
data class SocialLoginResponse(
    val access_token: String,
    val is_new_user: Boolean
)
