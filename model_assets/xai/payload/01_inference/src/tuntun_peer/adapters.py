"""Adapters return internal component scores; API uses an explicit allowlist."""
from dataclasses import dataclass
from typing import Protocol
from .registry import CORE

@dataclass(frozen=True)
class Prediction:
    absolute_scores: dict
    model_version: str
    unavailable_reason: str | None = None
    lifestyle_subcomponent_count: int = 2
    source: str = "model_inference"

class ModelAdapter(Protocol):
    def predict(self, normalized_request: dict, reason: str | None) -> Prediction: ...

class LegacyModelAdapter:
    def __init__(self, predictor):
        self.predictor = predictor

    def predict(self, normalized_request, reason):
        response = self.predictor.score(**normalized_request)
        return Prediction({k: response[k+"Score"] for k in CORE}, response["modelVersion"], reason,
                          response["lifestyleAvailableSubcomponentCount"])

class SyntheticAdapter:
    """Explicit fixtures only; never a model error fallback."""
    def __init__(self, scores, model_version="synthetic-v1"):
        self.scores, self.version = scores, model_version

    def predict(self, normalized_request, reason):
        return Prediction(self.scores, self.version, reason, source="synthetic")

