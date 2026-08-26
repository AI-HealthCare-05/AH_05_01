# 틈튼 KNHANES canonical ETL 설계 v0.1

작성일: 2026-08-25  
상태: 계약·합성 구현 완료, 2019~2022 집계 확인 대기  
모델 학습: 금지

## 1. 목적

원시 KNHANES의 연도별 컬럼과 특수코드를 모델 개발에 직접 사용하지 않고, 지금까지 확정한 임신 제외·운동·HbA1c·혈압 규칙을 동일한 출력 스키마로 변환한다.

canonical ETL v0.1은 다음 원칙을 따른다.

- 입력 행을 삭제하지 않는다.
- 모델 사용 가능 여부와 타깃 생성 가능 여부를 플래그로 분리한다.
- 결측·구조적 비해당·범위 위반을 0으로 바꾸지 않는다.
- 원측정값과 연구자 전환 민감도 값을 분리한다.
- 라벨 원천값과 comparator를 모델 입력에서 차단한다.
- 이 단계에서는 canonical 행 또는 train/test CSV를 출력하지 않는다.

## 2. 처리 순서

```text
원시 KNHANES 한 연도
  → 필수 컬럼·연도 검증
  → 숫자·범주 변환과 품질 사유 기록
  → 성인·성별/임신코드 일관성 판정
  → 신체계측 candidate guard 적용
  → 운동 파생식 적용
  → HbA1c·혈압 원자료와 민감도 값 병렬 생성
  → 허리둘레·당뇨·고혈압 타깃 eligibility 및 라벨 생성
  → F0/F1/F2-leisure 완전성 플래그 생성
  → 누설 방지 역할표 적용
```

## 3. 행과 코호트 정책

모든 입력 행은 canonical 결과에 남긴다. 다음 행을 실제 학습 태스크에 사용할지는 별도 eligibility로 결정한다.

### P0 일반 성인 eligibility

```text
adult_eligible = age >= 19

pregnancy_eligible =
    (sex == 1 AND HE_prg == 8)
    OR (sex == 2 AND HE_prg == 0)

p0_general_adult_eligible =
    adult_eligible AND pregnancy_eligible
```

- `sex=1, HE_prg=8`: 남성·임신 비해당
- `sex=2, HE_prg=0`: 여성·비임신
- `sex=2, HE_prg=1`: 임신 여성, 행 보존·P0 제외
- 성별과 임신코드가 모순되거나 결측이면 행 보존·P0 제외·품질 플래그

## 4. 측정 실패·유효범위·결측 처리

### 공통 처리

| 원시 상태 | canonical 처리 |
|---|---|
| 원시 결측 | 결측 유지, `source_missing` |
| 숫자 변환 실패 | 결측, `numeric_conversion_failure` |
| NaN 이외 무한값 | 결측, `nonfinite` |
| 후보 하한 미만 | 값 보존, `below_guard` 플래그 |
| 후보 상한 초과 | 값 보존, `above_guard` 플래그 |
| 정수형 변수의 소수 | 결측, `non_integer` |
| 허용되지 않은 범주 | 결측, `unexpected_code` |

행 삭제, 경계값 clipping, 타깃 결측 대치는 금지한다.

### candidate guard v0.1

| canonical 값 | 범위 |
|---|---:|
| `age_years` | 0~80, 정수; P0는 19 이상 |
| `height_cm` | 100~220cm |
| `weight_kg` | 25~250kg |
| `waist_cm` | 40~200cm |
| `fasting_hours` | 0~48시간 |
| `fasting_glucose_mg_dl` | 30~600mg/dL |
| `hba1c_raw_pct` | 3~20% |
| `sbp_raw_mmhg` | 60~260mmHg |
| `dbp_raw_mmhg` | 30~160mmHg |
| `examination_weight` | 0 초과 |

이 범위는 기존 값 감사의 후보 guard다. 따라서 초과값을 자동 결측·제외하지 않고 원수치를 보존한 채 품질 플래그만 남긴다. 전체 조사에서 신장·체중 하한 밖 값은 주로 소아 때문에 관찰됐으므로 P0 일반 성인 안에서의 위반 건수를 집계한 후, 임상·데이터 공동 검토를 거쳐 별도의 hard validity 규칙으로 승격할지를 결정한다.

2020년 첫 canonical 확인 실행에서는 공복시간 48시간 초과의 보호 억제 소수 사례가 자동 결측 처리되어 기존 측정 조화 cohort와 불일치했다. 이는 후보 guard의 역할을 넘어선 처리였으므로 r2에서 값 보존+플래그로 수정한다.

범위 밖 값은 행을 삭제하지 않고 해당 변수만 결측 처리한다. 따라서 한 태스크에서 측정 실패한 행이 다른 태스크에 사용 가능한 경우는 보존된다.

## 5. 운동 규칙

