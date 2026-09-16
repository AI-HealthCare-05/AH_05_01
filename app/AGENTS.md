# AGENTS.md — `app/` 백엔드 (FastAPI)

> 저장소 루트의 `AGENTS.md` 가 먼저입니다. 이 파일은 **백엔드 작업에만** 더해지는 규칙입니다.

## 구조

```
app/
├── apis/v1/       라우터 (HTTP 경계)
├── services/      비즈니스 로직
├── repositories/  DB 접근
├── models/        Tortoise ORM 모델
├── dtos/          요청·응답 스키마 (Pydantic)
├── core/          설정 · DB · JWT · 유틸
└── dependencies/  FastAPI 의존성
```

**Router → Service → Repository** 방향을 지킵니다. 라우터에서 ORM 을 직접 부르지 않습니다.

스택: FastAPI · Tortoise ORM · aerich · MySQL 8.0.46 · Redis 7.2

---

# 하지 말 것

## DB

- **마이그레이션 파일을 손으로 고치지 마세요.** `uv run aerich migrate --name <설명>` 으로 생성합니다.
- **`docker compose down -v` 를 습관적으로 쓰지 마세요.** `-v` 는 볼륨까지 지워서 **로컬 DB 데이터가 전부 날아갑니다.** 멈추려면 `docker compose stop`, 컨테이너만 지우려면 `docker compose down` 입니다.
- **ERD 51개 테이블을 한 번에 만들지 마세요.** 지금 필요한 것만 만듭니다.
- **`users.email` · `phone_number` 에 UNIQUE 제약 없이 두지 마세요.** 동시 가입에서 중복이 생깁니다.

## 컨테이너

- **컨테이너 안에서 `DB_HOST=localhost` 를 쓰지 마세요.** 컨테이너 안의 `localhost` 는 자기 자신입니다. compose 네트워크 `ws` 안에서는 서비스 이름 **`mysql`** 로 부릅니다.

  | 앱을 어디서 도나 | `DB_HOST` |
  | --- | --- |
  | 내 PC 에서 `uv run uvicorn` | `localhost` |
  | fastapi 컨테이너 안 | `mysql` |

- **운영 서버에서 mysql·redis 의 `ports:` 를 열지 마세요.** 두 컨테이너는 `ws` 네트워크 안에서만 쓰이면 됩니다. 밖에 열 것은 nginx 의 80/443 뿐입니다. DB 작업이 필요하면 SSH 터널을 쓰세요.

## 비동기

- **`async def` 안에서 `model.predict()` 같은 블로킹 호출을 그냥 부르지 마세요.** 이벤트 루프가 멈춥니다. `await asyncio.to_thread(...)` 로 감쌉니다.
- **동기 DB 드라이버를 쓰지 마세요.** `asyncmy` 를 씁니다.

## 인증

- **refresh token 을 평문으로 저장하지 마세요.** 해시로 저장하고 회전시킵니다.
- **JWT secret 이 없을 때 임의값으로 시작하게 두지 마세요.** 운영에서는 비밀이 없으면 **즉시 시작 실패**해야 합니다. 다중 워커·재시작에서 토큰이 조용히 무효화됩니다.
- **앱은 `httpOnly` 쿠키를 못 씁니다.** 안드로이드 클라이언트는 Keystore 에 저장합니다. 토큰 저장을 어댑터로 감싸 두세요.

## 코드

- **함수 안에서 import 하지 마세요.** (루트 규칙)
- **라우터 함수에 비즈니스 로직을 넣지 마세요.** 서비스로 내립니다.
- **`app/` 안에서 `android/` 나 `tmtn_ai/` 를 import 하지 마세요.**

---

# 할 것

## 로컬 개발 (권장 구성)

앱은 컨테이너 밖에서 돌리는 게 개발할 때 편합니다.

```bash
cp envs/example.local.env .env
docker compose up -d mysql redis        # DB·캐시만
uv run uvicorn app.main:app --reload --port 8000
```

이 구성에서는 `.env` 의 `DB_HOST=localhost` 가 맞습니다.

## 스키마 공유는 DB 공유가 아니라 마이그레이션으로

```bash
# 모델 고친 사람
uv run aerich migrate --name add_daily_records
git add app/core/db/migrations/ && git commit -m "behavioral: 일일 기록 테이블 추가"

# 나머지 팀원
git pull && uv run aerich upgrade
```

## API 경로

공개 경로 계약을 `/api/v1` 또는 `/v1` **둘 중 하나로 확정**하고 문서와 코드를 같은 PR 에서 맞추세요. 현재 코드(`/api/v1/auth/signup`)와 최신 계약(`/v1/auth/sign-up`)이 어긋나 있습니다.

## 커밋 전

```bash
uv run ruff format . && uv run ruff check --fix .
uv run mypy .
uv run pytest
```
