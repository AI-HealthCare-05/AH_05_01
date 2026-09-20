"""One authenticated result snapshot owns both rank and SHAP explanation."""

from datetime import date, datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator

from model_service.activity_reference import coaching_hint
from model_service.shap_story import make_story

RELEASE_PIN = "bdca54917705cde75fc2d1b275c49a91ebfef05f56f45472d29e4b97ba77c2e9"
BACKGROUND_PIN = "62407693c4570a4e0bd15a6a8122cdcad59c4a180a127996b7520bcc97df0fb9"
FEATURES = [
    "age_years",
    "sex_code",
    "height_cm",
    "weight_kg",
    "leisure_aerobic_moderate_equivalent_min_week",
    "strength_days_week",
]


class ShapContribution(BaseModel):
    model_config = ConfigDict(allow_inf_nan=False)
    key: str
    label: str
    value: float


class PersonalShapStory(BaseModel):
    # Older cached snapshots are accepted, then regenerated after validation.
    version: Literal["tmtn-shap-story-v1", "tmtn-shap-story-v2"]
    domain: Literal["diabetes", "hypertension"]
    title: str
    summary: str
    intro_kind: Literal["challenge_encouragement"] | None = None
    calculation_title: str | None = None
    calculation_summary: str | None = None
    context_note: str | None = None
    focus_keys: list[str]
    activity_text: str
    activity_keys: list[str]
    comparison_text: str
    comparison_kind: Literal["different_direction", "different_leader", "different_next_tier", "shared_pattern"]
    comparison_keys: list[str]
    scope_note: str


class PersonalXaiDomain(BaseModel):
    model_config = ConfigDict(allow_inf_nan=False)
    domain: Literal["diabetes", "hypertension"]
    rank: int = Field(ge=1, le=100)
    rank_range: list[int]
    reference_group: str = Field(min_length=1)
    reference_n: int = Field(gt=0)
    output_target: Literal["calibrated_reference_score_before_peer_percentile"]
    unit: Literal["internal_reference_score_point"]
    base_value: float = Field(ge=0, le=100)
    output_value: float = Field(ge=0, le=100)
    feature_order: list[str]
    contributions: list[ShapContribution]
    background_sha256: str
    background_rows: Literal[300]
    explainer: Literal["ExactExplainer"]
    masker: Literal["Independent"]
    link: Literal["identity"]
    shap_version: Literal["0.49.1"]
    additivity_error: float = Field(ge=0, le=1e-9)
    narrative: PersonalShapStory | None = None

    @model_validator(mode="after")
    def validate_explanation(self):
        if self.feature_order != FEATURES or [c.key for c in self.contributions] != FEATURES:
            raise ValueError("SHAP feature order/completeness mismatch")
        if self.background_sha256 != BACKGROUND_PIN:
            raise ValueError("SHAP background mismatch")
        if len(self.rank_range) != 2 or not 1 <= self.rank_range[0] <= self.rank <= self.rank_range[1] <= 100:
            raise ValueError("Rank range mismatch")
        residual = abs(self.base_value + sum(c.value for c in self.contributions) - self.output_value)
        if residual > 1e-9 or abs(residual - self.additivity_error) > 1e-10:
            raise ValueError("SHAP additivity failure")
        return self


class ActivityCoachingHint(BaseModel):
    version: Literal["tmtn-activity-hint-v1"]
    basis: Literal["survey_zero", "survey_reported"]
    title: str
    text: str
    reason: str


class ActivityComparisonCard(BaseModel):
    model_config = ConfigDict(allow_inf_nan=False)
    key: Literal["leisure_aerobic_moderate_equivalent_min_week", "strength_days_week"]
    label: str
    unit: Literal["분", "일"]
    n: int = Field(ge=30)
    mean: float = Field(ge=0)
    value: float = Field(ge=0)
    mean_display: str
    value_display: str
    delta: float
    title: str
    text: str
    comparison_text: str
    unit_note: str
    ci95: list[float]
    topcoded: bool
    coaching_hint: ActivityCoachingHint | None = None

    @model_validator(mode="after")
    def validate_units_and_arithmetic(self):
        strength = self.key == "strength_days_week"
        if self.unit != ("일" if strength else "분"):
            raise ValueError("Activity unit mismatch")
        if strength and (self.mean > 5 or self.value > 5 or int(self.value) != self.value):
            raise ValueError("Activity strength coding mismatch")
        if self.topcoded != (strength and self.value == 5):
            raise ValueError("Activity top-code mismatch")
        expected_mean = f"{self.mean:.{1 if strength else 0}f}".removesuffix(".0")
        expected_value = "5+" if self.topcoded else f"{self.value:g}"
        if self.mean_display != expected_mean or self.value_display != expected_value:
            raise ValueError("Activity displayed number mismatch")
        if abs(self.value - self.mean - self.delta) > 1e-9:
            raise ValueError("Activity difference mismatch")
        if len(self.ci95) != 2 or not 0 <= self.ci95[0] <= self.mean <= self.ci95[1]:
            raise ValueError("Activity uncertainty mismatch")
        self.coaching_hint = ActivityCoachingHint.model_validate(coaching_hint(self.key, self.value))
        return self