- 공식 `pa_aerobic`: 일·이동·여가 전체 활동 comparator/F1 후보
- 앱 정렬 피처: 여가 중강도 분/주 + 2×여가 고강도 분/주
- 참여 `2=아니오`인 분기만 명시적 0분
- `8/9`, `88/99`, 잘못된 skip 조합은 결측
- `BE5_1`: 0~5일 이상으로 정규화, `8/9`는 결측
- 활동 결측 시 0으로 채우지 않고 F0 fallback 사용

## 6. 원측정값과 민감도 값

### HbA1c

- 주 분석: `hba1c_raw_pct`
- 2022 민감도: `1.034 × raw - 0.0448`
- 두 값을 별도 컬럼과 버전으로 저장

### 혈압

- 주 분석: 공식 `sbp_raw_mmhg`, `dbp_raw_mmhg`
- 2021년 이후: Microlife→Greenlight 민감도 값을 별도 생성
- 전환값을 주 라벨이나 원자료 대체값으로 사용하지 않음

## 7. 타깃 생성

### 허리둘레

```text
waist_target_eligible =
    p0_general_adult_eligible AND valid waist_cm
```

### 당뇨 측정 이상

```text
diabetes_target_eligible =
    p0_general_adult_eligible
    AND fasting_hours >= 8
    AND glucose available
    AND HbA1c available

diabetes_measurement_label_raw =
    glucose >= 126 OR raw HbA1c >= 6.5
```

두 측정값이 모두 있어야 음성 라벨을 생성한다. 타깃은 대치하지 않는다.

### 혈압 측정 이상

```text
hypertension_target_eligible =
    p0_general_adult_eligible
    AND raw SBP available
    AND raw DBP available

hypertension_measurement_label_raw =
    raw SBP >= 140 OR raw DBP >= 90
```

## 8. 피처 완전성

| 피처셋 | 완전성 조건 |
|---|---|
| F0 | P0 적격 + 나이·성별·키·몸무게 유효 |
| F1 | F0 + 공식 `pa_aerobic` + 근력 일수 |
| F2-leisure | F0 + 여가 유산소 환산 분/주 + 근력 일수 |

F2-leisure가 불완전하다고 운동값을 0으로 대치하지 않는다. 선택형 온보딩과 정렬되는 fallback은 F0다.

## 9. 누설 방지

모델 입력은 명시적 allowlist만 허용한다.

- 나이
- 성별
- 키
- 몸무게
- 여가 유산소 중강도환산분/주
- 근력운동 일수
- F1 비교에 한해 공식 `pa_aerobic`

공복혈당, HbA1c, 혈압, 허리둘레 타깃, 공식 질환 comparator, 조사 ID·연도·가중치·층화·PSU는 모델 입력으로 금지한다. 코드의 `assert_model_input_columns()`가 금지 및 미승인 컬럼을 차단한다.

## 10. 구현물

- 계약: `contracts/canonical_etl_contract_v0_1.yaml`
- 행 단위 변환: `src/canonical_etl.py`
- 집계 확인: `src/audit_canonical_etl.py`
- 합성 테스트: `tests/test_canonical_etl.py`
- 감사 테스트: `tests/test_audit_canonical_etl.py`

## 11. 로컬 집계 확인 명령

```powershell
python -m src.audit_canonical_etl `
  --contract 'contracts/canonical_etl_contract_v0_1.yaml' `
  --year-file '2019=C:\Users\FORYOUCOM\PycharmProjects\AH_05_01\data\01_raw\knhanes\year=2019\hn19_all.csv' `
  --year-file '2020=C:\Users\FORYOUCOM\PycharmProjects\AH_05_01\data\01_raw\knhanes\year=2020\hn20_all.csv' `
  --year-file '2021=C:\Users\FORYOUCOM\PycharmProjects\AH_05_01\data\01_raw\knhanes\year=2021\hn21_all.csv' `
  --year-file '2022=C:\Users\FORYOUCOM\PycharmProjects\AH_05_01\data\01_raw\knhanes\year=2022\hn22_all.csv' `
  --output-json 'reports\canonical_etl\canonical_etl_confirmation_2019_2022_v0_1_r2.json' `
  --output-md 'reports\canonical_etl\canonical_etl_confirmation_2019_2022_v0_1_r2.md'
```

이 명령은 canonical 행이나 ID를 저장하지 않는다. 입력/출력 행 수, ID 품질, 성인 코호트, 피처 완전성, 타깃 수, 범위·결측 사유만 집계한다.

## 12. 실행 후 공동 확인

1. 입력과 canonical 출력의 행 수가 같은가?
2. ID 결측·중복이 0건인가?
3. 성별·임신코드 모순이 없는가?
4. P0 일반 성인의 신장·체중·측정값 guard 위반이 없는가?
5. 허리둘레·당뇨·고혈압 코호트 수가 기존 감사와 일치하는가?
6. F0/F1/F2-leisure 완전성 차이가 합리적인가?
7. eligibility 밖에 라벨이 생성된 행이 0건인가?
8. 모델 입력 allowlist와 금지목록이 겹치지 않는가?

이 확인과 병학님 공동 승인이 끝나기 전에는 실제 canonical 데이터 파일, train/test 파일을 생성하지 않는다.
