# TMTN canonical ETL and secure dataset splitter

> **중요 — 실제 KNHANES 분할 실행 보류**  
> 분할 코드는 2019~2021 개발, 2022 temporal validation, 2023 locked internal test, 2024 금지 역할로 개정됐다. 다만 배포 설정에는 `do_not_split: true`가 걸려 있다. 공동 승인, 2023 정답 보관 담당자 지정, 접근권한 확인 뒤 새 버전 설정에서만 해제한다. `do_not_train: true`도 유지한다.

KNHANES canonical task table을 **학습하거나 성능평가하지 않고** random 개발 분할, temporal validation, 잠금 내부 테스트로 나누는 Python 3.11+ 도구입니다. 테스트는 합성 데이터만 사용합니다.

## 보안 경계

- 원본 CSV는 읽기 전·후 SHA-256을 비교하며 수정하거나 덮어쓰지 않습니다.
- 2023은 YAML의 `locked_test_years`로 개발·시간검증과 격리합니다.
- 개발자가 받는 2023 CSV에는 정답 라벨이 없으며 ID·연도·6개 입력 피처만 포함합니다.
- 2023 정답은 필수 `--locked-label-output` 경로에 별도 저장합니다. 이 경로는 `--output`과 같거나 서로 포함 관계일 수 없습니다.
- 2023 라벨 분포·평균·통계·예시 행은 manifest와 콘솔에 기록하지 않습니다.
- 입력에 2024가 있으면 자동 필터링하지 않고 실패합니다.
- 이메일·전화번호·이름·주소 등 직접 식별자로 보이는 피처명은 차단합니다.
- 결측 대치, 스케일링, 인코딩, 피처 선택은 수행하지 않습니다. 이후 전처리의 fit은 train에만 수행해야 합니다.
- 완성 전에는 같은 파일시스템의 임시 폴더에 기록하고, 모든 검증이 끝난 뒤 디렉터리를 이동합니다.
- 기존 `{task}/{dataset_version}` 폴더는 기본적으로 거부하고, 명시적 `--force`에서만 롤백 가능한 교체 절차를 사용합니다.

세 설정은 모두 2019~2021을 개발, 2022를 시간검증, 2023을 잠금 내부 테스트로 사용하고 2024를 금지합니다. 기본 6개 피처는 `age_years`, `sex_code`, `height_cm`, `weight_kg`, `leisure_aerobic_moderate_equivalent_min_week`, `strength_days_week`입니다. 원자료가 아니라 canonical task table만 입력해야 합니다.

당뇨·고혈압 모델에 허리둘레 서브모델 출력을 추가할 때는 실측 `waist_cm`을 그대로 넣지 않습니다. 개발 split을 먼저 고정한 뒤 train 내부 OOF(out-of-fold) 예측으로 생성한 피처와 서비스 추정값을 같은 계약으로 연결해야 학습-서비스 불일치를 피할 수 있습니다.

## 구조

```text
tmtn_ai/
├─ README.md
├─ requirements.txt
├─ configs/
│  ├─ diabetes_split.yaml
│  ├─ hypertension_split.yaml
│  └─ waist_split.yaml
├─ src/
│  ├─ __init__.py
│  ├─ split_dataset.py
│  ├─ split_validation.py
│  └─ file_integrity.py
├─ tests/
│  └─ test_split_dataset.py
└─ data/
   ├─ raw/
   │  └─ .gitkeep
   └─ splits/
      └─ .gitkeep
```

한 번 실행하면 다음 구조가 만들어집니다.

```text
<output>/<task>/<dataset_version>/
├─ development/
│  ├─ random/
│  │  ├─ train.csv
│  │  └─ validation.csv
│  └─ temporal/
│     ├─ train.csv
│     └─ validation.csv
├─ locked_internal_test/
│  └─ test_2023_features.csv  # 정답 라벨 없음
└─ manifests/
   ├─ split_manifest.json
   ├─ integrity_manifest.json
   └─ config_snapshot.yaml
```

별도의 접근 제한 경로에는 다음 파일이 만들어집니다.

