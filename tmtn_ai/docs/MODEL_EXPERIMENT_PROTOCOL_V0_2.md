# TMTN 공식 모델 실험 프로토콜 v0.2

문서 상태: 공동 승인 요청 가능  
기준일: 2026-08-25  
프로젝트 종료일: 2026-09-22  
강호 승인: 승인  
병학 승인: 대기  
실제 학습: 병학 승인 및 별도 authorized training config 생성 전까지 금지  
기계 판독 계약: `contracts/model_experiment_protocol_v0_2.yaml`

## 1. 결정 원칙

이 버전은 v0.1의 미결정 항목을 더 이상 탐색 상태로 두지 않고, 2026-09-22까지 모델 개발을 완료하기 위한 공식 기본값으로 동결한다.

- 문헌이 직접 정하지 않는 수치는 틈튼 P0의 운영상 위험 허용 기준으로 명시한다.
- 숫자 기준은 실제 학습 결과를 본 뒤 유리하게 바꾸지 않는다.
- 변경이 필요하면 기존 결과를 보존하고 새 프로토콜 버전을 만든다.
- 2022를 확인한 뒤 변경한 모델은 2022를 다시 미사용 평가자료라고 표현하지 않는다.
- 2023은 이미 결과가 공유된 `opened internal benchmark`이며 미개봉 test가 아니다.
- 2024는 접근하지 않는다.
- 틈틈지수 종합 공식은 이 프로토콜 범위 밖이며 별도 검증 전 생성하지 않는다.

## 2. 승인 및 학습 해제 조건

강호 승인으로 이 문서의 방법론·수치 권고안을 고정한다. 병학님은 다음 중 하나로 회신한다.

1. 전체 승인
2. 항목 번호와 대체값을 명시한 수정 요청

전체 승인 후에만 별도 학습 승인 config를 생성한다. 이 문서 자체의 `do_not_train`은 계속 `true`로 유지하여 검토 문서와 실제 실행 권한을 분리한다.

학습 승인 config에는 최소 다음이 포함되어야 한다.

- 승인자와 승인 시각
- 이 문서 및 YAML의 SHA-256
- canonical snapshot SHA-256
- 개발 연도 `[2019, 2020, 2021]`
- 2022 평가 전용 표시
- 2023 custodian-only 표시
- 2024 접근 금지
- 실행 코드 commit 또는 SHA-256

## 3. 연도 역할

| 연도 | 공식 역할 | 허용 | 금지 |
|---|---|---|---|
| 2019~2021 | development | EDA, nested cross-fitting, 후보·hyperparameter·calibration 선택 | 개발 외 연도 혼입 |
| 2022 | evaluation-only temporal validation | 완전 동결 후 1회 transform·predict·집계 평가 | 모든 fit, 선택, threshold 변경 |
| 2023 | opened internal benchmark | 홍주님 환경의 동결 추론·집계 평가 | 개발자 행 접근, 같은 버전 재튜닝 |
| 2024 | forbidden | 없음 | 파일·행·통계 접근 |

2022 결과를 보고 코드·피처·모델·보정·통과선을 변경하면 다음 버전에서 2022 상태를 `opened_temporal_benchmark`로 낮춘다. 단순히 experiment 이름만 바꾸어 2022를 반복 평가하지 않는다.

## 4. 개발 resampling

- 공식 방식: nested cross-fitting
- outer folds: 5
- inner folds: 4
- primary seed: 42
- 안정성 seed: 42, 1042, 2042
- Random 8:2, seed 42: 재현성 보조 비교만 수행
- fold assignment는 `source_year + participant_id`와 함께 저장
- 모든 outer·inner train/holdout key 교집합: 0건

## 5. 허리둘레 모델 grid

### 5.1 Linear Regression

```yaml
fit_intercept: [true]
```

### 5.2 Elastic Net

```yaml
preprocessing: standard_scaler
alpha: [0.0001, 0.001, 0.01, 0.1, 1.0, 10.0]
l1_ratio: [0.1, 0.5, 0.9, 1.0]
max_iter: 20000
tol: 0.0001
```

### 5.3 Random Forest Regressor

```yaml
n_estimators: [500]
max_depth: [6, 12, null]
min_samples_leaf: [1, 5, 20]
max_features: [0.7, 1.0]
criterion: squared_error
bootstrap: true
random_state: 42
```

선택 순서:

1. development nested OOF MAE
2. RMSE
3. 사전 정의된 지원 가능 하위집단 중 최악 MAE
4. 모델 단순성

2022 성능으로 후보나 hyperparameter를 선택하지 않는다.

## 6. 허리둘레 P0 통과 기준

2022의 사전 동결 평가에서 다음을 모두 충족해야 한다.

| 지표 | 통과 기준 |
|---|---:|
| 전체 MAE | 4.0 cm 이하 |
| MAE 95% CI 상한 | 4.5 cm 이하 |
| 전체 RMSE | 5.5 cm 이하 |
| 잔차 평균 절댓값 | 1.0 cm 이하 |
| 5 cm 이내 비율 | 70% 이상 |
| 10 cm 이내 비율 | 95% 이상 |
| 지원 가능 하위집단 MAE | 각 5.0 cm 이하 |
| 하위집단/전체 MAE 비 | 1.25 이하 |

