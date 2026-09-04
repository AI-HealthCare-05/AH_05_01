from fastapi import FastAPI
from fastapi.responses import ORJSONResponse

from app.apis.v1 import v1_routers
from app.core import config
from app.core.config import Env
from app.core.db.databases import initialize_tortoise

# ⚠️ 2026-09-03 리뷰 반영: PROD인데 SMTP 환경변수가 비어있으면, 예전엔 요청이 들어올 때마다
# 조용히 500을 내며 "이메일 발송 설정이 되어 있지 않습니다"로만 실패했음(email_verification.py
# 참고). 그러면 배포가 이미 끝난 뒤에야, 그것도 누군가 회원가입을 시도해야만 문제를 알아챔.
# 배포 설정 실수는 "조용히 우회 모드로 내려가는 게 제일 위험하다"는 리뷰 지적대로, 앱이 아예
# 기동을 못 하게 fail-fast로 바꿔서 배포 파이프라인 단계에서 바로 드러나게 함.
if config.ENV == Env.PROD and not (config.SMTP_USERNAME and config.SMTP_APP_PASSWORD):
    raise RuntimeError(
        "PROD 환경인데 SMTP_USERNAME/SMTP_APP_PASSWORD가 설정되지 않았습니다. "
        "이 상태로 기동하면 이메일 인증이 동작하지 않습니다. .env를 확인하세요."
    )

app = FastAPI(
    default_response_class=ORJSONResponse, docs_url="/api/docs", redoc_url="/api/redoc", openapi_url="/api/openapi.json"
)
initialize_tortoise(app)

app.include_router(v1_routers)
