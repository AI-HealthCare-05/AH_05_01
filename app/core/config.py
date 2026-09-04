import os
import uuid
import zoneinfo
from dataclasses import field
from enum import StrEnum
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict


class Env(StrEnum):
    LOCAL = "local"
    DEV = "dev"
    PROD = "prod"


class Config(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="allow")

    ENV: Env = Env.LOCAL
    SECRET_KEY: str = f"default-secret-key{uuid.uuid4().hex}"
    TIMEZONE: zoneinfo.ZoneInfo = field(default_factory=lambda: zoneinfo.ZoneInfo("Asia/Seoul"))
    TEMPLATE_DIR: str = os.path.join(Path(__file__).resolve().parent.parent, "templates")

    # ⚠️ 2026-09-04: 틈튼지수 실모델(신체·당뇨·고혈압) 로컬 검토용 연결. 별도 Python
    # 3.14.7 프로세스(tuntun_local_service.py, 127.0.0.1)로 떠 있을 때만 사용. 운영
    # 배포 승인 전(PRODUCTION_RELEASE_GATE: BLOCKED)이라 기본값은 비활성(None)이고,
    # .env에 이 값을 채운 사람의 로컬 환경에서만 실모델을 타게 됨 - 값을 안 채우면
    # 지금과 똑같이 Mock으로만 동작해서 운영에는 절대 영향 없음.
    # 모델 패키지 자체(joblib 33MB, 검토용 소스)는 이 저장소에 없음 - 별도 배포 채널
    # (공유 드라이브 등)에서 받아 로컬에 풀고 tuntun_local_service.py를 띄운 뒤 그 주소를
    # 여기 넣을 것. 예: http://127.0.0.1:8765
    TUNTUN_LOCAL_MODEL_URL: str | None = None

    DB_HOST: str = "localhost"
    DB_PORT: int = 3306
    DB_USER: str = "root"
    DB_PASSWORD: str = "pw1234"
    DB_NAME: str = "ai_health"
    DB_CONNECT_TIMEOUT: int = 5
    DB_CONNECTION_POOL_MAXSIZE: int = 10

    COOKIE_DOMAIN: str = "localhost"

    JWT_ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60
    REFRESH_TOKEN_EXPIRE_MINUTES: int = 14 * 24 * 60
    JWT_LEEWAY: int = 5

    # Gmail SMTP (이메일 인증번호 발송용). .env에 실제 값 채우기 전까진 빈 문자열이라
    # EmailVerificationService가 자동으로 "발송 안 함, dev_only_code로 대체" 모드로 동작함.
    SMTP_HOST: str = "smtp.gmail.com"
    SMTP_PORT: int = 587
    SMTP_USERNAME: str = ""  # 발신용 Gmail 주소
    SMTP_APP_PASSWORD: str = ""  # 구글 계정 > 보안 > 앱 비밀번호에서 생성한 16자리 (일반 비밀번호 아님)
    SMTP_FROM_NAME: str = "틈튼 TMTN"
