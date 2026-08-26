# 병학님 D0·D1 handoff 검토 결과

작성일: 2026-08-25  
검토 대상: `TMTN_D0_D1_handoff_2026-08-25.zip`  
검토 상태: `REVIEWED_EXPERIMENTAL_NOT_APPROVED_FOR_OFFICIAL_PIPELINE`

## 1. 결론

이 패키지는 실험 기록과 코드 검토 자료로는 보존 가치가 있다. 허리둘레 OOF 생성, 실측 허리둘레의 질환 모델 직접 입력 차단, 개발 구간 전처리 적합, matched-D0 비교 등 재사용할 설계도 확인됐다.

그러나 현재 확정한 팀 계약에 그대로 편입할 수는 없다. 특히 패키지 안에 2023 라벨을 사용한 성능·calibration·행 수 결과가 이미 생성돼 있으므로, 현재 D0·D1 연구에서 2023은 더 이상 미개봉 `locked internal test`가 아니다. 기존 결과는 `opened internal benchmark` 또는 `historical experimental result`로만 보존해야 한다.

공식 다음 버전은 2019~2021 안에서 모델 선택과 calibration을 끝내고, 2022는 손대지 않은 temporal validation으로 남기도록 다시 작성해야 한다. 2023 평가는 홍주님 분리 실행 절차를 유지하되, 이미 본 2023 결과 때문에 최종 독립 공개 성능으로 주장하지 않는다. 최종 독립 평가는 향후 KNHANES 연도 또는 외부 코호트가 필요하다.

## 2. 패키지 무결성·안전 검사

| 항목 | 결과 | 비고 |
|---|---|---|
| ZIP SHA-256 | PASS | `5ac2d3eeb4fc25ae8501360e30258594b115e95b790b6588687900407c362e16` |
| 내부 체크섬 | PASS | `SHA256SUMS.txt` 기준 42/42 일치 |
| ZIP 경로 안전성 | PASS | 절대경로·상위경로 이동·심볼릭 링크·대소문자 중복 없음 |
| 암호화·비정상 압축률 | PASS | 암호화 항목 없음, 최대 압축률 약 7.33배 |
| Python 문법 검사 | PASS | 포함된 Python 파일 전체 compile 성공 |
| 원자료·행 단위 예측·모델 바이너리·자격증명 | 미발견 | 포함된 CSV는 헤더와 행 수 기준 집계 결과 |
| 전달된 테스트 결과 독립 재현 | 미실시 | 원자료·행 단위 산출물이 없고 테스트가 synthetic pytest가 아니라 기존 로컬 산출물을 읽는 실행형 검사이므로 이 검토 환경에서는 실행하지 않음 |

주의: 집계 CSV에는 2023 표본 수·양성 수·성능·calibration 정보가 포함돼 있다. 개인별 행은 아니지만 locked test 결과를 미개봉 상태로 유지하는 관점에서는 공개된 평가 정보다.

## 3. 계약과 일치하는 부분

- D0·D1 실험 본체의 대상 연도는 2019~2023으로 제한돼 있고, 해당 실행 경로에서 2024를 직접 읽는 코드는 확인되지 않았다.
- 연속형 스케일러는 개발 구간에만 적합한다.
- 질환 모델 피처 결측은 0 또는 평균으로 대치하지 않고 complete-case로 처리한다.
- D1 개발 자료의 허리둘레 예측은 2019~2021 내부 5-fold OOF 방식으로 생성한다.
- 각 fold에서 허리둘레가 관측된 학습 행만 추정 모델 학습에 쓰고, holdout 전체에 예측하는 구조다.
- 질환 모델 프레임에는 실측 `HE_wc` 또는 `waist_cm_measured`를 직접 피처로 넣지 않고 `estimated_waist_cm`만 사용한다.
- 허리둘레가 결측인 사용자에게도 입력 피처가 완전하면 추정값을 생성하도록 v0.2에서 범위를 수정했다.
- D1과 동일한 대상자 집합으로 matched-D0를 다시 학습해 허리둘레 추정값의 추가 효과를 비교한다.
- 측정값 기반 당뇨·고혈압 primary 라벨 구현은 현재 canonical 계약의 방향과 일치한다. 공식 분류값은 comparator로 분리한다.
- 기존 산출물을 덮어쓰지 않고 버전이 붙은 새 파일을 생성한다.

## 4. 공식 편입 전 필수 수정 사항

### P0-1. 2023 locked test 상태 충돌

패키지 코드는 2023 canonical과 라벨을 같은 개발 환경에서 만들고, D0·D1의 2023 성능·calibration·행 수를 출력한다. `locked_test`라는 이름과 달리 모델 개발자가 결과를 볼 수 있는 구조다.

조치:

