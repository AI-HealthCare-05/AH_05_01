# TMTN Pull Request·코드 리뷰 가이드

PR은 “코드를 합치는 요청”이면서 변경 이유, 위험, 검증 증거를 남기는 프로젝트 기록입니다. TMTN은 인증·건강 데이터·개인정보와 Android 권한을 다루므로 정상 동작뿐 아니라 데이터 최소화, 실패 복구, 접근성, 하위호환성을 함께 검토합니다.

## 기본 원칙

- 한 PR은 하나의 이슈·목적을 해결합니다.
- 작성자는 리뷰 요청 전에 diff를 처음부터 끝까지 self-review합니다.
- 리뷰어는 사람의 취향보다 요구사항, 계약, 불변조건, 재현 가능한 증거를 기준으로 판단합니다.
- CI 통과는 리뷰를 대신하지 않으며, 리뷰 승인도 CI 실패를 무시할 근거가 아닙니다.
- 긴급 상황에서도 사후 리뷰·테스트·회고를 생략하지 않습니다.
- 공격적 표현 대신 코드와 영향에 대해 구체적으로 말합니다.

## PR 크기와 열기 시점

- 권장: 생성 파일을 제외한 변경 400줄 이하, 20개 파일 이하
- 400줄을 넘으면 분리하지 못한 이유와 리뷰 순서를 PR에 적습니다.
- DB migration, OpenAPI 계약, 생성 SDK처럼 기계 생성 변경은 논리 변경과 분리합니다.
- 하루 이상 걸리는 작업은 Draft PR을 일찍 열어 방향·계약·화면 상태를 먼저 합의합니다.
- 기능 개발 전에 schema/OpenAPI PR, 이후 서버·Android 구현 PR 순서로 나눌 수 있습니다.

## 제목과 연결

PR 제목은 squash commit에 그대로 사용할 수 있어야 합니다.

```text
<type>(<scope>): <구체적인 변경>
```

```text
feat(card): add idempotent daily card selection
fix(auth): rotate refresh token after renewal
docs(android): document Health Connect permission flow
```

본문에 `Closes #123` 또는 관련 이슈를 연결합니다. 화면·요구사항 ID가 있으면 `FR-CARD-003`, `NFR-SEC-001`처럼 함께 적습니다.

## PR 본문에 반드시 포함할 내용

1. **무엇을 왜 바꿨는지** — 사용자가 겪은 문제와 해결 범위
2. **범위 밖** — 이 PR에서 일부러 하지 않은 것과 후속 이슈
3. **구현·설계** — 상태 전이, 트랜잭션, 권한, 실패 처리, 중요한 선택
4. **계약 영향** — API, DB, 이벤트, 캐시, Android 저장소·권한, 하위호환성
5. **검증 증거** — 실행한 명령과 결과, 수동 시나리오, 스크린샷·영상
6. **배포·롤백** — migration 순서, feature flag, 환경 변수, 되돌리는 방법
7. **보안·개인정보** — 새 수집 항목, 로그·분석·LLM 전송, 보존·삭제 영향

“테스트했습니다”만 쓰지 않고 어떤 환경에서 무엇을 확인했는지 적습니다.

## 승인 기준

| 변경 유형 | 최소 승인 | 추가 조건 |
|---|---:|---|
| 문서, 저위험 UI, 테스트만 변경 | 1명 | 담당 영역 리뷰어 권장 |
| 일반 Android·백엔드 기능 | 1명 | 관련 CI 모두 통과 |
| 인증·권한·개인정보·건강 데이터 | 2명 | 보안/프라이버시 체크 필수 |
| DB migration·원장·멱등 상태 전이 | 2명 | 백엔드/DB 담당 포함, 롤백 검토 |
| 인프라·배포·서명·비밀 관리 | 2명 | 운영 담당 포함, staging 증거 |
| 건강 참고 계산·추천·모델 변경 | 2명 | 데이터/도메인 담당 포함, 평가·고지 검토 |

작성자는 승인자로 계산하지 않습니다. 최신 commit 기준 승인이어야 하며, 모든 blocking 대화가 해결되어야 merge할 수 있습니다.

## 리뷰 코멘트 등급

리뷰 코멘트 앞에 등급을 표시합니다.

