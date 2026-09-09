from fastapi import HTTPException, status
from tortoise.transactions import in_transaction

from app.core.utils.common import normalize_phone_number
from app.core.utils.security import hash_password, verify_password
from app.core.validators.user_validators import validate_password
from app.dtos.users import (
    AccountDeleteRequest,
    EmailChangeRequest,
    PasswordChangeRequest,
    UserInfoResponse,
    UserUpdateRequest,
)
from app.models.assessments import AssessmentJob, TmtnIndexResult
from app.models.cards import DailyCardSet
from app.models.challenges import PointLedger
from app.models.companion import CompanionStageLog, CompanionState, RecommendationPreference
from app.models.health import ExerciseHabitSnapshot, HealthInputSnapshot
from app.models.mission_recommendation import ElementRecommendationScore
from app.models.notifications import DailyActionSummary
from app.models.prediction import PredictionResult
from app.models.records import DailyRecordNote
from app.models.users import User
from app.repositories.user_repository import UserRepository
from app.services.auth import AuthService
from app.services.email_verification import EmailVerificationService


class UserManageService:
    def __init__(self):
        self.repo = UserRepository()
        self.auth_service = AuthService()
        self.email_verification_service = EmailVerificationService()

    async def get_user_info(self, user: User) -> UserInfoResponse:
        """⚠️ 2026-09-07 추가: /users/me. height_cm은 User 모델에 없고
        health_input_snapshots(온보딩 입력, append-only)에서 최신값을 따로 조회해서 채움 -
        안드로이드가 걷기/조깅 케이던스 임계값을 신장 구간표로 계산할 때 씀."""

        info = UserInfoResponse.model_validate(user)
        latest_health = await HealthInputSnapshot.filter(user_id=user.id).order_by("-measured_at").first()
        if latest_health is not None:
            height = (latest_health.input_values or {}).get("height_cm")
            info.height_cm = float(height) if height is not None else None
        return info

    async def update_user(self, user: User, data: UserUpdateRequest) -> User:
        if data.email:
            await self.auth_service.check_email_exists(data.email)
        if data.phone_number:
            normalized_phone_number = normalize_phone_number(data.phone_number)
            await self.auth_service.check_phone_number_exists(normalized_phone_number)
            data.phone_number = normalized_phone_number
        async with in_transaction():
            await self.repo.update_instance(user=user, data=data.model_dump(exclude_none=True))
            await user.refresh_from_db()
        return user

    async def change_password(self, user: User, data: PasswordChangeRequest) -> None:
        """F16: 지금 비밀번호가 맞는지 먼저 확인하고, 새 비밀번호 규칙(8자+대소문자+숫자+특수문자)
        검사한 뒤 교체. HANDOFF.md 원칙("바꾸면 다른 기기 로그인 해제")은 세션 테이블이
        실질적으로 안 쓰이고 있어서(JWT만으로 인증) 이번엔 별도 구현 안 함 — 필요하면
        sessions 테이블을 실제로 검증하는 걸로 나중에 확장할 것."""

        if not verify_password(data.current_password, user.hashed_password):
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="지금 비밀번호가 올바르지 않습니다.")
        validate_password(data.new_password)  # 규칙 안 맞으면 ValueError -> 422로 자동 변환됨
        user.hashed_password = hash_password(data.new_password)
        await user.save(update_fields=["hashed_password"])

    async def change_email(self, user: User, data: EmailChangeRequest) -> User:
        """F15: A04와 같은 인증번호 검증을 재사용해서 새 이메일 소유를 확인.
        검증 성공 = 그 이메일에 접근 가능하다는 뜻이라, 코드가 맞으면 바로 교체."""

        await self.auth_service.check_email_exists(data.new_email)
        await self.email_verification_service.verify_code(data.new_email, data.code)
        user.email = data.new_email.strip().lower()
        await user.save(update_fields=["email"])
        return user

    async def delete_account(self, user: User, data: AccountDeleteRequest) -> None:
        """F17/F18: 비밀번호 재확인 후 삭제. users 테이블 FK가 전부 ON DELETE CASCADE로
        걸려 있어서(2026-08-31 확인), user.delete() 한 번으로 기록·재료·댐·동의 등
        관련 데이터가 DB 레벨에서 자동으로 함께 지워짐."""

        if not verify_password(data.password, user.hashed_password):
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="비밀번호가 올바르지 않습니다.")
        await user.delete()

    async def delete_records_only(self, user: User) -> None:
        """F13: 계정(이메일·비밀번호)은 그대로 두고, 기록·입력값·재료·댐만 지움.
        F13 화면 문구 기준으로 "입력한 값"(생년월일·성별·키·몸무게·주당운동량)도 같이
        지우는 대상이라, User 행 자체에 있는 값(생년월일·성별)도 같이 비움.

        daily_card_sets를 지우면 FK CASCADE로 card_options·selection·challenge·
        challenge_events·point_ledger(challenge_event 경유)·sensor 관련까지 같이
        지워지지만, PredictionResult/AssessmentJob/ElementRecommendationScore는
        health_input_snapshot이 null이어도 존재할 수 있어서(user로 직접 연결) 별도로
        명시해서 지움. notification_settings/accessibility/consents는 "계정" 쪽으로
        보고 그대로 둠."""

        async with in_transaction():
            await DailyCardSet.filter(user=user).delete()
            await HealthInputSnapshot.filter(user=user).delete()
            await ExerciseHabitSnapshot.filter(user=user).delete()
            await PointLedger.filter(user=user).delete()
            await CompanionState.filter(user=user).delete()
            await CompanionStageLog.filter(user=user).delete()
            await DailyRecordNote.filter(user=user).delete()
            await DailyActionSummary.filter(user=user).delete()
            await TmtnIndexResult.filter(user=user).delete()
            await AssessmentJob.filter(user=user).delete()
            await PredictionResult.filter(user=user).delete()
            await ElementRecommendationScore.filter(user=user).delete()
            await RecommendationPreference.filter(user=user).delete()

            user.name = None
            user.nickname = None
            user.gender = None
            user.birth_year = None
            user.birth_month = None
            await user.save(update_fields=["name", "nickname", "gender", "birth_year", "birth_month"])
