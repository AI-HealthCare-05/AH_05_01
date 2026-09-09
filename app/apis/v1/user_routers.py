from typing import Annotated

from fastapi import APIRouter, Depends, status
from fastapi.responses import ORJSONResponse as Response
from fastapi.responses import StreamingResponse

from app.dependencies.security import get_request_user
from app.dtos.users import (
    AccountDeleteRequest,
    EmailChangeRequest,
    PasswordChangeRequest,
    UserInfoResponse,
    UserUpdateRequest,
)
from app.models.users import User
from app.services.data_export import DataExportService
from app.services.users import UserManageService

user_router = APIRouter(prefix="/users", tags=["users"])


@user_router.get("/me", response_model=UserInfoResponse, status_code=status.HTTP_200_OK)
async def user_me_info(
    user: Annotated[User, Depends(get_request_user)],
    user_service: Annotated[UserManageService, Depends(UserManageService)],
) -> Response:
    info = await user_service.get_user_info(user)
    return Response(info.model_dump(), status_code=status.HTTP_200_OK)


@user_router.patch("/me", response_model=UserInfoResponse, status_code=status.HTTP_200_OK)
async def update_user_me_info(
    update_data: UserUpdateRequest,
    user: Annotated[User, Depends(get_request_user)],
    user_manage_service: Annotated[UserManageService, Depends(UserManageService)],
) -> Response:
    updated_user = await user_manage_service.update_user(user=user, data=update_data)
    return Response(UserInfoResponse.model_validate(updated_user).model_dump(), status_code=status.HTTP_200_OK)


@user_router.patch("/me/password", status_code=status.HTTP_204_NO_CONTENT)
async def change_password(
    data: PasswordChangeRequest,
    user: Annotated[User, Depends(get_request_user)],
    user_manage_service: Annotated[UserManageService, Depends(UserManageService)],
) -> None:
    """F16: 비밀번호 변경."""
    await user_manage_service.change_password(user=user, data=data)


@user_router.patch("/me/email", response_model=UserInfoResponse, status_code=status.HTTP_200_OK)
async def change_email(
    data: EmailChangeRequest,
    user: Annotated[User, Depends(get_request_user)],
    user_manage_service: Annotated[UserManageService, Depends(UserManageService)],
) -> Response:
    """F15: 이메일 변경. 먼저 POST /auth/email-verification/request로 새 이메일에
    인증번호를 받은 뒤, 그 코드를 여기로 같이 보내야 함."""
    updated_user = await user_manage_service.change_email(user=user, data=data)
    return Response(UserInfoResponse.model_validate(updated_user).model_dump(), status_code=status.HTTP_200_OK)


@user_router.delete("/me", status_code=status.HTTP_204_NO_CONTENT)
async def delete_account(
    data: AccountDeleteRequest,
    user: Annotated[User, Depends(get_request_user)],
    user_manage_service: Annotated[UserManageService, Depends(UserManageService)],
) -> None:
    """F17/F18: 계정 삭제. 비밀번호 재확인 후 실제로 지움(되돌릴 수 없음)."""
    await user_manage_service.delete_account(user=user, data=data)


@user_router.delete("/me/records", status_code=status.HTTP_204_NO_CONTENT)
async def delete_records_only(
    user: Annotated[User, Depends(get_request_user)],
    user_manage_service: Annotated[UserManageService, Depends(UserManageService)],
) -> None:
    """F13: 기록만 삭제. 계정(이메일)은 그대로 두고 기록·입력값·재료·댐만 지움."""
    await user_manage_service.delete_records_only(user=user)


@user_router.get("/me/export", status_code=status.HTTP_200_OK)
async def export_my_data(
    user: Annotated[User, Depends(get_request_user)],
    data_export_service: Annotated[DataExportService, Depends(DataExportService)],
) -> StreamingResponse:
    """F14: 내 데이터 내보내기. 요청 즉시 CSV로 만들어서 바로 내려줌(동기 버전)."""
    csv_text = await data_export_service.build_csv(user)
    return StreamingResponse(
        iter([csv_text.encode("utf-8-sig")]),  # BOM 포함 - 엑셀에서 한글 안 깨지게
        media_type="text/csv",
        headers={"Content-Disposition": "attachment; filename=tmtn_my_data.csv"},
    )
