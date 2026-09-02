from typing import Annotated

from fastapi import APIRouter, Depends, status
from fastapi.responses import ORJSONResponse as Response

from app.dependencies.security import get_request_user
from app.dtos.inquiries import InquiryCreateRequest, InquiryResponse
from app.models.users import User
from app.services.inquiries import InquiryService

inquiry_router = APIRouter(prefix="/inquiries", tags=["inquiries"])


@inquiry_router.post("", response_model=InquiryResponse, status_code=status.HTTP_201_CREATED)
async def create_inquiry(
    data: InquiryCreateRequest,
    user: Annotated[User, Depends(get_request_user)],
    inquiry_service: Annotated[InquiryService, Depends(InquiryService)],
) -> Response:
    inquiry = await inquiry_service.create(user=user, data=data)
    return Response(InquiryResponse.model_validate(inquiry).model_dump(), status_code=status.HTTP_201_CREATED)
