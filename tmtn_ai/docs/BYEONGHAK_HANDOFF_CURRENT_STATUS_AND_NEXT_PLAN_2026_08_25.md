# 병학님 D0·D1 handoff 검토 후 현재 단계와 다음 진행 방향

작성일: 2026-08-25  
공유 대상: 박강호·권병학·문홍주  
문서 상태: `TEAM_ALIGNMENT_DRAFT`

## 1. 전달 목적

병학님이 전달해주신 D0 calibration·D1 허리둘레 OOF handoff 패키지를 검토했습니다.

이번 문서는 상세 코드 검토 내용을 반복하기보다 다음 사항을 팀 기준으로 맞추기 위한 문서입니다.

- handoff에서 재사용할 부분과 공식 버전에서 수정할 부분
- 현재 전체 모델 개발 과정에서의 위치
- 홍주님 2023 materialization을 기다리는 동안 유지할 작업 경계
- materialization 패키지 수령 후 진행 순서
- 강호님·병학님·홍주님의 담당 범위

상세 기술 검토는 별도 문서인 `병학님 D0·D1 handoff 검토 결과`를 기준으로 합니다.

## 2. handoff 검토 결론

handoff 패키지는 실험 기록과 구조 검토 자료로 보존합니다. 다음 설계는 공식 vNext에도 재사용할 가치가 있습니다.

- 2019~2021 내부 OOF 허리둘레 추정 구조
- 각 행이 자신을 학습한 허리둘레 모델의 예측을 받지 않도록 한 구조
- 질환 모델에 실측 `HE_wc`를 직접 넣지 않고 `estimated_waist_cm`만 사용하는 구조
- 실측 허리둘레가 없는 행에도 입력 피처가 완전하면 추정값을 생성하는 구조
- D1과 동일한 대상자 집합으로 matched-D0를 다시 만들어 비교하는 구조
- 전처리 통계를 개발 구간에만 적합하는 원칙
- 기존 산출물을 덮어쓰지 않고 새 버전으로 생성하는 원칙

다만 현재 코드와 산출물을 공식 파이프라인에 그대로 편입하지는 않습니다.

주요 수정 사유는 다음과 같습니다.

1. 2022가 허리둘레 모델 선택과 calibration 적합에 사용돼 순수 temporal validation이 아닙니다.
2. 2023 성능·표본 수·calibration 결과가 이미 생성돼 현재 D0·D1 연구에서 2023을 미개봉 locked test라고 부를 수 없습니다.
3. `record_key`가 `source_year + participant_id`가 아니라 `source_year + source_row_number`로 구성돼 최신 계약과 다릅니다.
4. handoff canonical ETL은 2019~2023 라벨과 피처를 한 개발 경로에서 다루지만, 최신 계약은 2019~2022 개발 경로와 홍주님 전용 2023 materialization을 분리합니다.
5. 일부 과거 문서의 라벨·2024 역할이 최신 팀 계약과 충돌합니다.

따라서 기존 결과는 `historical experimental result`로 보존하고, 공식 vNext는 최신 canonical·분할·보안 계약에 맞춰 새 버전으로 작성합니다.

## 3. 현재 전체 단계

현재는 본격적인 모델 학습 단계 직전입니다.

| 단계 | 상태 |
|---|---|
| 역할·환경·보안 경계 | 대부분 확정, 2023 명칭 정리 필요 |
| 원시변수·공식 코드북 감사 | 완료 |
| 운동·측정값·결측·라벨 계약 | 완료 |
| 2019~2022 canonical ETL 기술 검증 | 완료 |
| 2023 custodian materialization | 홍주님 검토·실행 대기 |
| 공식 개발자료와 분할 생성 | 미실행 |
| EDA·D0 베이스라인 | 미시작 |
| 허리둘레 공식 서브모델 | 미시작 |
| 당뇨·고혈압 공식 확률모델 | 미시작 |
| 공개 게이트·제품 전달 | 미시작 |

즉, 현재 위치는 `canonical ETL 검증 완료 → 공식 분할·모델 개발 승인 직전`입니다.

