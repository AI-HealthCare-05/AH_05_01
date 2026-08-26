# TMTN 분할 연도 역할 및 보안 계약 v0.1

## 1. 목적

기존의 `2019~2023 개발 + 2024 sealed holdout` 가정을 제거하고, 확정된 A5 연도 역할을 코드·설정·출력 구조에 동일하게 적용한다.

이 단계는 분할 코드 생성과 합성 검증만 수행한다. 실제 KNHANES canonical 파일 생성·분할·학습·성능평가는 수행하지 않는다.

## 2. 확정 연도 역할

| 역할 | 연도 | 사용 규칙 |
|---|---|---|
| 개발 | 2019~2021 | random 8:2 보조 비교와 temporal train |
| 시간 검증 | 2022 | 사전 정의된 안정성·모델 선택 점검 |
| 잠금 내부 테스트 | 2023 | 모델·전처리·보정 동결 후 1회 평가 |
| 금지 | 2024 | 현재 분할 입력에 존재하면 즉시 실패 |

연도 역할은 코드에서 A5 v0.1로 고정한다. YAML만 임의로 바꿀 수 없으며 역할 변경에는 새로운 버전의 계약과 코드 검토가 필요하다.

## 3. Random 8:2의 역할

Random 8:2는 2019~2021 개발자료 안에서만 만든다.

- 분류: 라벨 층화
- 허리둘레 회귀: 타깃 분위수 근사 층화
- seed: 42
- 2022·2023·2024 포함 금지
- 시간 검증 또는 잠금 테스트를 대체하지 않음

동일한 2019~2021 전체 자료는 temporal train 출력에도 보존한다. random 출력과 temporal train의 행이 겹치는 것은 서로 다른 평가 설계를 위한 의도된 중복이다.

## 4. Task별 canonical 입력

| Task | 라벨 | 6개 입력 피처 |
|---|---|---|
| 허리둘레 | `waist_cm` | 공통 6개 |
| 당뇨 | `diabetes_measurement_label_raw` | 공통 6개 |
| 고혈압 | `hypertension_measurement_label_raw` | 공통 6개 |

공통 피처는 다음과 같다.

1. `age_years`
2. `sex_code`
3. `height_cm`
4. `weight_kg`
5. `leisure_aerobic_moderate_equivalent_min_week`
6. `strength_days_week`

입력은 원자료가 아니라 eligibility가 적용되고 라벨 결측이 제거된 task별 canonical table이어야 한다. 분할기에서 결측 대치·스케일링·인코딩·피처 선택을 수행하지 않는다.

## 5. 레코드 키

KNHANES ID 문자열의 연도 간 전역 유일성을 가정하지 않는다.

- 복합 레코드 키: `source_year + participant_id`
- 같은 연도 안에서 동일 ID 중복: 실패
- 다른 연도에서 같은 ID 문자열 재사용: 서로 다른 반복 횡단면 레코드로 허용
- split 간 누설 검사도 복합키 기준

## 6. 출력 구조

개발자 경로:

```text
<output>/<task>/<dataset_version>/
├─ development/
│  ├─ random/train.csv
│  ├─ random/validation.csv
│  ├─ temporal/train.csv
│  └─ temporal/validation.csv
├─ locked_internal_test/
│  └─ test_2023_features.csv
└─ manifests/
   ├─ config_snapshot.yaml
   ├─ split_manifest.json
   └─ integrity_manifest.json
```

접근 제한 경로:

```text
<locked-label-output>/<task>/<dataset_version>/
├─ test_2023_labels.csv
└─ sealed_manifest.json
```

2023 개발자용 파일에는 라벨이 없다. 정답키에는 복합키 구성 컬럼과 라벨만 저장한다. manifest에는 라벨 분포·평균·성능·예시 행을 기록하지 않는다.

## 7. 안전장치

- 배포 설정 3개 모두 `do_not_split: true`
- `do_not_split`이면 입력 CSV를 읽기 전에 중단
- 2024가 입력에 있으면 자동 제외하지 않고 실패
- 개발 출력과 잠금 정답 경로가 같거나 포함 관계이면 실패
- 입력·설정·코드·산출물 SHA-256 기록
- 원본 파일 실행 전후 SHA-256 일치 확인
- 동일 task/version 덮어쓰기 기본 금지
- 직접 식별자로 보이는 피처명 차단

## 8. 현재 구현물

- 계약: `contracts/split_contract_v0_1.yaml`
- 설정: `configs/waist_split.yaml`, `configs/diabetes_split.yaml`, `configs/hypertension_split.yaml`
- 분할: `src/split_dataset.py`
- 검증: `src/split_validation.py`
- 합성 테스트: `tests/test_split_dataset.py`

## 9. 실제 분할 전 공동 승인 항목

- [ ] 박강호·병학 연도 역할 및 6개 피처 계약 승인
- [ ] 2023 정답키 보관 담당자 지정
- [ ] 개발 출력과 정답키의 실제 접근권한 분리 확인
- [ ] 2023 canonical materialization 절차 승인
- [ ] task별 eligibility 필터·라벨 결측 제거 절차 승인
- [ ] 새 버전 config 생성
- [ ] 새 버전에서만 `do_not_split: false` 적용
- [ ] 실제 실행 담당자와 인계 절차 기록

위 항목 완료 전에는 현재 설정을 수정해 실행하지 않는다.