- 기존 2023 결과는 `opened_internal_benchmark` 또는 `historical_experimental_result`로 재분류한다.
- 개발자 실행 코드는 2019~2022까지만 읽도록 강제한다.
- 2023은 `src/materialize_locked_2023.py` 기반으로 홍주님 환경에서 피처와 라벨을 분리한다.
- 모델·피처·전처리·calibration 버전을 동결한 뒤 홍주님이 1회 평가한다.
- 이 절차로 새 평가를 하더라도 연구자가 이미 과거 2023 결과를 본 사실 때문에 2023을 완전한 독립 최종 성능으로 주장하지 않는다.
- 공개 게이트용 최종 독립 평가는 미래 KNHANES 연도 또는 외부 코호트로 별도 확보한다.

### P0-2. 2022 역할 충돌

D0는 2022에서 Platt 보정기를 적합한다. D1은 2022에서 허리둘레 모델 종류를 선택하고 질환 모델 Platt 보정기를 적합한다. 따라서 현재 2022 결과는 순수 temporal validation 결과가 아니다.

조치:

- 2019~2021 내부 OOF 또는 nested CV 결과로 허리둘레 모델·질환 모델·피처 세트를 선택한다.
- calibration도 2019~2021 cross-fitted OOF 예측으로 적합한다.
- 2022는 모델 선택, 피처 선택, 임계값 선택, calibration 적합에 사용하지 않고 평가만 한다.
- 기존 실험을 보존할 때는 2022를 `model_selection_and_calibration_fit_2022`로 명시한다.

### P0-3. record key 계약 불일치

handoff ETL은 `source_survey_year + source_row_number`로 `record_key`를 만든다. `source_row_number`는 파일 정렬이나 재내보내기에 따라 바뀔 수 있어 안정적인 참여자 키가 아니다. 연도가 키에 들어가므로 2022와 2023의 교집합 0 검사는 사실상 자동으로 통과하며, 참여자 중복 또는 키 재현성을 충분히 증명하지 못한다.

조치:

- 공식 키는 확정 계약대로 `source_year + participant_id(ID)`를 사용한다.
- 원자료 snapshot hash와 키 유일성·결측·재현성 검사를 함께 기록한다.
- fold disjoint 검사는 이 composite key를 기준으로 수행한다.

### P0-4. 공식 canonical ETL과 실행 경계 불일치

handoff의 canonical ETL은 2019~2023 전체 canonical 및 라벨 포함 모델 프레임을 한 개발 경로에 저장한다. 현재 팀 계약은 2019~2022 frozen canonical과 홍주님 전용 2023 materialization을 분리한다.

조치:

- handoff ETL을 공식 ETL로 병합하지 않는다.
- 공식 모델 실험은 현재 `src/canonical_etl.py` 산출 계약을 입력으로 받도록 adapter를 작성한다.
- 2023 접근은 별도 custodian entry point 외에는 코드 수준에서 차단한다.

## 5. 추가 보완 사항

### P1. 6개 피처 이름·표현 통일

개념은 대체로 일치하지만 handoff는 `age`, `sex_M`, `strength_days_category`를 사용하고 공식 계약은 `age_years`, `sex_code`, `strength_days_week`를 사용한다. 특히 근력운동은 KNHANES의 `5일 이상`을 숫자 6일로 바꾸면 안 된다.

공식 adapter에서 다음을 고정한다.

- `age` → `age_years`
- KNHANES `sex` → 계약상 `sex_code`, 모델 내부 인코딩은 별도 transformer에서 수행
- `strength_days_week`의 값 공간은 `0, 1, 2, 3, 4, 5_plus`로 보존하고 모델 내부에서 범주형 인코딩
- 앱 P0 유산소 입력은 일수 × 회당 분 × 대표 강도 계수로 `leisure_aerobic_moderate_equivalent_min_week`를 생성
- 앱 원시 입력과 KNHANES canonical 피처, 모델 텐서 컬럼을 각각 구분

### P1. 상충하는 이전 문서 제거 또는 superseded 표시

- `LABEL_CONTRACT_v0_1.md`에는 진단·복약을 primary 후보 라벨에 포함하는 과거 안이 남아 있지만 실제 코드는 측정값 primary를 구현한다.
- `VALIDATION_PLAN_v0_2.md`는 2024를 opened temporal evaluation으로 다루는 과거 계획이다.
- handoff README는 최신 계약 요약만 포함하고 위 문서는 `SUPERSEDED_DO_NOT_IMPLEMENT`로 표시하거나 공식 패키지에서 제외해야 한다.

### P1. 재현성 manifest와 환경 고정

현재 체크섬은 좋지만 다음 정보가 없어 동일 실행을 독립적으로 재현하기 어렵다.

- canonical input snapshot hash와 계약 버전
- Git commit SHA
- Python 및 라이브러리 lock 파일
- 모델·전처리·calibration 버전
- 실행 인자와 출력 스키마 버전
- random seed뿐 아니라 fold assignment artifact 또는 fold hash

### P1. 검증 게이트 미완료

현재 패키지는 비가중 점추정 중심이다. 공개 전에는 다음이 추가돼야 한다.

