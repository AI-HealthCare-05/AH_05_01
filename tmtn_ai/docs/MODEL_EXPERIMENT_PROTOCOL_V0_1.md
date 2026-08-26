# TMTN 공식 모델 실험 프로토콜 v0.1

작성일: 2026-08-25  
상태: `JOINT_REVIEW_PENDING`  
실제 학습: 금지  
기계 판독 계약: `contracts/model_experiment_protocol_v0_1.yaml`

## 1. 목적

이 문서는 다음 세 모델을 같은 데이터·분할·누설 방지·calibration 기준으로 개발하기 위한 사전 실험계획이다.

1. 허리둘레 추정 회귀모델
2. 당뇨 측정 이상 참고 확률모델
3. 고혈압 측정 이상 참고 확률모델

질환 출력은 미래 발병률이나 임상 진단 확률이 아니라 `현재 단일 검진에서 측정 이상 기준을 충족할 참고 확률`이다.

틈틈지수는 이 프로토콜 범위가 아니다. 개별 모델과 종합 공식이 별도 검증을 통과하기 전에는 단순 평균이나 임의 가중평균을 사용하지 않는다.

## 2. 현재 잠금 상태

이 프로토콜은 검토용 v0.1이며 `do_not_train: true`다.

다음 항목을 공동 확정하고 새 authorized training 계약을 만들기 전에는 실제 KNHANES 학습을 시작하지 않는다.

- 모델별 hyperparameter grid
- 수치형 공개·중단 기준
- survey weight 결측 처리
- 신뢰구간 계산 방식
- 앱 입력 유효범위와 unsupported 처리
- 강호님·병학님 승인

## 3. 연도 역할

| 연도 | 역할 | 허용 | 금지 |
|---|---|---|---|
| 2019~2021 | development | EDA, 후보 비교, nested CV, OOF, calibration fit | reserved 연도 사용 |
| 2022 | temporal validation | 완전 동결 후 평가 | 모델·피처·결측·전처리·calibration 선택 또는 적합 |
| 2023 | opened internal benchmark | 홍주님 환경의 동결 추론·집계 평가 | 개발자의 행 단위 접근·튜닝 |
| 2024 | forbidden | 없음 | 읽기·필터링·학습·평가 |
| 미래 연도·외부 코호트 | independent final evaluation | 별도 custodian 평가 | 동결 전 개발자 접근 |

2022 결과를 보고 설계를 바꾸면 새 실험 버전을 만든다. 그 이후에는 2022가 해당 후속 버전의 완전한 미개봉 validation이 아니라는 사실을 기록한다.

2023은 기존 실험 결과가 이미 공유됐기 때문에 독립 최종 holdout으로 표현하지 않는다.

## 4. 레코드와 피처 계약

공식 복합키는 다음 두 컬럼이다.

```text
source_year + participant_id
```

`source_row_number`는 파일 정렬·재내보내기에 따라 달라질 수 있으므로 공식 record key로 사용하지 않는다.

P0 primary 모델의 6개 피처는 다음과 같다.

1. `age_years`
2. `sex_code`
3. `height_cm`
4. `weight_kg`
5. `leisure_aerobic_moderate_equivalent_min_week`
6. `strength_days_week`

피처 세트 역할:

- `F0`: 나이·성별·키·몸무게만 사용하는 baseline 및 별도 fallback
- `F2_leisure`: 승인된 6개 피처를 사용하는 primary
- `F1`: 앱 입력과 직접 일치하지 않아 기본 실험에서 비활성
- `F2_total`: 직업·이동 활동까지 포함하므로 연구 comparator 전용

BMI 같은 결정론적 파생 피처도 v0.1에서는 추가하지 않는다. 추가하려면 성능을 보기 전에 새 프로토콜 버전으로 승인한다.

## 5. 전처리·결측 규칙

### 전처리

- scaler·encoder는 현재 학습 fold 안에서만 적합한다.
- 선형모델의 연속형 피처는 fold 내부 `StandardScaler`를 사용한다.
- 트리모델은 명시적 필요가 없으면 스케일링하지 않는다.
- `sex_code`는 고정된 KNHANES 값 공간으로 인코딩한다.
- `strength_days_week`는 `0, 1, 2, 3, 4, 5_plus` 범주를 보존한다.
- `5_plus`를 숫자 6으로 바꾸지 않는다.
- 동결 계약에 없는 범주는 자동 추정하지 않고 fail 또는 unsupported로 처리한다.

### 결측

