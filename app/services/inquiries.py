from app.dtos.inquiries import InquiryCreateRequest
from app.models.inquiries import Inquiry
from app.models.users import User


class InquiryService:
    async def create(self, user: User, data: InquiryCreateRequest) -> Inquiry:
        return await Inquiry.create(
            user=user, topic=data.topic, content=data.content, device_info=data.device_info
        )