- `[P0]` 즉시 차단: 보안 사고, 데이터 손상, 개인정보 위반, 배포 불가
- `[P1]` merge 차단: 기능 오류, 계약 위반, 누락된 핵심 테스트, 심각한 접근성 문제
- `[P2]` 수정 권장: 유지보수성·성능·경계 사례 문제. 이번 PR 처리 여부를 합의
- `[P3]` 선택 제안: 비차단 개선 아이디어
- `[question]` 의도·근거 확인
- `[praise]` 재사용할 만한 좋은 패턴

좋은 코멘트는 위치, 조건, 영향, 제안을 포함합니다.

```text
[P1] 같은 Idempotency-Key로 complete가 두 번 들어오면 energy_ledger가
중복 적립될 수 있습니다. 이벤트 키 UNIQUE 제약과 재요청 테스트를 추가해 주세요.
```

취향 차이는 formatter·lint·팀 규칙으로 자동화하고 blocking 코멘트로 만들지 않습니다.

## 공통 리뷰 체크리스트

### 요구사항·설계

- 이슈의 acceptance criteria를 실제로 충족하는가
- PR 범위가 한 목적에 집중되어 있는가
- 실패, 권한 거부, 빈 데이터, 오프라인을 구분하는가
- 새 의존성·서비스·권한이 꼭 필요한가
- 기존 규칙과 충돌하면 ADR·요구사항·OpenAPI가 함께 갱신됐는가

### 코드

- 이름과 계층 경계가 명확하고 중복·순환 의존이 없는가
- Router는 HTTP 변환, Service는 유스케이스, Repository는 저장소 책임에 집중하는가
- 예외를 삼키거나 무한 재시도하지 않는가
- 로그에 토큰, 비밀번호, 정밀 위치, 건강 원본, 개인정보가 남지 않는가
- 시간은 UTC와 `service_date`·timezone 의미를 혼동하지 않는가

### 테스트

- 정상 경로뿐 아니라 인증 실패, 경계값, 중복 요청, 동시성, 재시도를 검증하는가
- 버그 수정 PR에 재현 테스트가 먼저 또는 함께 추가됐는가
- mock이 구현을 그대로 복제하지 않고 외부 경계만 대체하는가
- flaky test, 고정 시간·순서 의존, 공유 DB 오염이 없는가
- CI에서 실제로 실행되며 coverage 감소 이유가 설명됐는가

### 운영

- 새 환경 변수에 예시·검증·문서가 있는가
- health check, timeout, retry, circuit/fallback이 적절한가
- 배포 중 구버전 앱·서버와 공존 가능한가
- migration은 expand → deploy → contract 순서이고 되돌릴 수 있는가
- 대시보드·로그·알람으로 실패를 발견할 수 있는가

## FastAPI·API 리뷰

- `tmtn_openapi.yaml`과 method, path, status, schema, error code가 일치하는가
- v1의 필드를 삭제·이름 변경·의미 변경하지 않는가
- 새 필드는 구버전 Android 앱이 무시할 수 있는 optional·default 형태인가
- 인증 오류가 계정 존재 여부를 노출하지 않는가
- refresh token rotation·revocation·reuse detection이 보장되는가
- 쓰기 API가 `Idempotency-Key`를 검증하고 같은 요청에 같은 결과를 반환하는가
- 카드 선택·시작·완료·skip과 원장 적립이 하나의 원자적 상태 전이인가
- 입력 크기, enum, 날짜, timezone, pagination, rate limit이 명시됐는가
- 4xx와 5xx를 혼동하지 않고 공통 에러 envelope를 사용하는가
- 로그와 API 문서에 비밀·개인정보 예시가 들어가지 않는가

## DB·migration 리뷰

- FK, UNIQUE, CHECK, index가 애플리케이션 실수를 DB에서도 막는가
- 이메일 중복, 하루 카드 세트, 완료 이벤트, 원장 적립의 race condition을 막는가
- 돈·점수·원장은 append-only와 source event 추적을 지키는가
- snapshot은 과거 화면을 재현할 version과 문구·속성을 보존하는가
- 큰 테이블 migration이 lock·downtime을 만들지 않는가
- up/down 또는 forward-fix와 백업·검증 쿼리가 준비됐는가
- migration이 빈 DB와 기존 데이터가 있는 DB 양쪽에서 테스트됐는가

