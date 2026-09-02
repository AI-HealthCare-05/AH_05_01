"""SQLModel.metadata가 모든 테이블을 인식하려면 여기서 전부 import 해둬야 함.
(Tortoise ORM: TORTOISE_APP_MODELS 리스트에 모듈 단위로 등록하는 방식과 병행 사용 가능)

users.py는 기존 리포지토리 파일 그대로이며, 이 프로젝트에서 새로 추가하는 모델은 아래 8개 파일.
v3: 실제 구현하신 action_cards.py/device_tokens.py/mission_records.py/mission_sessions.py의
로직을 공식 ERD 이름(mission_template_versions/fcm_device_tokens/sensor_measurement_events/
challenges)으로 이식 완료.
"""

from .accounts import (
    DeletionJob,
    DeletionJobItem,
    EmailVerificationRequest,
    PasswordResetToken,
    Session,
    UserConsent,
)
from .assessments import AssessmentJob, AssessmentResult, ModelRelease, TmtnIndexResult
from .cards import CardOption, DailyCardSelection, DailyCardSet
from .challenges import (
    Challenge,
    ChallengeEvent,
    PointLedger,
    SensorChallengeConfig,
    SensorMeasurementEvent,
)
from .companion import (
    CardFitFeedback,
    CompanionState,
    MissionReplacement,
    RecommendationPreference,
)
from .health import ExerciseHabitSnapshot, HealthInputSnapshot, MissionTemplateVersion
from .mission_recommendation import ElementRecommendationScore
from .notifications import (
    DailyActionSummary,
    FcmDeviceToken,
    NotificationSetting,
    ProfileAccessibility,
)
from .prediction import ApprovedModelVersion, PredictionResult
from .records import DailyRecordNote
from .release import ModelAuditLog, QaReleaseCheck, ReleaseApproval

__all__ = [
    "DeletionJob",
    "DeletionJobItem",
    "EmailVerificationRequest",
    "PasswordResetToken",
    "Session",
    "UserConsent",
    "HealthInputSnapshot",
    "ExerciseHabitSnapshot",
    "MissionTemplateVersion",
    "CardOption",
    "DailyCardSelection",
    "DailyCardSet",
    "Challenge",
    "ChallengeEvent",
    "PointLedger",
    "SensorChallengeConfig",
    "SensorMeasurementEvent",
    "AssessmentJob",
    "AssessmentResult",
    "ModelRelease",
    "TmtnIndexResult",
    "CardFitFeedback",
    "CompanionState",
    "MissionReplacement",
    "RecommendationPreference",
    "DailyActionSummary",
    "FcmDeviceToken",
    "NotificationSetting",
    "ProfileAccessibility",
    "ModelAuditLog",
    "QaReleaseCheck",
    "ReleaseApproval",
    "ElementRecommendationScore",
    "ApprovedModelVersion",
    "PredictionResult",
    "DailyRecordNote",
]