class ActivityComparison(BaseModel):
    status: Literal["ready", "insufficient_sample"]
    reference_version: Literal["knhanes-activity-2019-2021-weighted-v1"]
    source_sha256: Literal["9f90ba20624d0afac93fbbb4968ce9e0226946892bf518574b4f4bb35f8ab787"]
    input_revision: str = Field(min_length=1)
    reference_date: str
    group_key: Literal["19-39:1", "19-39:2", "40-64:1", "40-64:2", "65+:1", "65+:2"]
    group_label: str
    n: int = Field(ge=0)
    source_kind: Literal["knhanes_population_survey"]
    years: list[int]
    source_label: str
    source_url: Literal["https://knhanes.kdca.go.kr/knhanes/main.do"]
    scope_note: str
    activity_unit_note: str
    method_note: str
    grouping: str
    cards: list[ActivityComparisonCard]
    survey_recorded_at: datetime | None = None

    @model_validator(mode="after")
    def validate_reference(self):
        date.fromisoformat(self.reference_date)
        if self.survey_recorded_at is not None and self.survey_recorded_at.utcoffset() is None:
            raise ValueError("Survey timestamp must have a timezone")
        if self.years != [2019, 2020, 2021] or len({c.key for c in self.cards}) != len(self.cards):
            raise ValueError("Activity reference mismatch")
        if self.status == "ready" and not self.cards:
            raise ValueError("Activity cards missing")
        if self.status != "ready" and self.cards:
            raise ValueError("Unavailable activity contains cards")
        return self


class PersonalXaiSnapshot(BaseModel):
    model_config = ConfigDict(allow_inf_nan=False)
    schema_version: Literal["tmtn-personal-xai-v1"]
    snapshot_id: str = Field(min_length=1)
    input_revision: str = Field(min_length=1)
    computed_at: datetime
    reference_date: str
    release_sha256: str
    model_version: str = Field(min_length=1)
    reference_version: str = Field(min_length=1)
    formula_version: str = Field(min_length=1)
    input_mapping_version: str = Field(min_length=1)
    display_policy_version: str = Field(min_length=1)
    age_notice: str
    release_stage: Literal["candidate"]
    is_mock: Literal[False]
    status: Literal["ready", "unavailable"]
    reason: str | None = None
    composite_score: float | None = Field(default=None, ge=0, le=100)
    domains: list[PersonalXaiDomain]
    activity_comparison: ActivityComparison | None = None

    @model_validator(mode="after")
    def validate_release(self):
        if self.release_sha256 != RELEASE_PIN or self.computed_at.utcoffset() is None:
            raise ValueError("Snapshot release/time mismatch")
        expected_versions = {
            "model_version": "tuntun_integrated_candidate_v0_1",
            "reference_version": "peer-fixed-model42-unweighted-broad-v0.1-20260907",
            "formula_version": "peer-equal-four-v0.1-candidate",
            "input_mapping_version": "birth-month-app-inputs-v0.2",
            "display_policy_version": "peer-rank100-v0.2",
        }
        if any(getattr(self, key) != value for key, value in expected_versions.items()):
            raise ValueError("Snapshot policy/version mismatch")
        if self.status == "ready" and [d.domain for d in self.domains] != ["diabetes", "hypertension"]:
            raise ValueError("Snapshot domain mismatch")
        if self.status == "unavailable" and self.domains:
            raise ValueError("Unavailable snapshot has explanations")
        if self.domains and len({d.reference_group for d in self.domains}) != 1:
            raise ValueError("Domains cannot use different input groups")
        activity = self.activity_comparison
        if activity is not None and (
            self.status != "ready"
            or activity.input_revision != self.input_revision
            or activity.reference_date != self.reference_date
            or any(d.reference_group != activity.group_key for d in self.domains)
        ):
            raise ValueError("Activity comparison does not belong to this input")
        # Always regenerate from the validated pair. An old or altered narrative
        # must never travel beside a new calculation, even when read from cache.
        domains = [d.model_dump(exclude={"narrative"}) for d in self.domains]
        for domain in self.domains:
            domain.narrative = PersonalShapStory.model_validate(make_story(domains, domain.domain))
        return self


class PersonalXaiResponse(BaseModel):
    status: Literal["pending", "ready", "unavailable"]
    reason: str | None = None
    snapshot: PersonalXaiSnapshot | None = None
    retry_after_seconds: int | None = None
    activity_comparison: ActivityComparison | None = None

    @model_validator(mode="after")
    def validate_activity_binding(self):
        if (self.status == "ready") != (self.snapshot is not None):
            raise ValueError("Response status/snapshot mismatch")
        if self.snapshot is not None and self.snapshot.status != "ready":
            raise ValueError("Response cannot expose an unavailable snapshot")
        if self.activity_comparison is not None and self.reason in (
            "consent_required",
            "input_required",
            "input_invalid",
            "strength_days_unconfirmed",
            "release_review_required",
            "not_configured",
        ):
            raise ValueError("Activity cannot accompany an ineligible response")
        if (
            self.snapshot is not None
            and self.activity_comparison is not None
            and (
                self.activity_comparison.input_revision != self.snapshot.input_revision
                or self.activity_comparison.reference_date != self.snapshot.reference_date
                or any(d.reference_group != self.activity_comparison.group_key for d in self.snapshot.domains)
            )
        ):
            raise ValueError("Response activity input mismatch")
        return self
