from fastapi.exceptions import HTTPException
from pydantic import EmailStr
from starlette import status
from starlette.concurrency import run_in_threadpool
from tortoise.exceptions import IntegrityError

from app.core.jwt.tokens import AccessToken, RefreshToken
from app.core.logger import default_logger
from app.core.oauth.google import GoogleIdentity, verify_google_id_token
from app.core.utils.security import hash_password, verify_password
from app.dtos.auth import LoginRequest
from app.models.users import User
from app.repositories.user_repository import UserRepository
from app.services.email_verification import EmailVerificationService
from app.services.jwt import JwtService


class GoogleLinkRequiredError(Exception):
    """구글 로그인 중 "같은 이메일의 기존 계정을 찾았고, 연결하려면 사용자 확인이 필요함".

    ⚠️ 2026-09-10: 예전에는 서비스가 여기서 바로 연결해버렸습니다. 이제는 이 예외를
    던지고, 라우터가 409(LINK_REQUIRED)로 바꿔 앱에 되돌려줍니다. 사용자가 확인 화면에서
    "연결하기"를 누르면 앱이 같은 ID 토큰을 link_confirmed=true로 다시 보내고, 그때
    실제로 연결됩니다.

    HTTPException을 바로 쓰지 않는 이유: 여기는 "무엇이 필요한가"만 알리는 자리이고,
    그걸 어떤 상태 코드·본문으로 표현할지는 API 계층의 결정이라 섞지 않습니다.
    """

    def __init__(self, email: str) -> None:
        super().__init__(email)
        self.email = email


class GoogleSignupRequiredError(Exception):
    """처음 보는 구글 계정이라 가입이 필요함 - **아직 계정을 만들지 않았음**.

    ⚠️ 2026-09-10: 이 앱은 "약관 동의 전에는 계정을 만들지 않는다"를 지킵니다. 이메일
    가입도 A04(인증번호 입력)에서는 계정을 안 만들고, A06 동의를 받은 뒤에야
    confirmEmailVerification()을 부릅니다(ui/onboarding/RegistrationProgress.kt).
    건강정보 이용 동의는 개인정보보호법상 민감정보라 이 순서가 뒤집히면 안 됩니다.

    구글 로그인만 예외로 두면 "약관 동의 전에 만들어진 계정"이 생기므로, 여기서도 같은
    규칙을 씁니다. 라우터가 409(SIGNUP_REQUIRED)로 바꿔 돌려주고, 앱이 동의를 받은 뒤
    같은 ID 토큰에 signup_confirmed=true를 붙여 다시 부르면 그때 생성합니다.
    """

    def __init__(self, email: str) -> None:
        super().__init__(email)
        self.email = email


