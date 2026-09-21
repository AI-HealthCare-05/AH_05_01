from datetime import datetime
from uuid import UUID

from pydantic import BaseModel

from app.dtos.base import BaseSerializerModel


class ConsentRequest(BaseModel):
    purpose: str  # ConsentPurpose enum 값 문자열
    document_version: str


class ConsentResponse(BaseSerializerModel):
    id: UUID
    purpose: str
    document_version: str
    status: str
    agreed_at: datetime
    withdrawn_at: datetime | None = None