지금은 기존 실험 수치를 더 만드는 것보다 평가 경계와 실행 계약을 고정하는 것이 우선입니다.

## 4. 지금 유지할 작업 경계

홍주님 materialization 패키지를 받기 전까지 다음 원칙을 유지합니다.

- 기존 `do_not_split: true`, `do_not_train: true`, `do_not_materialize: true` 계약을 직접 수정하지 않습니다.
- 실제 KNHANES train/test 파일을 새로 만들지 않습니다.
- 2022 또는 2023 결과를 추가로 확인해 모델·피처·임계값을 선택하지 않습니다.
- 2023 라벨·양성 수·성능 자료를 강호님·병학님 개발 경로에 두지 않습니다.
- 2024는 현재 분할·모델링 파이프라인에서 읽지 않습니다.
- 기존 D0·D1 결과는 공식 성능이 아닌 참고 실험 기록으로만 둡니다.
- 공식 코드로 가져올 부분은 아이디어와 함수 구조만 선별하고, 현재 frozen canonical 계약 위에서 다시 구현합니다.

## 5. 홍주님에게 받을 파일

강호님·병학님이 받을 개발자용 패키지는 다음 구조여야 합니다.

```text
locked_2023_canonical_v0_2/
├─ waist/test_2023_features.csv
├─ diabetes/test_2023_features.csv
├─ hypertension/test_2023_features.csv
├─ developer_manifest.json
└─ contract_snapshot.yaml
```

다음 자료는 홍주님 제한 경로에만 남아야 합니다.

- `test_2023_labels.csv`
- `locked_manifest.json`
- 라벨 분포·양성 수·평균
- 2023 평가 결과

홍주님은 materialization 성공 여부, 개발자용 manifest, 피처 패키지, SHA-256만 전달합니다.

## 6. materialization 수령 직후 공동 확인

모델을 실행하기 전에 다음을 먼저 확인합니다.

- [ ] 승인된 신규 materialization 계약 버전인지 확인
- [ ] 기존 v0.1 잠금 계약을 직접 수정한 파일이 아닌지 확인
- [ ] 파일 SHA-256과 `developer_manifest.json` 일치 확인
- [ ] `source_year + participant_id` 복합키 사용 확인
- [ ] 복합키 결측·중복 0건 확인
- [ ] 세 task 피처 파일 스키마 확인
- [ ] 정확히 승인된 6개 피처와 허용 메타 컬럼만 포함됐는지 확인
- [ ] 라벨·혈당·HbA1c·혈압·실측 허리둘레가 개발자 파일에 없는지 확인
- [ ] 2024 행 또는 경로가 없는지 확인
- [ ] 개발자 피처 경로와 홍주님 라벨 경로의 접근권한 분리 확인

이 검증에서 2023 성능은 계산하지 않습니다.

## 7. materialization 확인 후 공식 vNext 작업 순서

### 7.1 연도 역할과 명칭 고정

권장 역할은 다음과 같습니다.

| 연도 | 역할 | 사용 규칙 |
|---|---|---|
| 2019~2021 | development·OOF | 모델·피처·calibration 선택 |
| 2022 | temporal validation | 선택·적합에 쓰지 않고 평가만 수행 |
| 2023 | opened internal benchmark | 홍주님 분리 평가, 공식 독립 최종 성능으로 주장하지 않음 |
| 2024 | forbidden for current pipeline | 현재 분할·학습·평가에서 사용하지 않음 |
| 미래 연도 또는 외부 코호트 | independent final evaluation | 공개용 독립 최종 평가 |

2023을 `opened internal benchmark`로 변경하는 이유는 기존 D0·D1 실험 결과가 이미 생성·공유됐기 때문입니다. 파일을 다시 숨겨도 사람이 확인한 평가 정보까지 미개봉 상태로 되돌릴 수는 없습니다.

### 7.2 신규 승인 계약 생성

기존 잠금 파일을 수정하지 않고 다음을 새 버전으로 만듭니다.

