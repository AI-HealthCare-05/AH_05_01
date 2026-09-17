# AI 모델 공식 환경 의존성 검증

작성일: 2026-08-27
연결: 이슈 #2 / develop 기준 e69dee47839a6cda1c899affa44e1e41241c6c59
범위: 환경 준비 및 합성 테스트. 실제 모델 재현 실행·공개·동결은 아님.

## 변경과 이유

- ai group: pandas 2.3.3 추가, 직접 사용하는 numpy·PyYAML 선언.
- dev group: pytest >=9.0.2,<10 직접 선언. lock은 기존 9.0.2 유지.
- pandas 외 기존 lock 패키지 버전 변경 없음. sklearn 1.8.0, numpy 2.4.1, scipy 1.17.0 유지.
- pandas는 기존 실험과 같은 2.3.3을 최초 호환성 기준으로 고정. 자동 major upgrade를 포함하지 않음.
- pandas의 라이선스는 배포 메타데이터상 BSD-3-Clause. 이번 작업을 전체 의존성의 취약점/라이선스 감사로 해석하지 않음.
- pyreadstat는 SAS 원자료 감사용이므로 이번 CSV 기반 D0 환경 범위에는 추가하지 않음.
- 기존 torch 계열 의존성을 삭제하거나 재구성하지 않음.

## 검증한 환경

독립 Windows x86-64 worktree와 새 venv에서 검증. 기존 연구 venv 및 미커밋 작업은 보존.

| 항목 | 버전 |
|---|---|
| Python | 3.13.15 |
| uv | 0.12.5 |
| scikit-learn | 1.8.0 |
| numpy | 2.4.1 |
| scipy | 1.17.0 |
| pandas | 2.3.3 |
| PyYAML | 6.0.3 |
| pytest | 9.0.2 |

최초 PR(4035fd8) 검증 시 uv.lock 파일 바이트 SHA-256 (리뷰 수정 전 이력):
`3e123ad5d95a54466ecf9f3aa5cdac7e5152914387bdb3f0c9dca3c8f9fe34e1`

홍주님 리뷰 반영 후 uv.lock 파일 바이트 SHA-256:
`c3aa7e1552dc4fe6329f5e6c1992f690c9804b792ede860a1ff1c67bab9dbdfa`
선언 상한만 변경됐으며 기존 패키지의 버전·배포 해시는 모두 유지됐다.
Git checkout의 줄바꿈 설정에 따라 파일 바이트 해시가 달라질 수 있으므로 실제 재현 실행에서는 해당 환경 파일을 다시 해시하고 기록한다.

## PR에 포함한 CI 검증

신규 `ai-environment` job은 다음을 실행한다. 기존 lint/app test job은 유지한다.

```bash
uv lock --check
uv sync --frozen --group app --group ai
uv run --frozen --group app --group ai python -m pytest tests/ai_environment -q
```

`tests/ai_environment`의 11개 smoke test는 Python/설치 패키지와 lock 일치,
YAML·pandas 자료형, scaler clone 분리, 작은 합성 입력의 LogisticRegression/RandomForest
확률 및 metric API를 확인한다. 실제 D0 모델 선택이나 성능 검증은 수행하지 않는다.
테스트는 원자료·모델·OOF 파일을 읽지 않으며 외부 서비스/비밀을 필요로 하지 않는다.

리뷰 반영 후 로컬 결과: 환경 테스트 11/11 PASS, 경고 0개.
Ruff check/format PASS, Mypy `app ai_worker` 52개 파일 PASS.
원격 CI 결과는 PR checks에서 별도로 확인한다. 로컬 app DB 통합 테스트는 실행하지 않았으며 기존 MySQL CI가 담당한다.

## 외부 모델 작업공간의 로컬 합성 검증

develop에는 아직 tmtn_ai 모델 구현이 없다. 이번 의존성 PR에 미승인 모델 코드를 대량 편입하지 않았다.
아래 결과는 외부 모델 작업공간에서 **이 PR의 새 Python 환경**으로 실행한 로컬 증거이며,
신규 GitHub smoke job이 D0 안전성 21개를 실행한다는 뜻이 아니다.

