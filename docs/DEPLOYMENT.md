# EC2 배포 · 현재 앱과 개인 XAI

대상은 **기존 EC2 Linux x86_64의 팀 내부 검토·시연 서버**입니다. 공개 운영 승인과는 별개입니다. 최신 `main`에는 앱·API·XAI 원본이 들어 있으므로 과거 ZIP을 다시 덮어쓰지 않습니다.

## 1. 현재 배포 설정 확인

기존 배포 커밋과 Compose 프로젝트 이름·파일·환경 파일을 기록하고 DB 백업을 완료합니다. 기존 `.env`, DB 볼륨, JWT·OAuth·SMTP 설정, Android 서명키는 유지합니다. 아래 예시는 **`docker-compose.yml` + `.env`**를 사용하는 기존 환경 기준입니다.

`docker-compose.prod.yml` / `.env.prod` 등 다른 구성을 쓰고 있다면 현재 프로젝트 이름과 환경 파일을 유지해야 합니다. 특히 `compose.xai-review.yml`의 `fastapi.env_file`은 `.env`를 직접 지정하므로 **CLI의 `--env-file .env.prod`만으로 컨테이너 설정이 바뀌지 않습니다.** 해당 환경에서는 운영 담당자가 env_file을 기존 파일과 `.env.xai-review`로 구성한 로컬 override를 준비한 뒤 진행합니다. 다른 프로젝트 이름으로 새 DB 볼륨을 만들거나 기존 볼륨을 초기화하지 않습니다.

```bash
cd /기존/AH_05_01
git status --short
git rev-parse HEAD
git switch main
git pull --ff-only
python3 tools/check_xai_assets.py
```

로컬 수정 때문에 전환·pull이 거절되면 중단하고 차이를 확인합니다. `reset --hard`나 과거 전달본 덮어쓰기로 해결하지 않습니다. 자산 검사는 XAI 원본 124개가 모두 일치해야 통과합니다.

## 2. 내부 XAI 설정과 이미지 준비

```bash
# 원본 검사 및 API·XAI 공통 토큰 생성. 기존 .env와 토큰 파일은 유지
python3 tools/prepare-xai-ec2.py
docker compose -f docker-compose.yml -f compose.xai-review.yml config --quiet
docker compose -f docker-compose.yml -f compose.xai-review.yml build fastapi tuntun-peer-bridge tuntun-xai
```

API는 Python **3.13.15**, 개인 XAI와 또래 브릿지는 별도 Python **3.14.7** 환경입니다. XAI 이미지 빌드에서 원본·런타임 검사를 수행합니다. Windows 실행 파일을 EC2에서 사용하지 않습니다.

`.env.xai-review`는 생성 시 0600 권한이며 `ENV=dev`, loopback 8776, 공통 인증 토큰을 담습니다. 기존 또래 브릿지 설정 `TUNTUN_PEER_BRIDGE_URL=http://127.0.0.1:8766` 등은 기존 서버 환경 파일에서 확인합니다. 별도로 쓰던 허리둘레 보조 서비스 URL도 임의로 삭제하지 않습니다.

토큰을 Android·PR·로그에 복사하지 않습니다. 8766·8776은 FastAPI의 네트워크 공간에서 사용하며 외부 포트를 열지 않습니다. API는 XAI 작업·캐시 일관성을 위해 worker 1개로 실행합니다.

## 3. 기존 DB 마이그레이션

기존 MySQL이 실행 중이며 API의 `DB_HOST`가 컨테이너에서 접근 가능한 주소인지 확인합니다. 신규 설치와 기존 서버 업데이트를 혼동하지 않습니다.

```bash
docker compose -f docker-compose.yml -f compose.xai-review.yml run --rm --no-deps fastapi uv run --no-sync aerich heads
docker compose -f docker-compose.yml -f compose.xai-review.yml run --rm --no-deps fastapi uv run --no-sync aerich upgrade
```

- 기존 파일을 그대로 유지하며, Aerich가 적용 기록에 없는 파일만 처리합니다.
- 23번은 근력운동 빈도의 단위, 24번은 개인 XAI 주간 이력입니다.
- 19번 접두사의 두 파일은 전체 이름이 다릅니다. 첫 재료 테이블 생성은 19·21 경로 모두 `IF NOT EXISTS`를 사용합니다. 하나를 임의로 삭제하거나 번호를 바꾸지 않습니다.
- 빈 MySQL 전체 적용과 재실행은 CI에서 확인했지만, 현재 서버 DB의 별도 수동 변경까지 검증한 것은 아닙니다.

## 4. 서비스 갱신과 확인

```bash
docker compose -f docker-compose.yml -f compose.xai-review.yml up -d --no-deps --force-recreate fastapi tuntun-peer-bridge tuntun-xai nginx
docker compose -f docker-compose.yml -f compose.xai-review.yml ps
docker compose -f docker-compose.yml -f compose.xai-review.yml logs --tail=50 fastapi tuntun-xai
docker compose -f docker-compose.yml -f compose.xai-review.yml exec -T tuntun-xai python -B model_service/verify_personal.py --payload /app/payload --out /tmp/xai-check
```

두 모델 서비스가 API의 네트워크를 공유하므로 FastAPI만 재생성한 채 두 모델 컨테이너를 그대로 두지 않습니다. 모델 검증기는 합성 입력 5건으로 추론·기여도 합·지원하지 않는 입력 차단을 확인하며 회원 DB는 사용하지 않습니다.

배포 후 앱에서 확인합니다.

1. 기존 계정 로그인과 토큰 갱신, Google 인증을 사용하는 환경이면 해당 인증.
2. 카드 선택·시작·완료, 완료 응원, 틈새운동 잠금 해제와 저장 재시도.
3. 일보에서 현재 댐과 월요일 기준 주간 기록 확인.
4. 분석 동의 후 신체·운동 정보를 저장하고 개인 결과가 `pending`에서 `ready`로 바뀌는지 확인.
5. 입력을 바꾸면 새 설명이 나오며, 동의 철회 시 개인 결과·주간 이력이 숨겨지는지 확인.

없는 지난주 점수는 표시되지 않는 것이 정상입니다. 상태별 원인과 조치는 [XAI 안내](XAI.md)를 확인합니다.

## 5. Android 담당자

같은 `main`을 받고 기존 `android/local.properties`와 서명 설정을 유지합니다. [Android 빌드 안내](../android/README.md)에 따라 APK를 만듭니다. 일반 앱에 검사용 init 스크립트를 적용하지 않습니다. 기존 앱과 서명이 달라 업데이트가 거절되면 같은 키로 다시 빌드하며, 해결을 위해 사용자 데이터를 삭제하지 않습니다.

## 오류 발생 시

실패한 단계에서 멈추고 오류와 배포 커밋을 남깁니다. DB 백업·기존 이미지와 현재 스키마 호환성을 확인한 뒤 복구합니다. DB 볼륨 삭제, 모델 해시 재작성, 자동 downgrade로 우회하지 않습니다. 이 안내서가 EC2에 실제 배포했다는 확인서는 아니며, 수행한 검사 범위는 [통합 기록](INTEGRATION_2026-09-21.md)에 있습니다.
