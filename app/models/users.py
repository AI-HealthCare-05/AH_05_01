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

    v3(2026-09-09, 구글 계정 연동 로그인):
    - hashed_password: 필수 -> 선택(null=True). 구글로만 가입한 계정은 비밀번호가 없음.
    - google_sub 추가. 구글이 부여한 영구 식별자로, 계정 연결의 기준값.
    """

    id = fields.BigIntField(primary_key=True)
    email = fields.CharField(max_length=40)
    # ⚠️ 2026-09-09: null 허용으로 바뀜. "구글로만 가입한 계정"은 비밀번호가 존재하지 않음.
    # 더미 해시를 넣는 방법도 있지만, 그러면 비밀번호 재설정 흐름에서 이 계정이 일반
    # 계정처럼 보여서 더 헷갈립니다. "비밀번호가 없다"를 있는 그대로 표현하는 쪽을 택함.
    # 로그인 검증(services/auth.authenticate)에서 None을 반드시 먼저 걸러야 함.
    hashed_password = fields.CharField(max_length=128, null=True)
    # ⚠️ 2026-09-09 추가(구글 로그인): 구글 ID 토큰의 sub 클레임.
    # 이메일은 사용자가 바꿀 수 있지만 sub는 절대 안 바뀌므로, "이 구글 계정이 누구인가"의
    # 기준은 항상 이 값입니다. 이메일은 최초 연결(매칭) 때만 씁니다.
    # unique=True라서 한 구글 계정이 두 유저에 붙는 일이 DB 레벨에서 막힙니다.
    google_sub = fields.CharField(max_length=64, null=True, unique=True)
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