```text
<new-environment-python> -B -m pytest tests/test_disease_model_pipeline.py tests/test_d0_calibration_crossfit.py -q -p no:cacheprovider
21 passed, 381 warnings

<new-environment-python> -B -m pytest -q -p no:cacheprovider
213 passed, 381 warnings
```

전체 213개는 D0 21개를 포함한다. 모든 입력은 기존 합성 fixture이며 실제 KNHANES 자료는 사용하지 않았다.

검증한 소스의 SHA-256 (모델 작업공간 상대경로):

| 파일 | SHA-256 |
|---|---|
| src/disease_model_pipeline.py | bfa8a5cc7e440649a69ca5cfa784fadeb02a0fdfd70b30494b058fdae5fc226b |
| src/run_d0_oof.py | 0fe2f82bb1403ceeccf8301a7eaf57331c0c2dd87233f8b167551495f2692fee |
| tests/test_disease_model_pipeline.py | d9f4613e522e784d6a6f2a22d1bbd01fc0cde4aba30c1c0640f4bce41aeb7f0b |
| tests/test_d0_calibration_crossfit.py | 4e3ddbdab854ac418cec3a4fc32e7ebc5db20142a9cdc1ed30b22318f9bff75b |

위 381개 경고는 외부 D0 코드의 penalty 인자 deprecation이다. 실제 D0 코드는 보존하며
외부 D0 경고를 숨기지 않았다. 이는 환경 smoke test의 경고 제거와 별개다.

## 후속 실행 경계

### 홍주님 리뷰 반영: 환경 smoke와 레거시 검증 분리

- sklearn 선언은 `>=1.8.0,<1.10`으로 제한하며 lock의 1.8.0은 유지한다.
- 환경 smoke의 두 호출은 `LogisticRegression(C=1.0, l1_ratio=0.0, max_iter=1000)`으로 변경한다.
  유한 C의 L2 모델은 API 호환성 확인용이며 실제 무규제 Platt 모델을 대체하지 않는다.
- 환경 테스트 모듈에서만 경고를 오류로 처리한다. 외부 D0 및 백엔드 경고 정책은 바꾸지 않는다.
- 1.8.0에서 `C=np.inf`는 별도 UserWarning을 발생시키므로 무규제가 필요 없는 smoke에는 쓰지 않는다.
- `<1.10`은 penalty 제거 버전으로의 무검토 상승을 막는 임시 상한이다. 허용 범위의 모든 버전을 검증했다는 뜻은 아니다.

### 상한 제거 조건

1. 기존 D0 코드·실행·환경 기록을 보존하고 별도 코드 버전/PR에서 API를 이전한다.
2. 무규제와 L1/L2 의미를 보존한다. 위 유한 C smoke 구성을 실제 Platt에 복사하지 않는다.
3. 합성 안전성 테스트 및 사전 정의한 기준에 따른 필요한 수치 검증을 수행한다.
4. 코드 변경 실행을 기존 코드의 환경 재현으로 표기하지 않는다.
5. 데이터/AI·공동 환경 리뷰 후 상한·lock·환경 문서를 함께 갱신한다.

근거: [sklearn 1.8 변경 내역](https://scikit-learn.org/stable/whats_new/v1.8.html),
[무규제 인자 경고 이슈](https://github.com/scikit-learn/scikit-learn/issues/32927).

### 기존 실행 제한 유지

- 공식 환경에서는 uv.lock의 pytest 9.0.2를 사용. 기존 실험 requirements의 pytest <9 제한은 그 환경의 이력으로 보존하고 함께 설치하지 않음.
- 모델 코드가 develop에 정식 편입될 때 D0 전체 합성 테스트의 CI 연결을 별도 수행.
- PR 리뷰/병합, 실행 전 승인 증거·policy·receipt·입력 해시 검증이 끝난 후에만 실제 D0 재현을 시작.
- 2022는 전체 동결 전 금지, 2023은 custodian 전용, 2024는 금지. 이번 작업에서는 어느 연도의 실제 건강 데이터도 접근하지 않음.
- rollback은 pyproject·lock·CI·환경 문서를 함께 되돌리는 PR로 수행. 기존 연구 환경과 산출물은 삭제하지 않음.
