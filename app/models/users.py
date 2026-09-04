from enum import StrEnum

from tortoise import fields, models


class Gender(StrEnum):
    MALE = "MALE"
    FEMALE = "FEMALE"


class User(models.Model):
    """v2(2026-08-27, 온보딩 화면 재설계 반영):
    - phone_number: 필수 -> 선택(null=True). Figma 어디에도 전화번호 입력 화면이 없음.
    - birthday(전체 날짜) -> birth_year·birth_month로 교체. 팀 원칙("전체 생년월일 수집 안 함")과
      드디어 일치하게 됨.
    - name/gender/birth_year/birth_month/nickname: 전부 선택(null=True). 이메일+비밀번호로 먼저
      가입되고, 프로필은 온보딩 중 별도 단계(PATCH /users/me)에서 채워짐.
    """

    id = fields.BigIntField(primary_key=True)
    email = fields.CharField(max_length=40)
    hashed_password = fields.CharField(max_length=128)
    name = fields.CharField(max_length=20, null=True)
    nickname = fields.CharField(max_length=20, null=True)
    gender = fields.CharEnumField(enum_type=Gender, null=True)
    birth_year = fields.SmallIntField(null=True)
    birth_month = fields.SmallIntField(null=True)  # 1~12
    # ⚠️ 2026-09-04 추가: 틈튼지수 실모델(D0) 입력 계약이 "임신 여부를 명시적으로 알아야만
    # 계산 가능(모르면 nonpregnant로 넘겨짚지 않음)"이라 추가. null=아직 안 물어봄(또는
    # 남성이라 해당 없음). 여성 온보딩(A07)에서만 물어봄 - app/services/tuntun_score_service.py
    # _get_pregnancy_status() 참고.
    is_pregnant = fields.BooleanField(null=True)
    phone_number = fields.CharField(max_length=11, null=True)
    is_active = fields.BooleanField(default=True)
    is_admin = fields.BooleanField(default=False)
    last_login = fields.DatetimeField(null=True)
    created_at = fields.DatetimeField(auto_now_add=True)
    updated_at = fields.DatetimeField(auto_now=True)

    class Meta:
        table = "users"
