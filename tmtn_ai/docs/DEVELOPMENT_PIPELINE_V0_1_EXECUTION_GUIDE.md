# TMTN 2019~2021 개발 파이프라인 v0.1 실행 가이드

## 현재 상태

- 모델 실험 프로토콜 v0.2: 박강호·병학 공동 승인 완료
- 승인 config: `configs/model_development_v0_1.yaml`
- 이번 구현 범위: task frame 및 nested fold registry 생성
- 실제 모델 학습·성능평가: 이 검토 대화에서는 수행하지 않음
- 2022: 전체 동결 전 접근 금지
- 2023: 홍주 custodian 전용
- 2024: 접근 금지

## 1. 승인 config 무결성 확인

프로젝트 루트와 활성화된 `.venv`에서 실행한다.

```powershell
python -c "from pathlib import Path; from src.experiment_setup import load_authorized_config; load_authorized_config(Path('configs/model_development_v0_2.yaml')); print('AUTHORIZED CONFIG PASS')"
```

`PASS`가 아니면 실행을 중단한다. 승인된 protocol, canonical ETL, 환경 파일 또는 구현 코드가 변경된 상태이므로 변경 내용을 공동 검토하고 새 config 버전과 해시를 만들어야 한다.

## 2. 2019~2021 canonical 통합 파일 생성

입력은 확정된 canonical ETL로 생성된 2019~2021 자료만 포함해야 한다. 2022·2023·2024 행이 한 개라도 들어 있으면 task filtering 전에 전체 실행이 중단된다.

현재 확인된 로컬 원자료 경로를 사용한 실행 명령은 다음과 같다.

```powershell
python -m src.materialize_development_canonical `
  --config 'configs/model_development_v0_2.yaml' `
  --year-file '2019=C:\Users\FORYOUCOM\PycharmProjects\AH_05_01\data\01_raw\knhanes\year=2019\hn19_all.csv' `
  --year-file '2020=C:\Users\FORYOUCOM\PycharmProjects\AH_05_01\data\01_raw\knhanes\year=2020\hn20_all.csv' `
  --year-file '2021=C:\Users\FORYOUCOM\PycharmProjects\AH_05_01\data\01_raw\knhanes\year=2021\hn21_all.csv' `
  --output-root 'C:\Users\FORYOUCOM\PycharmProjects\AH_05_01\data\04_harmonized\tmtn_canonical'
```

이 명령은 세 원자료의 SHA-256을 기존 canonical 집계 감사 영수증과 먼저 대조한다. 일치한 경우에만 `canonical_development_v0_1/canonical_2019_2021.csv`와 manifest를 새로 만든다. 2022 이후 파일은 인자로 받지 않는다.

canonical 파일은 다음을 만족해야 한다.

- `(source_year, participant_id)`가 결측·중복 없음
- 고정 6개 피처 보유
- task별 eligibility와 label 보유
- `examination_weight`, `strata`, `psu` 보유
- 기존 canonical 또는 원자료를 덮어쓰지 않음

## 3. task frame 생성

아래 경로는 로컬 실제 경로에 맞게 바꾼다.

```powershell
python -m src.prepare_development_frames `
  --config 'configs/model_development_v0_2.yaml' `
  --canonical 'C:\Users\FORYOUCOM\PycharmProjects\AH_05_01\data\04_harmonized\tmtn_canonical\canonical_development_v0_1\canonical_2019_2021.csv' `
  --output-root 'C:\Users\FORYOUCOM\PycharmProjects\AH_05_01\data\05_cohorts\tmtn_development_frames'
```

생성물은 허리둘레·당뇨·고혈압 task별 파일과 `manifest.json`이다. eligibility, label 결측 제거, 고정 6개 피처 complete-case만 적용하며 0·평균 대치, clipping, scaling, 학습은 수행하지 않는다.

## 4. nested fold registry 생성

task별로 실행한다.

```powershell
python -m src.build_nested_fold_registry `
  --config 'configs/model_development_v0_2.yaml' `
  --task waist `
  --task-frame 'C:\Users\FORYOUCOM\PycharmProjects\AH_05_01\data\05_cohorts\tmtn_development_frames\development_v0_1\waist\development_2019_2021.csv' `
  --output-root 'C:\Users\FORYOUCOM\PycharmProjects\AH_05_01\data\06_splits\tmtn_nested_folds'
```

`--task`는 `waist`, `diabetes`, `hypertension`를 각각 사용한다. seed 42·1042·2042별 outer 5-fold와 각 outer-train 내부의 inner 4-fold registry가 만들어진다. outer holdout 키는 해당 outer fold의 inner registry에 포함되지 않는다.

## 5. 실행 후 확인

- 세 task 모두 manifest와 SHA-256 존재
- 입력 원본 SHA-256 불변
- task frame에 2019~2021 이외 연도 없음
- task frame의 6개 피처 결측 없음
- outer registry의 key가 task frame key를 정확히 한 번 포함
- 각 outer holdout과 해당 inner registry key의 교집합이 0
- 동일 config·입력으로 재생성 시 registry 내용이 동일

기존 output version이 존재하면 덮어쓰지 않고 실패한다. 계약·코드·입력 변경이 필요하면 원본을 수정하지 말고 새 버전을 만든다.

## 다음 개발 단계

이 registry를 기준으로 모델 파이프라인 코드를 작성한다. scaler와 모든 학습 전처리는 각 inner/outer 학습 fold 안에서만 적합하고, 허리둘레 OOF 예측도 자기 행을 학습하지 않은 모델에서만 생성한다. 2019~2021 OOF로 모델·hyperparameter·calibration을 동결한 후에만 2022 일회 평가 절차로 넘어간다.
