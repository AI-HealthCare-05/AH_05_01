from fastapi import APIRouter

from app.apis.v1.accessibility_routers import accessibility_router
from app.apis.v1.assessment_routers import assessment_router
from app.apis.v1.auth_routers import auth_router
from app.apis.v1.card_routers import card_router
from app.apis.v1.challenge_routers import challenge_router
from app.apis.v1.companion_routers import companion_router
from app.apis.v1.consent_routers import consent_router
from app.apis.v1.exercise_habit_routers import exercise_habit_router
from app.apis.v1.health_routers import health_router
from app.apis.v1.inquiry_routers import inquiry_router
from app.apis.v1.notification_setting_routers import notification_setting_router
from app.apis.v1.prediction_routers import prediction_router
from app.apis.v1.record_routers import record_router
from app.apis.v1.sensor_routers import sensor_router
from app.apis.v1.user_routers import user_router

v1_routers = APIRouter(prefix="/api/v1")
v1_routers.include_router(auth_router)
v1_routers.include_router(user_router)
v1_routers.include_router(consent_router)
v1_routers.include_router(health_router)
v1_routers.include_router(exercise_habit_router)
v1_routers.include_router(notification_setting_router)
v1_routers.include_router(card_router)
v1_routers.include_router(challenge_router)
v1_routers.include_router(sensor_router)
v1_routers.include_router(assessment_router)
v1_routers.include_router(accessibility_router)
v1_routers.include_router(prediction_router)
v1_routers.include_router(companion_router)
v1_routers.include_router(record_router)
v1_routers.include_router(inquiry_router)
