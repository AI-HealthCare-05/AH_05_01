# TMTN 2023 custodian 운영 계약 v0.2

작성일: 2026-08-25  
계약 상태: `JOINT_REVIEW_PENDING`  
실행 잠금: `do_not_materialize: true`, `do_not_score: true`  
기계 판독 계약: `contracts/custodian_2023_operation_contract_v0_2.yaml`

## 1. v0.2의 핵심 변경

v0.1은 2023 task별 피처 파일을 강호님·병학님에게 전달하고, 개발자가 예측 파일을 다시 홍주님에게 보내는 구조였다.

v0.2에서는 2023 행 단위 피처도 홍주님 환경 밖으로 내보내지 않는다. 라벨이 없어도 결측률·이상치·범주·분포를 확인하면 2023에 맞춰 전처리나 모델을 조정할 수 있기 때문이다.

새 흐름은 다음과 같다.

```text
강호·병학: 2019~2022만 사용
        ↓
모델·전처리·결측 처리·calibration·평가계획 동결
        ↓
동결 inference bundle과 SHA-256을 홍주님에게 전달
        ↓
홍주님 PC에서 2023 canonical·task cohort 생성
        ↓
홍주님 PC에서 동결 bundle로 2023 예측
        ↓
홍주님이 예측과 정답키를 결합해 평가
        ↓
팀에는 사전 정의된 집계 결과만 공유
```

## 2. 2023의 평가 지위

기존 D0·D1 실험에서 2023 집계 성능과 calibration 결과가 이미 생성·공유됐다. 따라서 파일을 다시 격리해도 현재 연구의 2023을 완전한 미개봉 최종 holdout으로 되돌릴 수 없다.

v0.2에서 2023의 공식 명칭은 다음과 같다.

- 명칭: `opened internal benchmark`
- 허용: 동결 버전의 내부 시간 benchmark
- 금지: 미개봉 locked test, 독립 최종 평가, 공개용 최종 성능으로 표현
- 최종 독립 평가: 미래 KNHANES 연도 또는 외부 코호트

이 지위 변경과 별개로 custodian 분리 절차는 유지한다. 추가 정보 노출을 막고 미래 독립 평가의 운영 절차를 검증하기 위해서다.

## 3. 역할

### 강호님·병학님

- 2019~2021에서 모델·피처·전처리·calibration을 개발한다.
- 2022는 사전에 고정된 temporal validation 평가에만 사용한다.
- 2023 원자료·피처·라벨·행 단위 예측을 열람하지 않는다.
- 동결 inference bundle과 평가계획을 홍주님에게 전달한다.
- 2023 결과로 같은 동결 버전을 재튜닝하지 않는다.

### 홍주님

- hn23 원자료와 2023 행 단위 산출물을 보관한다.
- frozen canonical 규칙으로 2023 task cohort를 생성한다.
- 피처·라벨·예측을 분리된 경로에 저장한다.
- 동결 bundle의 체크섬과 합성 smoke test를 확인한다.
- 라벨 경로를 추론 프로그램에 전달하지 않고 추론을 수행한다.
- 별도 평가 단계에서만 예측과 정답키를 결합한다.
- 사전 정의된 집계 결과만 팀에 공유한다.

## 4. 저장 경계

홍주님 환경에는 최소 네 개의 분리된 저장 경계가 필요하다.

```text
custodian_workspace/
├─ raw_2023/              # 홍주님 전용 원자료
├─ task_features/         # 홍주님 전용, 라벨 없음
├─ locked_labels/         # 홍주님 전용 정답키
├─ predictions/           # 홍주님 전용 행 단위 예측
└─ aggregate_results/     # release gate 통과 후 집계만 공유
```

- `task_features`는 개발자에게 전달하지 않는다.
- `locked_labels`는 추론 entrypoint가 읽을 수 없어야 한다.
- `predictions`는 라벨을 포함하지 않는다.
- `aggregate_results`에는 개인키·행·예시값을 포함하지 않는다.
- 기존 버전 경로를 덮어쓰지 않는다.

## 5. materialization 전 확인

