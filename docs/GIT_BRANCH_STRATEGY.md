# TMTN Git 브랜치 전략

이 저장소는 Android APK/AAB와 백엔드의 정기 릴리스를 함께 관리해야 하므로 **경량 GitFlow**를 사용합니다. 오래 유지되는 브랜치는 `main`과 `develop` 두 개뿐이며, 작업 브랜치는 짧게 유지합니다.

## 브랜치 흐름

```text
feature/* ─┐
fix/* ─────┼─> develop ─> release/x.y.z ─> main ─> vX.Y.Z
docs/* ────┘                     │             │
                                └─────────────┘ back-merge

main ─> hotfix/x.y.z ─> main ─> vX.Y.Z
                 └────────────> develop
```

## 브랜치별 규칙

| 브랜치 | 생성 기준 | PR 대상 | 수명과 목적 |
|---|---|---|---|
| `main` | 영구 | 해당 없음 | 배포 가능한 릴리스만 보관. 직접 push 금지 |
| `develop` | `main`에서 최초 1회 | 해당 없음 | 다음 릴리스 통합. 직접 push 금지 |
| `feature/<issue>-<slug>` | `develop` | `develop` | 사용자 기능·도메인 추가 |
| `fix/<issue>-<slug>` | `develop` | `develop` | 아직 배포되지 않은 결함 수정 |
| `docs/<issue>-<slug>` | `develop` | `develop` | 문서만 변경 |
| `refactor/<issue>-<slug>` | `develop` | `develop` | 동작 변경 없는 구조 개선 |
| `test/<issue>-<slug>` | `develop` | `develop` | 테스트·품질 게이트 보강 |
| `chore/<issue>-<slug>` | `develop` | `develop` | 의존성·설정·자동화 유지보수 |
| `release/<version>` | `develop` | `main` | 릴리스 후보 안정화. 새 기능 금지 |
| `hotfix/<version>` | `main` | `main` | 운영의 긴급 결함·보안 수정 |

브랜치 slug는 영문 소문자와 하이픈을 사용합니다.

```text
feature/123-daily-card-selection
fix/241-refresh-token-rotation
docs/88-api-version-policy
release/0.3.0
hotfix/0.3.1
```

이슈가 없는 긴급 문서 작업을 제외하면 이슈 번호를 생략하지 않습니다. 이름에 사람 이름, `final`, `new`, `test1` 같은 의미 없는 표현을 쓰지 않습니다.

## 일상 작업 순서

```bash
git switch develop
git pull --ff-only origin develop
git switch -c feature/123-daily-card-selection

# 작업 후 작은 단위로 커밋
git add -p
git commit -m "feat(card): add daily card selection"

git push -u origin feature/123-daily-card-selection
```

1. 하나의 이슈와 하나의 목적만 브랜치에 담습니다.
2. 가능하면 1~3일 안에 PR을 열고, 큰 작업은 기능 플래그나 수직 슬라이스로 나눕니다.
3. 작업 중 `develop`이 바뀌면 자신의 브랜치에 반영합니다.
4. 리뷰와 CI가 끝나면 GitHub에서 merge하고 원격 브랜치를 삭제합니다.

## 최신 `develop` 반영

혼자만 사용하는 작업 브랜치는 rebase할 수 있습니다.

```bash
git fetch origin
git rebase origin/develop
git push --force-with-lease
```

둘 이상이 공유하는 작업 브랜치는 history를 다시 쓰지 않고 merge합니다.

```bash
git fetch origin
git merge origin/develop
git push
```

- `--force`는 사용하지 않습니다. 꼭 필요할 때도 `--force-with-lease`만 사용합니다.
- `main`, `develop`, `release/*`, 다른 사람이 사용하는 브랜치는 rebase하지 않습니다.
- 충돌을 해결한 뒤 관련 테스트를 다시 실행하고, 해석이 필요한 충돌은 원 작성자와 함께 결정합니다.

## Merge 정책

| PR 유형 | Merge 방식 | 이유 |
|---|---|---|
| 작업 브랜치 → `develop` | Squash merge | 이슈당 한 커밋으로 읽기 쉬운 기록 유지 |
| `release/*` → `main` | Merge commit | 릴리스 경계를 명확히 보존 |
| `hotfix/*` → `main` | Merge commit | 긴급 릴리스 경계를 명확히 보존 |
| `main` → `develop` 동기화 | Merge commit | 공개 history를 다시 쓰지 않고 동일 수정 전파 |