- 공식 연도 역할 계약
- 공식 split config
- 공식 모델 개발 실행 계약
- 2019~2022 canonical snapshot manifest
- 2023 developer package manifest 연결 정보

신규 계약에서만 승인된 `do_not_split: false`와 필요한 실행 권한을 적용합니다. `do_not_train` 해제도 별도 공동 승인 후 신규 버전에서만 수행합니다.

### 7.3 2019~2022 task별 개발자료 생성

각 task에 대해 다음 자료를 생성합니다.

```text
waist/
├─ development_2019_2021.csv
└─ temporal_validation_2022.csv

diabetes/
├─ development_2019_2021.csv
└─ temporal_validation_2022.csv

hypertension/
├─ development_2019_2021.csv
└─ temporal_validation_2022.csv
```

적용 원칙:

- 19세 이상·비임신자
- task 라벨 결측자 제외
- 승인된 6개 피처만 모델 입력 후보로 사용
- 피처 결측을 분할 전에 0·평균으로 대치하지 않음
- 분할 전에 스케일링·인코딩·피처 선택을 수행하지 않음
- Random 8:2는 2019~2021 안에서만 보조 비교용으로 생성

### 7.4 실험계획 사전 고정

성능을 확인하기 전에 다음을 공동 문서로 확정합니다.

- 허리둘레 모델 후보와 선택 기준
- D0·D1 질환 모델 후보
- 내부 CV 또는 nested CV 방식
- OOF 허리둘레 생성 방식
- calibration 방식과 적합 데이터
- 결측 처리 방식
- random seed와 fold 저장 방식
- 성별·연령대·연도별 하위집단
- KNHANES 가중·비가중 민감도 분석
- 허리둘레 MAE·RMSE·상대오차 기준
- 질환 모델 AUROC·AUPRC·Brier·log loss·calibration 기준
- 모델 중단·공개 게이트 기준

## 8. 공식 모델 개발 순서

### 8.1 D0 베이스라인

승인된 직접 입력만 사용하는 당뇨·고혈압 모델을 먼저 만듭니다.

```text
6개 직접 입력
→ D0 당뇨 측정 이상 확률
→ D0 고혈압 측정 이상 확률
```

2019~2021 내부에서 모델 선택과 calibration을 끝내고 2022는 평가에만 사용합니다.

### 8.2 허리둘레 서브모델

```text
6개 직접 입력
→ OOF estimated_waist_cm
```

- 2019~2021 내부 fold별 학습
- 각 행은 자신을 학습하지 않은 모델의 예측만 사용
- 허리둘레 모델 선택도 2019~2021 내부에서 수행
- 2022는 동결된 개발 모델로 예측·평가
- 실측 허리둘레 관측자 성능과 결측자 예측 커버리지를 분리

### 8.3 D1 질환 모델

```text
6개 직접 입력
→ OOF estimated_waist_cm
→ D1 당뇨·고혈압 확률
```

D0와 D1을 동일한 대상자 집합에서 비교합니다. 허리둘레 추정값이 calibration 또는 discrimination에 실질적인 추가 가치를 주지 못하면 D1을 P0 공식 모델로 채택하지 않을 수 있습니다.

### 8.4 2022 평가와 모델 동결

다음을 확인한 후 모델·피처·전처리·calibration 버전을 동결합니다.

- 연도 이동 성능
- calibration
- 성별·연령 하위집단
- 누설 검사
- KNHANES 가중·비가중 민감도
- 동일 입력·동일 버전 재현성

2022 결과를 보고 설계를 바꾸면 새 실험 버전으로 처음부터 다시 기록하고, 2022가 반복 개발에 사용됐음을 명시합니다.

### 8.5 2023 예측과 홍주님 평가

동결 모델로 개발자용 2023 피처의 예측 파일만 생성합니다.

```text
test_2023_features.csv
→ frozen model·preprocessor·calibrator
→ test_2023_predictions.csv
→ 홍주님 전달
```

홍주님이 정답키와 결합해 사전 정의된 지표를 계산합니다. 강호님·병학님은 정답키를 전달받지 않습니다.