- [ ] v0.2 계약을 강호·병학·홍주가 검토했다.
- [ ] 현재 `src/materialize_locked_2023.py`의 개발자 피처 export가 제거 또는 차단됐다.
- [ ] 합성 테스트가 통과했다.
- [ ] 원자료·피처·라벨·예측·집계 경로가 확정됐다.
- [ ] 각 경로의 실제 OS 접근권한을 확인했다.
- [ ] `source_year + participant_id` 결측·중복 시 fail-closed가 적용됐다.
- [ ] 2024가 발견되면 필터링하지 않고 즉시 실패한다.
- [ ] 새 승인 snapshot을 만들었다.

현재 review 계약을 직접 수정해 잠금을 풀면 안 된다. 위 확인 후 새 authorized snapshot에서만 `do_not_materialize: false`를 설정한다.

## 6. 모델 동결 전 팀에 공유할 수 있는 정보

홍주님은 다음 pass/fail 정보만 전달한다.

```text
MATERIALIZATION_READINESS
- contract_version
- contract_sha256
- code_sha256
- schema_gate_status
- key_integrity_gate_status
- feature_allowlist_gate_status
- label_separation_gate_status
- forbidden_year_gate_status
- custodian_environment_ready
```

모델 동결 전에는 다음을 공유하지 않는다.

- 정확한 행 수
- 결측률·이상치율·범주 빈도
- 평균·표준편차·최솟값·최댓값
- 라벨 분포·양성 수
- 행 예시
- 2023 성능

## 7. 동결 inference bundle

강호님·병학님은 다음을 하나의 읽기 전용 버전 패키지로 전달한다.

```text
frozen_inference_bundle/
├─ model_manifest.json
├─ input_schema.yaml
├─ output_schema.yaml
├─ evaluation_plan.yaml
├─ requirements.lock
├─ SHA256SUMS.txt
├─ inference_entrypoint
├─ task_models/
├─ preprocessors/
├─ calibrators/
└─ synthetic_smoke_test/
```

`model_manifest.json`에는 canonical snapshot, 피처 계약, 실험 프로토콜, 모델·전처리·calibration 버전, 코드 hash, dependency hash, random seed, 학습 연도와 2022 평가 연도를 기록한다.

체크섬을 만든 뒤에는 bundle을 수정하지 않는다. 수정이 필요하면 새 버전을 만든다.

## 8. 실행 단계 분리

### 8.1 Materialization

- 입력: hn23 원자료, 승인된 canonical·materialization 계약
- 출력: task별 피처, task별 정답키, 내부 manifest
- 모델 실행: 금지

### 8.2 Inference

- 입력: task별 피처, frozen inference bundle
- 출력: task별 행 단위 예측, inference receipt
- 라벨 접근: 금지
- 네트워크 접근: 금지

### 8.3 Evaluation

- 입력: 행 단위 예측, 정답키, 동결 evaluation plan
- 출력: 홍주님 내부 평가보고서, 공유 가능한 집계보고서
- 모델·전처리·calibration 수정: 금지

### 8.4 Release

- 사전 정의된 지표만 공유한다.
- 최소 셀 기준에 미달한 하위집단은 억제한다.
- 행 단위 예측·정답·개인키는 공유하지 않는다.
- 결과 명칭은 `2023 opened internal benchmark`로 고정한다.

## 9. 예상하지 못한 스키마·품질 문제

2023 실행 중 예상하지 못한 범주·결측·스키마가 발견되면 해당 버전은 평가하지 않는다.

홍주님은 구체적인 값이나 분포 대신 다음과 같은 실패 코드만 전달한다.

```text
SCHEMA_GATE_FAIL
- task: diabetes
- affected_contract_field: strength_days_week
- evaluation_not_executed: true
```

문제를 보고 전처리나 계약을 바꾸면 새 bundle 또는 계약 버전을 만든다. 이 변경은 2023 정보를 본 뒤 이루어진 것이므로 이후 2023 결과는 최초 미개봉 평가로 해석할 수 없다.

## 10. 승인 항목

- [ ] 강호님 승인
- [ ] 병학님 승인
- [ ] 홍주님 승인
- [ ] custodian-only materializer 코드 개정
- [ ] 합성 테스트 통과
- [ ] 실제 경로·권한 확인
- [ ] 승인 계약 snapshot 생성
- [ ] 공식 모델 실험 프로토콜 승인
- [ ] 동결 bundle 인계 규격 승인

승인 전에는 2023 materialization·추론·평가를 실행하지 않는다.