- 타깃 결측 대치는 금지한다.
- F2 primary 분석은 complete-case로 시작한다.
- 운동 결측을 0으로 바꾸지 않는다.
- 일반 평균 대치는 v0.1에서 사용하지 않는다.
- 명시적으로 운동 비참여 분기를 선택한 경우에만 구조적 0을 허용한다.
- 운동 입력이 없는 사용자를 위한 F0는 별도 모델로 관리하며 F2 결과로 표시하지 않는다.

## 6. 개발 resampling

### 보조 Random 8:2

- 대상: 2019~2021만
- seed: 42
- 역할: 재현성과 기존 random split 비교
- 최종 모델 선택을 Random 8:2 한 번의 결과만으로 결정하지 않는다.

### 공식 nested cross-fitting

- outer fold: 5
- inner fold: 4
- 기본 seed: 42
- 반복 안정성 seed: 42, 1042, 2042
- fold assignment를 composite key와 함께 별도 artifact로 저장한다.
- fold별 train·holdout key 교집합은 반드시 0이어야 한다.
- 작은 strata를 합치는 규칙은 첫 fit 전에 기록한다.

질환 task는 라벨 층화를 우선하고 연도·성별 균형을 점검한다. 허리둘레 회귀는 연도·성별과 사전 정의된 target quantile 균형을 사용하되, 작은 층은 사전 고정 규칙으로 합친다.

## 7. 허리둘레 서브모델

### 후보 입력

- `W0`: F0 입력 baseline
- `W2`: F2_leisure 6개 입력 primary 후보

### 후보 모델

- Linear Regression
- Elastic Net
- Random Forest Regressor

정확한 hyperparameter grid는 실제 학습 전에 YAML로 고정한다.

### 선택 기준

- primary: MAE(cm)
- tie-breaker: RMSE, 사전 정의 하위집단 중 가장 나쁜 MAE, 모델 단순성
- 2022 성능으로 모델을 선택하지 않는다.

### 필수 OOF 조건

- 개발 행은 자신을 학습하지 않은 허리둘레 모델의 예측만 받는다.
- 실측 `waist_cm`은 허리둘레 모델의 정답으로만 사용한다.
- 질환 모델에 실측 허리둘레를 직접 넣지 않는다.
- 실측 허리둘레가 없어도 6개 입력이 완전하면 추정값을 생성할 수 있다.

### 보고 지표

- MAE(cm)
- RMSE(cm)
- Median Absolute Error
- R²
- MAPE
- 3cm·5cm·10cm 이내 비율
- residual 평균·표준편차
- 성별·연령·연도 하위집단

기존 2~3% 목표는 아직 공식 release gate가 아니다. 상대오차 계산식과 대상 범위를 공동 고정한 뒤 새 버전에 반영한다.

## 8. 질환 확률모델

### 모델 구조

```text
D0 = F2_leisure 직접 입력
D1 = F2_leisure + nested cross-fitted estimated_waist_cm
matched-D0 = D1과 정확히 같은 행에서 estimated_waist_cm만 제외
F0 fallback = 별도 4개 입력 모델
```

### 후보 모델

- Logistic Regression, class weight 없음
- Logistic Regression, balanced
- Random Forest, class weight 없음
- Random Forest, balanced

후보 추가는 첫 성능 확인 전에 새 프로토콜 버전으로 승인한다.

### nested upstream waist 규칙

질환 모델 outer fold를 평가할 때 outer holdout의 참여자는 허리둘레 모델 선택·전처리·학습에서도 제외한다.

```text
질환 outer-train
├─ inner cross-fitting으로 outer-train용 estimated_waist 생성
├─ inner CV로 허리둘레 후보 선택
└─ outer-train 전체 관측 허리로 최종 outer waist model 적합

질환 outer-holdout
└─ outer-train에서만 적합한 waist model로 estimated_waist 생성
```

그다음 outer-train으로 질환 모델을 학습하고 outer-holdout을 예측한다. 이 구조는 전체 D1 pipeline의 성능을 평가하며 upstream 모델의 test-row 유입을 막는다.

### 모델 선택 기준

확률 출력이 목적이므로 development OOF의 calibrated Brier score를 primary로 사용한다.

tie-breaker:

1. Log loss
2. Calibration slope의 1과의 거리
3. Calibration intercept의 0과의 거리
4. PR-AUC
5. ROC-AUC
6. 모델 단순성

## 9. Calibration

calibration은 2019~2021의 cross-fitted OOF 예측만으로 적합한다.

비교 대상:

- 보정 없음
- 무규제 Platt logistic recalibration

Isotonic calibration은 v0.1에서 비활성화한다. 사용하려면 새 프로토콜 버전이 필요하다.

2022와 2023에서 calibrator를 적합하지 않는다.

보고 항목:

- Brier score
- Log loss
- Calibration intercept
- Calibration slope
- 사전 고정 10-bin ECE
- Calibration table

진단용 calibration intercept·slope는 기본 L2 규제가 아닌 무규제 logistic regression으로 계산한다.

## 10. 2022 temporal validation

2022를 열기 전에 다음을 동결한다.

- 후보 모델 registry
- hyperparameter grid
- fold registry
- 피처와 결측 처리
- 전처리
- development OOF에서 선택·적합한 calibration
- 전체·하위집단 지표
- 코드·환경 hash

2022에서는 fitting을 수행하지 않고 다음만 계산한다.

- 전체 집계 성능
- 성별·연령 하위집단
- 비가중·가중 민감도
- calibration table
- 사전 정의 gate 판정

2022 결과를 보고 수정할 경우 새 experiment version을 만든다.

## 11. KNHANES 가중 평가

v0.1의 primary 학습은 비가중으로 시작한다. 공개 전 민감도 분석에서는 다음을 함께 보고한다.

- 비가중 결과
- `examination_weight`를 적용한 결과
- 유효 가중치 coverage
- strata·PSU를 고려한 불확실성

가중치 결측을 대치하지 않는다. 2019~2021 가중치 결측 행의 정확한 분석 규칙과 design-based 신뢰구간 방식은 실제 학습 승인 전에 공동 고정한다.

## 12. 하위집단과 불확실성

필수 하위집단:

- 연도
- 성별
- 19~39세
- 40~64세
- 65세 이상

선택 교차집단:

- 성별 × 연령대

개인정보 보호 최소 셀은 5명이다. 안정적인 성능 추정에 필요한 최소 event 수는 별도로 공동 결정한다. 표본이 부족하면 숫자를 억지로 계산하지 않고 `insufficient support`로 표시한다.

95% 신뢰구간을 보고한다. bootstrap 또는 복합표본 설계 기반 방식은 학습 승인 전 고정한다.

## 13. D0와 D1 채택 규칙

D1은 matched-D0보다 추가 가치가 재현되고 calibration이 악화되지 않을 때만 채택한다.

추가 가치가 없으면 다음과 같이 처리한다.

- 질환 모델은 D0 유지
- 허리둘레 추정값은 별도 허리둘레 참고 결과로만 사용
- 질환 모델 복잡도를 늘리기 위해 D1을 강제로 채택하지 않음

## 14. 모델 동결과 홍주님 전달

2023용 bundle은 2019~2021만으로 적합한 모델을 사용한다. 2022는 평가 전용 역할을 유지하기 위해 refit에 포함하지 않는다.

동결 항목:

- fold registry
- 후보와 선택 결과
- hyperparameter
- 모델
- preprocessor
- calibrator
- 입력·출력 schema
- evaluation plan
- model card
- dependency lock
- SHA-256

홍주님에게 전달한 후 bundle을 수정하지 않는다. 수정하려면 새 버전과 새 체크섬을 만든다.

## 15. 공개 게이트

다음은 전부 필수다.

- schema·계약 일치
- 라벨·측정값 누설 0건
- outer·inner fold key 교집합 0건
- 2022 fit·선택 0건
- 개발자의 2023·2024 접근 0건
- 동일 입력 재현성
- 전체 성능
- calibration
- 연도 안정성
- 하위집단 성능
- 가중 민감도
- 안전 문구 승인

수치형 통과 기준은 아직 정하지 않았다. 기준을 versioned contract로 고정하기 전에는 `do_not_train: true`를 해제하지 않는다.

## 16. 실제 학습 전 공동 결정 항목

- [ ] 허리둘레 모델 hyperparameter grid
- [ ] 질환 모델 hyperparameter grid
- [ ] 허리둘레 수치형 통과 기준
- [ ] 질환모델 discrimination·calibration 통과 기준
- [ ] 하위집단 최소 event 수
- [ ] survey weight 결측 처리
- [ ] 95% 신뢰구간 계산 방식
- [ ] 앱 입력 유효범위·unsupported 정책
- [ ] 강호님 승인
- [ ] 병학님 승인
- [ ] 신규 authorized training 계약 생성

위 항목이 끝나기 전에는 실제 KNHANES 학습을 실행하지 않는다.

