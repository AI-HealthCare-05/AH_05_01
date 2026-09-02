from fastapi import HTTPException, status

from app.dtos.assessments import AssessmentJobResponse, AssessmentResultResponse
from app.models.assessments import AssessmentJobStatus
from app.models.users import User
from app.repositories.assessment_repository import AssessmentRepository, ModelReleaseRepository
from app.repositories.health_repository import HealthInputRepository

# ⚠️ 아래 점수 계산은 실제 검증된 모델이 아니라 구조 확인용 placeholder입니다.
# 기능명세서 §9.2가 요구하는 "누설 점검·holdout·calibration·하위집단 성능·재현성·안전 문구
# 승인"을 전혀 거치지 않았으므로, 사용자에게 실제로 노출하기 전에 반드시 교체해야 합니다.
# (지난 대화에서 "동기 처리로 단순화" 하기로 한 방향을 그대로 반영: 큐 없이 요청 즉시 계산)


def _placeholder_score(input_values: dict) -> tuple[float, str, list[dict]]:
    height_cm = input_values.get("height_cm")
    weight_kg = input_values.get("weight_kg")

    if not height_cm or not weight_kg:
        # 최소 입력값이 없으면 참고 점수 자체를 계산하지 않음 (화면 28 "입력보완" 흐름과 연결)
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
            detail="참고 분석에 필요한 최소 입력값(height_cm, weight_kg)이 없습니다.",
        )

    bmi = weight_kg / ((height_cm / 100) ** 2)
    if bmi < 23:
        band = "LOW"
    elif bmi < 27:
        band = "MODERATE"
    else:
        band = "HIGH"

    score = round(min(bmi * 3, 100), 1)  # 임의 스케일링. 실제 모델로 교체 필요
    factors = [{"name": "bmi", "value": round(bmi, 1), "rank": 1}]
    return score, band, factors


class AssessmentService:
    def __init__(self):
        self.assessment_repo = AssessmentRepository()
        self.model_release_repo = ModelReleaseRepository()
        self.health_repo = HealthInputRepository()

    async def create_assessment(self, user: User) -> AssessmentJobResponse:
        snapshot = await self.health_repo.get_latest(user.id)
        if snapshot is None:
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
                detail="신체정보를 먼저 입력해야 참고 분석을 받을 수 있습니다.",
            )

        model_release = await self.model_release_repo.get_or_create_baseline()

        job = await self.assessment_repo.create_job(
            user_id=user.id,
            health_input_snapshot_id=snapshot.id,
            model_release_id=model_release.id,
            status=AssessmentJobStatus.PENDING,
        )

        try:
            score, band, factors = _placeholder_score(snapshot.input_values)
        except HTTPException:
            job.status = AssessmentJobStatus.FAILED
            await job.save(update_fields=["status"])
            raise

        await self.assessment_repo.create_result(
            job_id=job.id, score=score, band=band, factors=factors, model_version=model_release.model_version
        )
        job.status = AssessmentJobStatus.DONE
        await job.save(update_fields=["status"])

        refreshed = await self.assessment_repo.get_job_with_result(job.id)
        return await self._to_response(refreshed)

    async def get_latest(self, user: User) -> AssessmentJobResponse:
        job = await self.assessment_repo.get_latest_for_user(user.id)
        if job is None:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="분석 이력이 없습니다.")
        return await self._to_response(job)

    async def _to_response(self, job) -> AssessmentJobResponse:
        result = await job.result if job.status == AssessmentJobStatus.DONE else None
        result_dto = AssessmentResultResponse.model_validate(result) if result else None
        return AssessmentJobResponse(
            id=str(job.id), status=job.status, created_at=job.created_at, result=result_dto
        )