## Android·APK 리뷰

- 모듈 의존 방향이 `presentation → domain ← data` 원칙을 지키는가
- DTO·Room entity가 UI에 직접 노출되지 않고 domain model로 매핑되는가
- ViewModel에 비즈니스 규칙을 쌓지 않고 UseCase로 분리하는가
- coroutine이 lifecycle과 구조화 동시성을 지키고 `GlobalScope`를 쓰지 않는가
- refresh token은 Keystore 기반 안전한 저장소에 있고 로그·백업에서 제외되는가
- 프로세스 종료·회전·백그라운드 복귀 후 챌린지 상태를 서버에서 복구하는가
- 로딩, 오류, 빈 데이터, 오프라인, 권한 거부 UI가 각각 있는가
- FCM·Health Connect·위치는 사용자 CTA 뒤 최소 권한으로 요청하는가
- Health Connect 없이 수동 기록이 가능하고 중복 활동을 합산하지 않는가
- 48dp 이상 터치 영역, TalkBack, 색 대비, 글자 확대, 시니어 모드를 확인했는가
- screenshot/video에 실사용자 정보와 토큰이 보이지 않는가
- release build의 signing, R8/ProGuard, lint, unit/UI test가 통과하는가

## AI Worker·Redis 리뷰

- API 요청 스레드에서 무거운 학습·추론을 실행하지 않는가
- Stream consumer group과 job 상태가 추적 가능한가
- 성공 후에만 `XACK`하고, pending claim의 idle 조건과 최대 시도를 제한하는가
- 같은 job 재처리가 결과·원장·알림을 중복 생성하지 않는가
- timeout, 취소, 재시도, DLQ, 운영자 재처리 절차가 있는가
- 모델·프롬프트·특징·데이터 버전을 결과에 기록하는가
- 불필요한 건강정보와 원시 정밀 위치를 모델·LLM에 전송하지 않는가
- 모델 실패 시 안전한 정적 카드·설명으로 폴백하는가

## 보안·프라이버시 리뷰

- 필요한 데이터만 수집하고 목적·보존·파기 시점을 문서화했는가
- 계정 삭제·내보내기·동의 철회는 재인증과 감사 이벤트를 남기는가
- `.env`, keystore, 인증서, OAuth·FCM 키가 diff와 artifact에 없는가
- SQL/command injection, SSRF, path traversal, insecure deserialization 가능성이 없는가
- 의존성·컨테이너 취약점과 라이선스를 확인했는가
- 오행·활동 지침·KNHANES 참고를 합산하거나 진단·예측처럼 표현하지 않는가

## 작성자 완료 조건

- [ ] 이슈와 요구사항 ID를 연결했다
- [ ] PR을 self-review하고 불필요한 diff를 제거했다
- [ ] OpenAPI·ERD·문서·migration을 함께 갱신했다
- [ ] 로컬 lint, typecheck, test를 실행하고 결과를 적었다
- [ ] Android 또는 API의 수동 핵심 시나리오를 확인했다
- [ ] 보안·개인정보·접근성 영향을 검토했다
- [ ] 배포·rollback과 환경 변수 변경을 적었다
- [ ] 스크린샷·로그에서 민감정보를 제거했다
- [ ] CI가 모두 통과했다

## 리뷰어 완료 조건

- [ ] 문제 정의와 acceptance criteria를 이해했다
- [ ] 위험한 경로와 핵심 diff를 직접 읽었다
- [ ] 계약·DB·상태 전이·실패 복구를 검토했다
- [ ] 테스트가 실제 결함을 잡는지 확인했다
- [ ] blocking과 non-blocking을 명확히 구분했다
- [ ] 작성자의 답변과 수정 후 코드를 다시 확인했다
- [ ] 해결되지 않은 대화가 없고 최신 commit에 승인했다

## Merge 후

- 배포·migration·모니터링 결과를 이슈 또는 릴리스에 남깁니다.
- 작업 브랜치를 삭제합니다.
- 예상치 못한 문제는 revert 또는 hotfix로 처리하고 공개 branch history를 다시 쓰지 않습니다.
- 문서·계약이 실제 구현과 어긋났다면 같은 스프린트 안에 수정합니다.