- 성별·연령대·연도별 discrimination 및 calibration
- 표본 수가 작은 하위집단의 불확실성 표시
- KNHANES 복합표본 가중치 적용 결과와 비가중 결과의 민감도 비교
- calibration intercept·slope, Brier, ECE의 신뢰구간
- 허리둘레 MAE·RMSE·오차분포 및 하위집단 성능
- complete-case 선택 편향과 허리둘레 결측 메커니즘 점검
- 동일 입력·동일 snapshot·동일 버전 재현성 검사

### P2. 구현 견고성

- calibration slope/intercept 진단에 기본 L2 규제가 적용된 `LogisticRegression`을 사용한다. 진단용 추정은 무규제 또는 명시된 방법으로 고정하는 편이 적절하다.
- merge 후 `waist_target_observed.astype(bool)`는 매칭 실패로 생긴 NaN을 명확히 거부하지 않는다. merge cardinality와 결측을 먼저 검사한 뒤 boolean으로 변환해야 한다.
- 핵심 중단 조건 일부가 `assert`라서 Python 최적화 옵션에서 사라질 수 있다. 명시적 예외로 바꾼다.
- 전달된 검사는 pytest test function이 아니라 기존 로컬 산출물을 읽는 실행형 검사다. synthetic fixture 기반 단위 테스트와 계약 테스트를 별도로 만든다.

## 6. 재사용 판단

| 구분 | 판단 |
|---|---|
| OOF 허리둘레 생성 아이디어 | 재사용 가능, 공식 composite key와 nested 선택 절차로 개정 필요 |
| HE_wc 직접 입력 차단 | 재사용 권장 |
| HE_wc 결측 행에도 추정값 생성 | 재사용 권장, 결측 선택 편향 검증 추가 |
| matched-D0 비교 | 재사용 권장 |
| 기본 지표 계산 코드 | 참고용 재사용, 가중치·CI·하위집단 확장 필요 |
| handoff canonical ETL | 공식 병합 금지, 현재 frozen canonical adapter로 대체 |
| 2022 선택·calibration 절차 | 공식 절차로 재사용 금지 |
| 2023 성능 결과 | 모델 선택·튜닝·공식 독립 성능 주장에 사용 금지 |
| 과거 LABEL/VALIDATION 문서 | 최신 계약으로 대체하거나 superseded 표시 |

## 7. 권장 공식 vNext 흐름

1. 현재 canonical 계약과 2019~2022 frozen ETL을 단일 입력 기준으로 사용한다.
2. 2019~2021에서 nested 또는 cross-fitted OOF 방식으로 허리둘레 모델과 질환 모델 후보를 비교한다.
3. 같은 개발 구간의 OOF 예측으로 calibration을 적합하고 모델·피처·전처리·calibration을 동결한다.
4. 2022에서 연도 이동 성능과 calibration을 한 번 평가한다. 결과를 보고 수정하면 새 버전으로 처음부터 다시 기록하고 2022가 반복 개발에 사용됐음을 명시한다.
5. 홍주님에게 2023 피처 패키지만 전달하고, 홍주님이 정답키와 결합해 동결 예측을 평가한다.
6. 2023은 현재 연구에서 이미 열린 이력이 있으므로 내부 benchmark로 해석한다.
7. 공개용 독립 최종 평가는 미래 KNHANES 연도 또는 외부 코호트로 수행한다.

## 8. 팀 결정이 필요한 항목

1. 현재 D0·D1 연구에서 2023의 명칭을 `opened_internal_benchmark`로 공식 변경할지 결정한다. 권장안은 변경이다.
2. 공개 게이트의 독립 최종 평가 자료를 미래 KNHANES 연도와 외부 코호트 중 무엇으로 확보할지 결정한다. 당장 자료가 없다면 `pending_external_or_future_holdout`으로 기록한다.
3. KNHANES 복합표본 가중 평가는 모델 학습 가중치와 평가·calibration 가중치를 분리해 설계할지 결정한다. 권장안은 우선 비가중 학습을 유지하되 가중 평가·calibration 민감도 분석을 필수로 추가하는 것이다.

## 9. 병학님께 전달할 요약

handoff 패키지의 무결성과 실험 코드 구조는 확인했습니다. OOF 허리둘레, HE_wc 직접 입력 차단, matched-D0 비교는 다음 공식 버전에 재사용하겠습니다. 다만 현재 팀 계약 기준으로는 2022가 모델 선택·calibration 적합에 쓰여 순수 temporal validation이 아니고, 2023 성능이 이미 생성·공유돼 locked test도 아닙니다. 기존 결과는 실험 기록으로 보존하고, 공식 vNext는 2019~2021 내부 OOF에서 선택·calibration을 끝낸 뒤 2022를 평가 전용으로 사용하도록 개정해야 합니다. record key도 source row number가 아니라 source year + participant ID로 바꾸고, 공식 frozen canonical 및 홍주님 2023 분리 materialization 절차에 연결하겠습니다.