Squash commit 제목은 PR 제목과 같은 Conventional Commit 형식을 사용합니다. merge 후 원격 작업 브랜치는 자동 삭제합니다.

## 커밋 규칙

```text
<type>(<scope>): <명령형 요약>

왜 변경했는지와 중요한 제약

Closes #123
```

허용 type:

- `feat`: 새 기능
- `fix`: 결함 수정
- `docs`: 문서
- `refactor`: 동작 변경 없는 구조 개선
- `test`: 테스트
- `perf`: 성능
- `style`: 포맷만 변경
- `build`: 빌드·의존성
- `ci`: CI/CD
- `chore`: 기타 유지보수
- `revert`: 되돌리기

권장 scope는 `android`, `api`, `auth`, `card`, `challenge`, `privacy`, `health-connect`, `notification`, `worker`, `db`, `infra`, `docs`입니다.

```text
feat(challenge): make completion idempotent
fix(android): restore active timer after process death
docs(api): document refresh token rotation
ci(android): add release lint and unit tests
```

- 한 커밋은 빌드·테스트 가능한 한 가지 논리 변경을 담습니다.
- `WIP`, `수정`, `업데이트`, `최종`처럼 의도가 없는 제목을 merge history에 남기지 않습니다.
- 생성 파일과 포맷 변경을 기능 변경과 분리하면 리뷰가 쉬워집니다.
- breaking change는 footer에 `BREAKING CHANGE:`를 기록하며, v1에서는 호환 경로·이행 계획 없이 merge하지 않습니다.

## Release 절차

1. `develop`에서 `release/x.y.z`를 만듭니다.
2. 버전, changelog, 마이그레이션, OpenAPI, Android `versionCode`·`versionName`을 맞춥니다.
3. 릴리스 브랜치에는 결함 수정·문서·버전 변경만 허용합니다.
4. 백엔드 통합 테스트와 Android unit/UI/lint, staging smoke test, APK/AAB 서명 검증을 통과합니다.
5. `main` PR을 merge하고 annotated tag `vX.Y.Z`를 생성합니다.
6. `main`을 `develop`에 PR로 back-merge합니다.
7. GitHub Release에 사용자 변경점, 알려진 문제, DB migration·rollback, APK/AAB 위치를 기록합니다.

```bash
git tag -a v0.3.0 -m "TMTN v0.3.0"
git push origin v0.3.0
```

APK·AAB 파일은 Git에 직접 commit하지 않고 CI artifact 또는 GitHub Release에 첨부합니다. 서명키는 GitHub Secrets 또는 승인된 비밀 저장소에만 둡니다.

## Hotfix 절차

1. 운영 이슈를 만들고 영향·재현·임시 대응을 기록합니다.
2. `main`에서 `hotfix/x.y.z`를 생성합니다.
3. 최소 수정과 회귀 테스트만 포함합니다.
4. 보안·개인정보·데이터 손상 이슈는 2명 승인을 받습니다.
5. `main` merge·tag·배포 후 `develop`에 즉시 back-merge합니다.
6. 장애 회고와 예방 이슈를 후속 등록합니다.

## GitHub 보호 설정

`main`과 `develop`에 다음 규칙을 적용합니다.

- Pull Request 없이 merge 금지
- 일반 변경 1명 승인, 민감 변경 2명 승인
- 새 commit이 올라오면 기존 승인 무효화
- 대화가 해결되지 않은 PR merge 금지
- 필수 CI: Ruff, Mypy, Pytest, OpenAPI 검사, Android lint·unit test, secret scan
- 브랜치 최신화 또는 merge queue 사용
- 관리자 우회와 force push 금지
- `main` 삭제 금지, `develop`도 삭제 제한
- linear history는 `develop`의 squash 정책과 함께 적용하되 release merge commit 정책과 충돌하지 않게 ruleset을 분리

저장소에는 현재 원격 `main`만 있으므로, 팀 합의 후 `develop`을 만들고 두 브랜치 보호 규칙을 먼저 설정한 다음 기능 개발을 시작합니다.

## 금지 사항

- `main` 또는 `develop` 직접 commit·push
- 공유 브랜치 force push와 공개 history rebase
- 기능, 대규모 포맷, 마이그레이션을 한 PR에 혼합
- `.env`, keystore, 인증서, 실제 데이터, APK/AAB commit
- 실패하는 CI를 우회하거나 테스트 삭제로 통과시키기
- 리뷰 없이 migration, 인증, 개인정보, 의료·건강 로직 merge
