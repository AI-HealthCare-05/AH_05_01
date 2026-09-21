"""Read-only newspaper contract. General knowledge never explains a personal score."""

from datetime import date
from typing import Literal

from pydantic import BaseModel, Field


class JournalSource(BaseModel):
    id: str
    publisher: str
    title: str
    url: str
    edition: str
    locator: str
    checked_on: date


class JournalKnowledgeArticle(BaseModel):
    id: str
    section: Literal["table_column", "movement_column", "daily_column"]
    title: str
    text: str
    revision: str
    content_sha256: str
    numeric_slot: bool
    source: JournalSource
    topic: str = ""
    allowed_claim_scope: str = ""
    audience_min_age: int | None = None
    audience_max_age: int | None = None


class JournalReadingSection(BaseModel):
    section: Literal["table_column", "movement_column", "daily_column"]
    status: Literal["ready", "empty"]
    articles: list[JournalKnowledgeArticle] = Field(default_factory=list)


class JournalModelExplanation(BaseModel):
    # Personal scores and explanations are delivered atomically by their own endpoint.
    status: Literal["separate_endpoint"] = "separate_endpoint"
    endpoint: Literal["/api/v1/tuntun-score/personal"] = "/api/v1/tuntun-score/personal"


class JournalEditorialResponse(BaseModel):
    schema_version: Literal["tmtn-journal-editorial-v1"] = "tmtn-journal-editorial-v1"
    service_date: date
    preview: bool = False
    sections: list[JournalReadingSection]
    related_readings: dict[str, list[JournalKnowledgeArticle]] = Field(default_factory=dict)
    daily_articles: list[JournalKnowledgeArticle] = Field(default_factory=list)
    weekly_articles: list[JournalKnowledgeArticle] = Field(default_factory=list)
    weekly_issue_start: date | None = None
    model_explanation: JournalModelExplanation = Field(default_factory=JournalModelExplanation)