- 기존 2~3% 상대오차는 연구 목표이며 P0 공개 게이트가 아니다.
- MAPE는 보조 지표로만 보고한다.
- 허리둘레 추정값으로 임상 진단을 단정하지 않는다.
- 임상 경계 부근에서는 직접 측정을 권유한다.
- 사용자가 실측값을 입력하면 추정값을 대체한다.

## 7. 당뇨·고혈압 모델 grid

두 task에 동일한 grid를 쓰되 선택과 보정은 task별로 독립 수행한다.

### 7.1 Logistic Regression

```yaml
preprocessing: standard_scaler
penalty: l2
C: [0.01, 0.1, 1.0, 10.0, 100.0]
solver: lbfgs
max_iter: 5000
class_weight: [null, balanced]
```

### 7.2 Random Forest Classifier

```yaml
n_estimators: [500]
criterion: log_loss
max_depth: [4, 8, null]
min_samples_leaf: [5, 20, 50]
max_features: [sqrt, 1.0]
class_weight: [null, balanced]
bootstrap: true
random_state: 42
```

- 모델 선택 primary: development OOF calibrated Brier score
- tie-breaker: log loss, calibration slope 거리, intercept 거리, PR-AUC, ROC-AUC, 단순성
- `class_weight=balanced` 예측은 보정 전 확률로 공개하지 않는다.
- calibration 후보: identity, 무규제 Platt logistic recalibration
- isotonic: 비활성화
- 보정기 적합 자료: 2019~2021 cross-fitted OOF만 허용

## 8. 질환 확률모델 P0 통과 기준

확률 참고값이므로 calibration을 hard gate로, discrimination을 최소 guardrail로 사용한다.

| 영역 | 지표 | 통과 기준 |
|---|---|---:|
| Discrimination | ROC-AUC | 0.65 이상 |
| Discrimination | ROC-AUC 95% CI 하한 | 0.60 이상 |
| Discrimination | PR-AUC / 평가 유병률 | 1.25 이상 |
| Overall | Brier Skill Score | 0 초과 |
| Overall | prevalence-only 대비 Brier 개선 | paired 95% CI가 0을 제외 |
| Calibration | intercept 절댓값 | 0.10 이하 |
| Calibration | slope | 0.80~1.20 |
| Calibration | 사전 고정 10-bin ECE | 0.05 이하 |
| Calibration | smoothed curve | 필수 검토 및 승인 |

당뇨와 고혈압은 독립 판정한다. 한 task가 실패하면 다른 task까지 자동 실패시키지 않지만, 실패한 task의 확률은 공개하지 않는다.

## 9. D0·D1 채택 규칙

D1은 matched-D0와 정확히 같은 행에서 비교한다.

- 3개 안정성 seed 중 최소 2개에서 development OOF Brier 개선
- 2022 paired Brier delta `(D1 - D0) < 0`
- 2022 paired 95% CI 상한 `< 0`
- calibration intercept 절댓값 악화 0.02 이내
- calibration slope의 1과의 거리 악화 0.05 이내
- ECE 악화 0.01 이내

하나라도 충족하지 않으면 질환모델은 D0를 유지한다. 허리둘레 추정값은 별도 참고 결과로 사용할 수 있으나 D1을 강제로 채택하지 않는다.

## 10. 하위집단 지원 기준

필수 하위집단:

- 성별
- 19~39세
- 40~64세
- 65세 이상
- development 연도별

질환모델:

| 지원량 | 처리 |
|---|---|
| event 100 이상, non-event 100 이상 | 전체 지표·95% CI·gate 판정 |
| event/non-event 중 작은 쪽 50~99 | 탐색적 수치만 보고, gate 제외 |
| event 또는 non-event 50 미만 | 수치 억제, `insufficient_support` |

허리둘레 모델:

- 하위집단 `n >= 200`: 정식 지표·gate 판정
- `100 <= n < 200`: 탐색적 보고
- `n < 100`: `insufficient_support`

성별×연령 교차집단은 선택 분석이며 동일한 지원 기준을 적용한다.

## 11. KNHANES 복합표본 가중 평가

- primary 학습·모델 선택: 비가중
- 공개 전 민감도: 비가중과 가중 결과 모두 보고
- 가중치: `wt_itvex`의 canonical 이름 `examination_weight`
- 층: `kstrata`의 canonical 이름 `strata`
- 집락: `psu`
- 2019~2021 통합 가중치: 각 연도 `wt_itvex / 3`
- 2022 단일연도 평가: 원래 `wt_itvex` 사용

가중치 결측 또는 비양수:

- 대치 금지
- 해당 행은 가중 민감도 분석에서만 제외
- task eligibility를 충족하면 비가중 분석에는 유지
- 전체·연도·task·라벨별 유효 가중치 coverage 보고
- coverage 95% 미만이면 `insufficient_weight_coverage`
- 제외 후 임의 재정규화 금지

