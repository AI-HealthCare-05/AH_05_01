from datetime import datetime
from typing import Annotated

from pydantic import BaseModel, Field

from app.dtos.base import BaseSerializerModel


class InquiryCreateRequest(BaseModel):
    """F21: 문의 남기기. topic은 화면의 칩 4종 중 하나."""

    topic: Annotated[str, Field(pattern="^(CARD_CHALLENGE|RECORD_DAM|ACCOUNT_LOGIN|OTHER)$")]
    content: Annotated[str, Field(min_length=1, max_length=1000)]
    device_info: Annotated[str | None, Field(None, max_length=200)]


class InquiryResponse(BaseSerializerModel):
    id: str
    topic: str
    content: str
    device_info: str | None = None
    created_at: datetime