class AuthService:
    def __init__(self):
        self.user_repo = UserRepository()
        self.jwt_service = JwtService()
        self.email_verification_service = EmailVerificationService()

    async def signup_after_email_verification(self, email: str, code: str, password: str) -> User:
        """v2: A03(이메일+비밀번호) -> A04(인증번호) 흐름의 마지막 단계.
        인증번호가 맞아야만 계정이 실제로 생성됨. 이름/성별/생년월일 등은 아직 없음
        (온보딩 후속 단계에서 PATCH /users/me로 채워짐)."""

        await self.check_email_exists(email)
        await self.email_verification_service.verify_code(email, code)

        user = await self.user_repo.create_user_minimal(
            email=email,
            hashed_password=hash_password(password),
        )
        return user

    async def authenticate(self, data: LoginRequest) -> User:
        email = str(data.email)
        user = await self.user_repo.get_user_by_email(email)
        if not user:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST, detail="이메일 또는 비밀번호가 올바르지 않습니다."
            )

        # ⚠️ 2026-09-09 추가(구글 로그인): 구글로만 가입한 계정은 hashed_password가 None임.
        # 이 검사를 안 하면 아래 verify_password()에 None이 들어가서 500이 납니다.
        #
        # 문구를 "이메일 또는 비밀번호가 올바르지 않습니다"로 통일하지 않은 이유:
        # 그러면 구글로 가입한 사람이 비밀번호를 아무리 정확히 쳐도 영원히 같은 실패
        # 메시지만 보게 되어 빠져나올 길이 없습니다. 가입 여부가 드러나는 건 맞지만,
        # 이메일 열거 방지는 인증번호 요청 엔드포인트에서 이미 처리하고 있고
        # (services/email_verification.py), 여기서만 숨겨봐야 실효가 없습니다.
        if user.hashed_password is None:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="구글 계정으로 가입된 이메일입니다. 구글로 계속하기를 눌러 주세요.",
            )

        if not verify_password(data.password, user.hashed_password):
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST, detail="이메일 또는 비밀번호가 올바르지 않습니다."
            )

        if not user.is_active:
            raise HTTPException(status_code=status.HTTP_423_LOCKED, detail="비활성화된 계정입니다.")

        return user

    async def login(self, user: User) -> dict[str, AccessToken | RefreshToken]:
        await self.user_repo.update_last_login(user.id)
        return self.jwt_service.issue_jwt_pair(user)

    async def check_email_exists(self, email: str | EmailStr) -> None:
        if await self.user_repo.exists_by_email(email):
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="이미 사용중인 이메일입니다.")

    async def email_exists(self, email: str | EmailStr) -> bool:
        """⚠️ 2026-09-03 리뷰 반영: check_email_exists()는 존재하면 바로 409를 던져서
        가입 여부를 그대로 드러냄(이메일 열거 취약점). request_email_verification처럼
        "가입 여부와 무관하게 항상 같은 응답"을 만들어야 하는 곳에서는 이 non-raising
        버전을 써서 호출 쪽에서 분기 처리함."""

        return await self.user_repo.exists_by_email(email)

    async def check_phone_number_exists(self, phone_number: str) -> None:
        if await self.user_repo.exists_by_phone_number(phone_number):
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="이미 사용중인 휴대폰 번호입니다.")

    # ===== 2026-09-09 추가: 구글 계정 연동 로그인 =====

    async def login_with_google(
        self, raw_id_token: str, link_confirmed: bool = False, signup_confirmed: bool = False
    ) -> tuple[User, bool]:
        """구글 ID 토큰으로 로그인/가입. (user, is_new_user)를 돌려줌.

        계정을 찾는 순서가 중요합니다.

        1) google_sub로 조회 - 이미 연결된 계정. 이메일이 바뀌었어도 여기서 찾힙니다.
        2) 같은 이메일의 기존 계정 - 이메일로 먼저 가입했던 사람이 구글로 들어온 경우.
           **연결 여부를 사용자에게 먼저 확인받습니다**(GoogleLinkRequiredError). 확인을 받고
           다시 들어오면(link_confirmed=True) 그 계정에 google_sub를 붙입니다 - 계정을
           새로 만들지 않습니다.
        3) 둘 다 없으면 신규 - 단, **약관 동의를 받기 전에는 만들지 않습니다**
           (GoogleSignupRequiredError). 동의 후 signup_confirmed=True로 다시 들어오면 생성.

        1번을 2번보다 먼저 보는 이유: 구글에서 이메일을 바꾼 사용자가 다시 들어왔을 때,
        이메일부터 찾으면 "없네" 하고 계정을 하나 더 만들어 버립니다.

        ⚠️ email_verified 검사(core/oauth/google.py)는 확인 단계가 생겨도 여전히 필수입니다.
        확인 화면은 "계정 주인이 동의했는가"를 묻는 것이지 "이 사람이 이메일 주인인가"를
        증명해주지 않습니다. 미인증 이메일을 믿으면, 남의 주소를 등록한 구글 계정을 만든
        공격자에게 확인 화면을 띄우고 그가 "연결하기"를 눌러 계정을 가져갈 수 있습니다.

        link_confirmed는 2번 경로에서만, signup_confirmed는 3번 경로에서만 의미가 있습니다.
        이미 연결된 계정(1번)은 확인할 것이 없어서 두 값과 무관하게 그대로 로그인됩니다.
        """

        # ⚠️ 2026-09-10: verify_google_id_token은 동기 함수이고, 공개키 캐시가 비어 있으면
        # 구글로 네트워크를 탑니다. async 함수 안에서 그냥 부르면 그 왕복 동안 이벤트 루프가
        # 멈춰서 **다른 사용자들의 요청까지 전부** 대기합니다. 스레드풀로 넘겨서 격리합니다.
        identity: GoogleIdentity = await run_in_threadpool(verify_google_id_token, raw_id_token)

        linked = await self.user_repo.get_user_by_google_sub(identity.subject)
        if linked is not None:
            self._ensure_active(linked)
            return linked, False

        existing = await self.user_repo.find_user_for_google_link(identity.email)
        if existing is not None:
            # 비활성 계정 확인을 연결 확인보다 먼저 합니다 - 어차피 못 쓰는 계정을 두고
            # "연결할까요?"를 물어봐야 사용자가 수락한 뒤에 다시 거절당할 뿐입니다.
            self._ensure_active(existing)
            if not link_confirmed:
                raise GoogleLinkRequiredError(existing.email)
            await self.user_repo.link_google_sub(existing.id, identity.subject)
            default_logger.info("구글 계정 연결(사용자 확인함): user_id=%s", existing.id)
            refreshed = await self.user_repo.get_user(existing.id)
            return (refreshed or existing), False

        if not signup_confirmed:
            # ⚠️ 여기서 계정을 만들면 "약관 동의 전에 생성된 계정"이 됩니다. 앱이 A06 동의를
            # 받은 뒤 signup_confirmed=True로 다시 부를 때까지 아무것도 저장하지 않습니다.
            raise GoogleSignupRequiredError(identity.email)

        try:
            created = await self.user_repo.create_user_from_google(
                email=identity.email,
                google_sub=identity.subject,
                name=identity.name,
            )
        except IntegrityError:
            # 같은 구글 계정으로 거의 동시에 두 번 요청이 들어온 경우(더블탭 등).
            # google_sub UNIQUE 덕분에 뒤 요청만 실패하므로, 먼저 만들어진 계정을 다시
            # 읽어서 정상 로그인으로 처리합니다 - 사용자 입장에선 결과가 같아야 합니다.
            raced = await self.user_repo.get_user_by_google_sub(identity.subject)
            if raced is None:
                raise
            self._ensure_active(raced)
            return raced, False

        default_logger.info("구글 신규 가입: user_id=%s", created.id)
        return created, True

    @staticmethod
    def _ensure_active(user: User) -> None:
        if not user.is_active:
            raise HTTPException(status_code=status.HTTP_423_LOCKED, detail="비활성화된 계정입니다.")