## 12. 95% 신뢰구간

- 방식: Rao-Wu rescaled bootstrap
- 재표집 단위: PSU
- 층화: `kstrata`
- 반복: 2,000
- seed: 20260825
- 구간: percentile 2.5%, 97.5%
- 최소 유효 반복: 1,900
- D0·D1 및 후보 비교는 같은 bootstrap replicate를 사용하는 paired 방식

단순 행 bootstrap과 fold 표준편차를 95% CI로 표현하는 것을 금지한다. CI 적용 지표는 MAE, RMSE, 범위 내 비율, ROC-AUC, PR-AUC, Brier, Brier skill, calibration intercept·slope와 paired delta를 포함한다.

## 13. 앱 입력과 unsupported 정책

### 13.1 나이

- 앱 저장 허용: 정수 19~120세
- 19세 미만: P0 모델 `unsupported_population`
- 80세 이상: 모델 입력을 80으로 top-code하고 `age_top_coded_80_plus=true` 기록
- 120세 초과 또는 비정수: `invalid_input`

### 13.2 모델 계산용 성별

- 앱 값: `male`, `female`
- KNHANES 매핑: `male -> 1`, `female -> 2`
- 질문 문구는 성별 정체성이 아니라 건강지표 계산용 출생 시 기록 성별임을 명시
- 그 외 값·응답 거부: 건강 점수 `unsupported`, 챌린지 사용은 허용

### 13.3 키·몸무게

- 키: 100~220 cm
- 몸무게: 25~250 kg
- 범위 밖: clipping하지 않고 `unsupported_input`

### 13.4 유산소

- 일수: 정수 0~7일/주
- 일수 0이면 회당 시간은 구조적 0
- 일수 1~7이면 회당 시간 10~960분
- 회당 240분 초과: 재확인 UI
- 대표 강도: `moderate`, `vigorous`
- 파생식: `days * minutes * (1 if moderate else 2)`
- 0일인데 시간이 양수인 경우 자동 수정하지 않고 재입력 요청

### 13.5 근력운동

- 허용 범주: `0`, `1`, `2`, `3`, `4`, `5_plus`
- 앱 UI도 `0일, 1일, 2일, 3일, 4일, 5일 이상`으로 맞춘다.
- `4_plus`를 canonical `5_plus`로 변환하는 것은 금지한다.
- `5_plus`를 임의로 숫자 6으로 변환하지 않는다.

### 13.6 임신·결측·범위 밖

- 임신: P0 일반 성인 건강모델 `unsupported_population`
- 임신 여부 불명: 건강 점수 미계산
- 결측을 0·평균으로 바꾸지 않음
- 운동 결측 시 F2가 아닌 별도 F0 fallback만 허용
- 건강모델 unsupported여도 일반 챌린지 기능은 유지
- 모든 반환에는 모델버전·입력 snapshot·unsupported reason code 기록

## 14. 2023 custodian 운영

2023 상태는 `opened_internal_benchmark`로 고정한다.

- 금지 표현: locked test, untouched holdout, independent external validation, final public performance
- 행 단위 원자료·피처·라벨·예측: 홍주님 환경 밖 반출 금지
- 강호·병학: 동결 inference bundle만 전달
- 홍주: 로컬 materialization, 추론, 정답 결합, 집계 평가
- 팀 공유: 사전 정의된 집계 결과만 허용
- 같은 동결 버전을 2023 결과로 재튜닝 금지

## 15. 빠른 실행 일정

| 기간 | 산출물 |
|---|---|
| 8/25~8/26 | v0.2 공동 승인, authorized training config, 환경·snapshot hash |
| 8/27~9/03 | canonical task frame, fold registry, 허리둘레 nested OOF |
| 9/04~9/08 | 당뇨·고혈압 D0/D1 nested OOF, development calibration |
| 9/09 | 모델·전처리·calibrator·gate 완전 동결 |
| 9/10 | 2022 1회 평가 및 PASS/FAIL 판정 |
| 9/11~9/14 | model card, inference adapter, 합성 smoke test, 동결 bundle |
| 9/15~9/17 | 홍주님 환경의 2023 opened benchmark 및 집계 보고 |
| 9/18~9/20 | API 계약·안전 문구·재현성·QA 체크리스트 |
| 9/21~9/22 | 최종 버전 동결·인계 |

2023 준비가 늦어져도 2019~2022 공식 개발·평가와 model card 작성은 중단하지 않는다. 2023 opened benchmark 미완료 시 해당 항목만 후속 검증으로 명확히 기록한다.

## 16. 승인 체크리스트

- [x] 강호 방법론·수치 권고 승인
- [ ] 병학 방법론·수치 권고 승인
- [ ] 앱 근력 입력 `5일 이상` UI 확인
- [ ] 앱 성별·임신 unsupported 문구 확인
- [ ] authorized training config 생성
- [ ] 개발 snapshot·코드·환경 SHA-256 고정
- [ ] `do_not_train: false`가 authorized config에만 존재하는지 확인