```text
<locked-label-output>/<task>/<dataset_version>/
├─ test_2023_labels.csv          # ID, 연도, 정답만 포함
└─ sealed_manifest.json
```

## 현재 단계: 값 감사·운동 전처리 원자료 확인 완료

원시 스키마 감사와 2019~2023 값 수준 감사가 완료됐습니다. 현재 기준 문서는 다음과 같습니다.

- `docs/VARIABLE_AUDIT_2019_2024_v0_2.md`: 연도별 변수·문항·코드 감사표
- `docs/VALUE_AUDIT_2019_2023_FINAL_V0_2.md`: 값 감사 최종 판정
- `contracts/value_audit_spec_v0_2.yaml`: 확인 감사 명세
- `contracts/data_contract_v0_3.yaml`: `do_not_train: true` 상태의 데이터 계약
- `contracts/activity_feature_contract_v0_1.yaml`: 운동 피처 전처리 계약
- `contracts/locked_2023_materialization_contract_v0_1.yaml`: 홍주 custodian 검토용 2023 잠금 생성 계약
- `docs/ACTIVITY_FEATURE_PREPROCESSING_V0_1.md`: 공식 재현·앱 정렬 운동 피처 설계와 실행 절차

운동 피처 파생 코드와 집계 전용 확인 감사는 합성 테스트와 2019~2021 원자료 확인을
통과했다. 공식 `pa_aerobic` 재현 불일치는 전 연도 0건이며 예상 밖 원시코드도 0건이다.
최종 판정은 `docs/ACTIVITY_FEATURE_AUDIT_2019_2021_FINAL_V0_2.md`에 기록했다.

HbA1c 2022년 분석기관 변경과 2021년 성인 혈압계 변경의 영향 감사에서는 주 분석을
공식 원측정값, 연구자 전환값을 민감도 분석 전용으로 사전 등록했다. 설계와 실행 절차는
`docs/MEASUREMENT_HARMONIZATION_HBA1C_BP_V0_1.md`에 기록했다.

2019~2022 집계 감사의 기술 검사는 통과했다. 최종 판정과 공동 검토 항목은
`docs/MEASUREMENT_HARMONIZATION_2019_2022_FINAL_V0_1.md`에 기록했으며, 병학님과 정책을
공동 승인하기 전까지 `do_not_train: true`를 유지한다.

임신 제외·운동·HbA1c·혈압 규칙을 통합한 canonical ETL v0.1-r2는 2019~2022 집계
기술 확인을 통과했다. 최종 검토는 `docs/CANONICAL_ETL_2019_2022_FINAL_V0_1.md`에
기록했다. 분할 코드는 새 연도 역할로 개정됐지만 공동 승인 전에는 canonical 행이나
분할 파일을 생성하지 않는다.

입력·타깃·custodian 공동 결정은 `docs/JOINT_DECISIONS_INPUT_TARGET_LOCKED_TEST_V0_1.md`,
홍주님 실행 인계 절차는 `docs/HONGJU_LOCKED_2023_MATERIALIZATION_HANDOFF_V0_1.md`를 따른다.

`src.audit_raw_schema`는 CSV/SAS/SPSS 파일에서 컬럼 메타데이터만 읽습니다. 참가자 행·값·표본 예시·행 수·결측률·분포·기술통계를 출력하지 않습니다. 2024에도 같은 제한이 적용됩니다.

전체 연도 스키마를 감사하는 실행 예시는 다음과 같습니다.

```powershell
python -m src.audit_raw_schema `
  --spec contracts/variable_audit_spec_v0_1.yaml `
  --year-file '2019=D:\data\HN19_ALL.sas7bdat' `
  --year-file '2020=D:\data\HN20_ALL.sas7bdat' `
  --year-file '2021=D:\data\HN21_ALL.sas7bdat' `
  --year-file '2022=D:\data\HN22_ALL.sas7bdat' `
  --year-file '2023=D:\data\HN23_ALL.sas7bdat' `
  --year-file '2024=E:\restricted\HN24_ALL.sas7bdat' `
  --output 'D:\audit\knhanes_schema_audit_v0_1.json'
```

