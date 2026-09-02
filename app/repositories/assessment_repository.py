from app.models.assessments import AssessmentJob, AssessmentResult, ModelRelease


class ModelReleaseRepository:
    def __init__(self):
        self._model = ModelRelease

    async def get_active(self) -> ModelRelease | None:
        return await self._model.filter(is_active=True).order_by("-released_at").first()

    async def get_or_create_baseline(self) -> ModelRelease:
        """운영자 승인 절차(9.2절)가 아직 없어, 개발 단계에서는 baseline 버전을 자동 생성.
        ⚠️ 실제 모델 게이트(release_approvals/qa_release_checks) 붙이면 이 함수는 없애야 함."""

        active = await self.get_active()
        if active:
            return active
        return await self._model.create(model_version="v0-baseline-placeholder", is_active=True)


class AssessmentRepository:
    def __init__(self):
        self._job_model = AssessmentJob
        self._result_model = AssessmentResult

    async def create_job(self, user_id, health_input_snapshot_id, model_release_id, status: str) -> AssessmentJob:
        return await self._job_model.create(
            user_id=user_id,
            health_input_snapshot_id=health_input_snapshot_id,
            model_release_id=model_release_id,
            status=status,
        )

    async def create_result(
        self, job_id, score: float, band: str, factors, model_version: str
    ) -> AssessmentResult:
        """uq_result_per_job(OneToOne) — job당 결과 1건만 존재 가능."""

        return await self._result_model.create(
            job_id=job_id, score=score, band=band, factors=factors, model_version=model_version
        )

    async def get_job_with_result(self, job_id) -> AssessmentJob | None:
        return await self._job_model.get_or_none(id=job_id).prefetch_related("result")

    async def get_latest_for_user(self, user_id) -> AssessmentJob | None:
        return (
            await self._job_model.filter(user_id=user_id)
            .order_by("-created_at")
            .prefetch_related("result")
            .first()
        )