이번 2023 결과는 내부 benchmark로 기록하며, 독립 최종 공개 성능은 미래 KNHANES 연도 또는 외부 코호트에서 평가합니다.

## 9. 역할 제안

### 병학님

- handoff에서 재사용할 OOF·matched-D0 구조 정리
- handoff 코드가 현재 frozen canonical 출력만 입력받도록 adapter 설계
- 공식 컬럼명 `age_years`, `sex_code`, `height_cm`, `weight_kg`, `leisure_aerobic_moderate_equivalent_min_week`, `strength_days_week`로 통일
- `source_year + participant_id` 복합키 적용
- 2019~2022 task별 frame·manifest 생성 코드 작성
- 2023·2024 접근 차단 테스트 작성
- 과거 `LABEL_CONTRACT`·`VALIDATION_PLAN`에 superseded 표시
- 데이터 snapshot·계약·코드 해시 기록 구조 작성

### 강호님

- D0·허리둘레·D1 모델 후보와 실험 순서 확정
- nested CV·OOF·calibration 방식 확정
- 허리둘레 및 질환 모델 평가지표·게이트 기준 작성
- 하위집단·연도 안정성·가중 평가 계획 작성
- 모델·전처리·calibration 버전 규칙 작성
- 제품 출력 의미와 API 계약 연결

### 홍주님

- 2023 canonical materialization 실행
- 개발자 피처와 정답키의 물리적·권한상 분리
- 정답키와 locked manifest 보관
- 동결 예측 수령 후 사전 정의된 지표 평가
- 라벨·분포·성능의 평가 전 비공개 유지

### 공동

- 신규 계약 승인
- materialization 인계 검증
- 2023 명칭 확정
- 2022 평가 전 모델 선택 규칙 동결
- 공식 모델 동결 및 공개 게이트 승인

## 10. 당장 병학님과 맞출 사항

1. 기존 D0·D1 산출물은 수정하거나 삭제하지 않고 실험 기록으로 보존합니다.
2. 공식 vNext는 기존 코드에 덧대지 않고 최신 canonical·split 계약을 입력 기준으로 새 버전을 만듭니다.
3. 2022에서 모델 선택과 calibration을 하지 않고 2019~2021 내부 OOF에서 완료합니다.
4. 2023을 현재 연구의 독립 locked test가 아닌 내부 benchmark로 취급하는 권고안을 확인합니다.
5. 홍주님 패키지를 받기 전에는 실제 데이터 분할·추가 학습·성능평가를 진행하지 않습니다.
6. 홍주님 패키지를 받으면 먼저 manifest·키·스키마·라벨 비노출을 공동 확인합니다.
7. 확인이 끝난 뒤 병학님은 공식 task frame·adapter·manifest 트랙, 강호님은 모델·calibration·검증 트랙을 진행합니다.

## 11. 병학님께 전달할 짧은 메시지

병학님, D0·D1 handoff 패키지 검토했습니다. OOF 허리둘레 생성, HE_wc 직접 입력 차단, 결측자 예측 범위 확장, matched-D0 비교 구조는 공식 vNext에도 재사용하면 좋겠습니다. 다만 현재 팀 계약 기준으로 2022가 모델 선택·calibration 적합에 사용돼 순수 temporal validation이 아니고, 2023 결과가 이미 생성·공유돼 현재 연구에서는 미개봉 locked test로 보기 어렵습니다. 기존 결과는 실험 기록으로 보존하고, 공식 vNext는 2019~2021 내부 OOF에서 모델 선택과 calibration을 끝낸 뒤 2022를 평가 전용으로 사용하는 방향을 권장합니다. 홍주님 materialization 패키지를 받기 전에는 추가 분할·학습·평가를 멈추고, 수령 후에는 manifest·복합키·6개 피처·라벨 비노출부터 같이 확인하면 됩니다. 이후 병학님은 공식 canonical adapter와 task frame·manifest, 저는 모델 후보·OOF·calibration·검증 게이트를 맡아 진행하는 방향으로 생각하고 있습니다.
