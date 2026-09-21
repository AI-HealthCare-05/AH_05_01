"""F21(문의 남기기) 모델.

⚠️ 답변은 이 버전에선 다루지 않음(운영자가 이메일로 직접 답장하는 걸로 우선 처리).
"기기 정보 함께 보내기"는 기종·앱버전만 저희가 그때그때 텍스트로 채워 보내는 걸로 하고,
서버가 파싱해서 쓰는 구조 아님 -> device_info를 그냥 자유 텍스트로 받음.
"""

import uuid

from tortoise import fields, models


class Inquiry(models.Model):
    id = fields.UUIDField(primary_key=True, default=uuid.uuid4)
    user = fields.ForeignKeyField("models.User", related_name="inquiries")
    topic = fields.CharField(max_length=30)  # "CARD_CHALLENGE" / "RECORD_DAM" / "ACCOUNT_LOGIN" / "OTHER"
    content = fields.TextField()
    device_info = fields.CharField(max_length=200, null=True)  # "기종 · 앱 버전 · 오류 코드" 자유 텍스트
    created_at = fields.DatetimeField(auto_now_add=True)

    class Meta:
        table = "inquiries"