아직 일부 연도 파일만 준비됐다면 `--allow-partial`을 명시해 점진적으로 확인할 수 있습니다. 이 감사 결과와 공식 이용지침서를 대조해 데이터 계약을 공동 승인하기 전에는 분할 CLI와 모델 학습을 실행하지 않습니다.

## PyCharm 설정과 검증 순서

1. PyCharm에서 **Open**을 눌러 이 `tmtn_ai` 폴더를 엽니다.
2. **Settings → Project → Python Interpreter → Add Interpreter → Add Local Interpreter → Virtualenv**를 선택합니다.
3. Base interpreter는 Python 3.11 이상, 가상환경 위치는 프로젝트의 `.venv`로 설정합니다.
4. PyCharm Terminal에서 다음을 실행합니다.

   ```powershell
   python -m pip install --upgrade pip
   python -m pip install -r requirements.txt
   python -m pytest -q
   ```

5. 테스트가 모두 통과해도 현재 배포 YAML의 `do_not_split: true`는 임의로 수정하지 않습니다. 실제 데이터나 잠금 테스트의 내용·통계를 이 대화나 외부 서비스에 공유하지 않습니다.
6. 공동 승인 후 새 버전의 승인된 YAML을 만든 경우에만 프로젝트 루트에서 다음 형태로 실행합니다.

   ```powershell
   python -m src.split_dataset `
     --config configs/diabetes_split.yaml `
     --input "D:\data\diabetes_canonical_task_table.csv" `
     --output "D:\data\tmtn_splits" `
     --locked-label-output "E:\restricted\tmtn_locked_test_labels"
   ```

   `--locked-label-output`은 개발자가 사용하는 `--output`과 다른 드라이브 또는 접근 제어된 상위 폴더를 권장합니다. 분할 실행자는 정답 경로를 평가 담당자에게 인계한 뒤 모델 개발자에게 읽기 권한을 부여하지 않습니다.

7. 같은 task/version 출력이 이미 있으면 실행이 중단됩니다. 의도한 교체임을 별도로 확인한 경우에만 끝에 `--force`를 붙입니다. 새 실험은 `dataset_version`을 올리는 편이 안전합니다.

## 설정 의미

- `task_type`: `classification` 또는 `regression`
- `development_years`: random 8:2 모집단이자 temporal train 역할
- `temporal_validation_years`: 개발 연도와 겹치지 않는 시간검증 연도
- `locked_test_years`: 모델·보정 동결 뒤 한 번 평가하는 잠금 내부 테스트 연도
- `forbidden_years`: 입력에 존재하면 실패하는 연도
- `do_not_split`: `true`이면 입력파일을 읽기 전에 실행 중단
- `classification_stratify`: 분류 random split의 0/1 라벨 층화 여부
- `regression_stratify`: `none` 또는 `quantile`
- `regression_stratify_bins`: quantile 근사 층화 bin 수
- `csv_encoding`: `utf-8` 또는 `utf-8-sig`; Windows Excel 호환이 필요하면 `utf-8-sig`
- `output_format`: 현재 안전하게 검증된 값은 `csv`만 지원

입력에 YAML의 허용 역할 밖 연도가 있거나, 설정한 연도 중 행이 전혀 없는 경우에는 추정하여 버리지 않고 실패합니다. 특히 2024는 자동 필터링하지 않습니다. 먼저 승인된 절차로 task별 labeled canonical cohort를 준비해야 합니다.

## Manifest 주의사항

`split_manifest.json`은 각 CSV의 행 수와 SHA-256만 기록합니다. `integrity_manifest.json`은 입력·설정·코드와 산출물 해시를 기록합니다. 무결성 manifest 자신은 자기 자신의 해시를 포함할 수 없으므로, 그 파일을 제외한 모든 생성 artifact를 기록합니다. 어느 manifest에도 잠금 테스트의 라벨별 개수나 통계는 포함되지 않습니다.
