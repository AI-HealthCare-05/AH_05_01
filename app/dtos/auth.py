from typing import Annotated

from pydantic import AfterValidator, BaseModel, EmailStr, Field

from app.core.validators import validate_password


class EmailVerificationRequestRequest(BaseModel):
    """A03: 이메일 회원가입 첫 화면 - 이메일+비밀번호 입력, 인증번호 요청."""

    email: Annotated[EmailStr, Field(max_length=40)]


class EmailVerificationConfirmRequest(BaseModel):
    """A04: 인증번호 6자리 입력 -> 확인되면 이 정보로 계정 생성.
    비밀번호는 A03에서 이미 입력했지만, 재전송/새로고침 등에도 안전하게 여기서 다시 받음."""

    email: Annotated[EmailStr, Field(max_length=40)]
    code: Annotated[str, Field(min_length=6, max_length=6)]
    password: Annotated[str, Field(min_length=8), AfterValidator(validate_password)]


class LoginRequest(BaseModel):
    email: EmailStr
    password: Annotated[str, Field(min_length=8)]


class LoginResponse(BaseModel):
    access_token: str


class TokenRefreshResponse(LoginResponse): ...


class GoogleLoginRequest(BaseModel):
    """⚠️ 2026-09-09 추가(구글 계정 연동 로그인).

    앱은 구글이 서명한 ID 토큰 **원문만** 보냅니다. 이메일이나 이름을 앱이 같이 보내서
    서버가 그걸 믿는 구조면 안 됩니다 - 아무나 남의 이메일을 적어 보낼 수 있으니까요.
    신원 정보는 서버가 토큰을 열어서 직접 꺼냅니다(core/oauth/google.py).

    max_length는 방어용입니다. 구글 ID 토큰은 보통 1KB 안팎이라 4096이면 충분하고,
    이걸 안 걸면 거대한 문자열을 던져서 검증 로직을 괴롭힐 수 있습니다.
    """

    id_token: Annotated[str, Field(min_length=1, max_length=4096)]

    # ⚠️ 아래 두 플래그는 같은 원리입니다. 서버가 "그냥 진행하면 안 되는 상황"을 만나면
    # 409로 한 번 되돌려주고, 사용자가 앱에서 확인하면 **같은 ID 토큰을** 해당 플래그만
    # true로 바꿔서 다시 보냅니다.
    #
    # 서버에 중간 상태를 저장하지 않는 게 핵심입니다. 구글 ID 토큰 자체가 "이 사람이 이
    # 이메일의 주인"이라는 증명이고 보통 1시간쯤 유효하므로, 확인 화면을 띄우는 동안
    # 그대로 재사용하면 됩니다. 별도 임시 토큰을 발급하면 그걸 저장·만료 관리할 자리가
    # 또 필요해집니다.

    #: 같은 이메일로 이미 가입한 계정이 있을 때 - 409 LINK_REQUIRED 후 "연결하기" 수락.
    link_confirmed: bool = False

    #: ⚠️ 2026-09-10 추가(동의 없이 계정 만들지 않기): 처음 보는 구글 계정일 때 서버는
    #: 계정을 **만들지 않고** 409 SIGNUP_REQUIRED를 돌려줍니다. 앱이 약관 동의(A06)를
    #: 받은 뒤에야 이 값을 true로 해서 다시 부르고, 그때 계정이 생성됩니다.
    #:
    #: 이메일 가입이 이미 이 규칙을 지키고 있어서 맞춘 것입니다 - A04(인증번호)에서
    #: 계정을 만들지 않고, A06 동의 후 submitConsents()에서 confirmEmailVerification()을
    #: 부릅니다(RegistrationProgress). 구글만 예외로 두면 "약관 동의 전에 만들어진 계정"이
    #: 생기는데, 건강정보 이용 동의는 민감정보라 그 순서가 뒤집히면 안 됩니다.
    signup_confirmed: bool = False


class SocialLoginResponse(LoginResponse):
    """구글 로그인 응답. 액세스 토큰은 이메일 로그인과 똑같고, 앱이 다음에 어느 화면으로
    가야 하는지 판단할 수 있게 한 가지를 더 알려줍니다.

    is_new_user: 이번 요청으로 계정이 **처음 만들어졌는지**. true면 앱은 홈이 아니라
        온보딩(A06 약관 동의)부터 태워야 합니다. 구글이 신원을 확인해 줬다고 해서
        약관·개인정보·건강정보 동의까지 받은 게 아니고, 그건 법적으로 우리가 따로 받아야
        하는 항목이기 때문입니다.
    """

    is_new_user: bool


class GoogleActionRequiredResponse(BaseModel):
    """구글 로그인을 그대로 진행하면 안 되는 상황을 알리는 409 응답 (2026-09-10).

    **로그인 실패가 아닙니다.** 앱은 에러 배너를 띄우지 말고 code에 따라 갈라야 합니다.

    - LINK_REQUIRED  : 같은 이메일로 이미 가입한 계정이 있음.
                       -> 연결 확인 다이얼로그. 수락하면 link_confirmed=true로 재요청.
    - SIGNUP_REQUIRED: 처음 보는 구글 계정이라 가입이 필요함. **계정은 아직 안 만들어졌음.**
                       -> 약관 동의(A06)부터. 동의 후 signup_confirmed=true로 재요청하면
                          그때 계정이 생성됨.

    email을 가리지 않고 그대로 내려주는 이유: 이 응답을 받는 사람은 방금 구글에서 그
    이메일의 소유자임을 증명한 사람입니다. 자기 이메일이라 가릴 이유가 없고, 오히려
    어떤 계정에 연결되는지 정확히 보여줘야 판단할 수 있습니다.

    detail을 문자열로 같이 넣어두는 이유: 앱이 이 코드를 아직 모르는 버전이어도(구버전
    앱) 일반 에러 처리 경로에서 사람이 읽을 수 있는 문구가 나오게 하기 위함.
    """

    code: str
    email: str
    detail: str
