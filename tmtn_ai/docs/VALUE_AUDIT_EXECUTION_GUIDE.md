# KNHANES 2019~2023 값 수준 감사 실행 가이드

> 기준 사양: `contracts/value_audit_spec_v0_1.yaml`  
> 실행 코드: `src/audit_raw_values.py`  
> 금지 연도: 2024  
> 주의: 이 명령은 값 감사만 수행하며 분할·전처리·학습·성능평가를 수행하지 않는다.

## 1. 실행 전 확인

프로젝트 루트에서 가상환경을 활성화하고 테스트를 실행한다.

```powershell
python -m pip install -r requirements.txt
python -m pytest tests/test_audit_raw_values.py -q
```

다음 파일을 먼저 검토한다.

- `contracts/data_contract_v0_3.yaml`
- `contracts/value_audit_spec_v0_1.yaml`
- `docs/VALUE_AUDIT_PLAN_2019_2023.md`

## 2. 2019년 부분 감사

전체 5개년을 실행하기 전에 2019년 한 개 파일로 입출력 형식과 컬럼을 확인한다.

```powershell
python -m src.audit_raw_values `
  --spec 'contracts/value_audit_spec_v0_1.yaml' `
  --year-file '2019=C:\실제경로\HN19_ALL.sas7bdat' `
  --output-json 'C:\감사결과\value_audit_2019_partial_v0_1.json' `
  --output-md 'C:\감사결과\value_audit_2019_partial_v0_1.md' `
  --allow-partial
```

CSV를 사용할 경우 확장자와 실제 경로만 바꾼다.

```powershell
--year-file '2019=C:\실제경로\hn19_all.csv'
```

부분 감사가 성공해도 데이터 계약이 승인되거나 학습이 허용되는 것은 아니다.

## 3. 2019~2023 통합 감사

2019년 부분 결과를 확인한 뒤 새로운 출력 파일명으로 전체 감사를 실행한다.

```powershell
python -m src.audit_raw_values `
  --spec 'contracts/value_audit_spec_v0_1.yaml' `
  --year-file '2019=C:\실제경로\HN19_ALL.sas7bdat' `
  --year-file '2020=C:\실제경로\HN20_ALL.sas7bdat' `
  --year-file '2021=C:\실제경로\HN21_ALL.sas7bdat' `
  --year-file '2022=C:\실제경로\HN22_ALL.sas7bdat' `
  --year-file '2023=C:\실제경로\HN23_ALL.sas7bdat' `
  --output-json 'C:\감사결과\value_audit_2019_2023_v0_1.json' `
  --output-md 'C:\감사결과\value_audit_2019_2023_v0_1.md'
```

`2024=...`를 전달하면 코드는 값을 읽기 전에 실패해야 한다.

## 4. 출력 파일

### JSON

자동 검토와 후속 계약 생성에 사용한다.

- 입력 파일명·SHA-256
- 실행 사양 SHA-256
- 연도별 행 수
- 변수별 결측·코드·후보 범위 위반
- 소규모 셀 억제 결과
- 공식 BMI·혈압 평균 재현 검사
- 임신 코드 교차표
- 운동 문항 missingness pattern
- 임신 매핑 전 당뇨·고혈압·허리둘레 후보 cohort 집계

### Markdown

사람이 우선 검토할 요약 보고서다. UTF-8 BOM을 포함해 노션 복사 시 한글 인코딩이 유지되도록 생성한다.

## 5. 개인정보·오염 방지

- 원시 행과 ID는 출력하지 않는다.
- 범주별 셀이 5명 미만이면 count를 억제한다.
- 차감으로 작은 셀을 복원하지 못하도록 보완 셀도 함께 억제한다.
- 범위 밖 값의 실제 원시값이나 행 예시는 출력하지 않는다.
- 2024는 값 감사 허용 연도에 포함하지 않는다.
- 모델 fit, imputation fit, 분할, feature selection, 성능 계산은 수행하지 않는다.

## 6. 첫 결과 검토 순서

1. 필수 컬럼 누락 여부
2. ID 결측·중복 여부
3. 범주형 변수의 관측 코드와 코드북 밖 값
4. 후보 특수코드의 실제 사용 여부
5. `HE_prg`·`HE_dprg` 교차표와 임신 매핑
6. 여가 중·고강도 문항의 missingness pattern
7. BMI 재계산 및 혈압 2·3차 평균 일치도
8. 공복·혈당·HbA1c 동시 가용 cohort
9. 2022년 전후 HbA1c 및 혈압 분포 이동
10. 가중치·층화·PSU 유효성

## 7. v0.1 결과 후 작업

v0.1은 discovery 감사다. 관측 코드와 문항 구조를 확인한 뒤 다음을 수행한다.

1. `HE_prg`·`HE_dprg`의 확정 코드 매핑
2. 운동 참여 여부·일수·시간·분의 확정 특수코드 매핑
3. 공식 `pa_aerobic` 재현 규칙 추가
4. 임신 제외가 적용된 최종 후보 cohort 산출
5. 변수별 유효범위 및 실패 조건 확정
6. `value_audit_spec_v0_2` 작성 및 확인 감사
7. 결과를 반영한 `data_contract_v1.0` 공동 승인

`data_contract_v1.0` 승인 전에는 실제 train CSV를 생성하지 않는다.

## 8. 2019~2023 v0.2 확인 감사

각 연도의 v0.1 탐색 감사와 공식 코드북 대조가 끝난 뒤 아래 명령을 한 번 실행한다. 기존 v0.1 출력은 덮어쓰지 않는다.

```powershell
python -m src.audit_raw_values `
  --spec 'contracts/value_audit_spec_v0_2.yaml' `
  --year-file '2019=C:\Users\FORYOUCOM\PycharmProjects\AH_05_01\data\01_raw\knhanes\year=2019\hn19_all.csv' `
  --year-file '2020=C:\Users\FORYOUCOM\PycharmProjects\AH_05_01\data\01_raw\knhanes\year=2020\hn20_all.csv' `
  --year-file '2021=C:\Users\FORYOUCOM\PycharmProjects\AH_05_01\data\01_raw\knhanes\year=2021\hn21_all.csv' `
  --year-file '2022=C:\Users\FORYOUCOM\PycharmProjects\AH_05_01\data\01_raw\knhanes\year=2022\hn22_all.csv' `
  --year-file '2023=C:\Users\FORYOUCOM\PycharmProjects\AH_05_01\data\01_raw\knhanes\year=2023\hn23_all.csv' `
  --output-json 'reports\value_audit\value_audit_2019_2023_v0_2_confirmation_r2.json' `
  --output-md 'reports\value_audit\value_audit_2019_2023_v0_2_confirmation_r2.md'
```

확인 감사의 통과 조건은 다음과 같다.

- 모든 연도의 예상 외 범주 코드가 0건이다.
- 임신 제외가 적용된 성인 코호트 집계가 생성된다.
- 유산소 참여자의 일수·시간·분 완전성 집계가 생성된다.
- BMI와 최종 혈압 공식 재현율이 계속 100%다.
- 입력 파일 SHA-256이 v0.1 통합 보고서와 동일하다.
- 보고서 정책에 `model_training_performed=false`가 기록된다.
- 보고서 정책에 `binary_partition_complementary_suppression=true`가 기록된다.
- `audit_implementation`에 감사 코드 버전과 SHA-256이 기록된다.

초기 `value_audit_2019_2023_v0_2_confirmation.*` 출력은 이진 분할의 작은 반대 셀을 차감으로 복원할 수 있어 최종 근거로 사용하지 않는다. 삭제할 필요는 없지만 반드시 `r2` 결과로 대체한다.
