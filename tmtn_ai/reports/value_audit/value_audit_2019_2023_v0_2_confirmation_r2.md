# KNHANES 2019~2023 값 수준 감사 보고서

> 감사 버전: `v0.2-confirmation`  
> 상태: 코드 실행 결과 — 모델 학습 결과 아님  
> 개인정보 정책: 집계값만 포함, 원시 행·ID 미포함

## 실행 정책

- 감사 연도: 2019, 2020, 2021, 2022, 2023
- 금지 연도: 2024
- 최소 셀 크기: 5
- 누락된 설정 연도: 없음

## 2019년

- 파일: `hn19_all.csv`
- 행 수: 8110
- 선택 컬럼 수: 35
- 선택적 누락 컬럼: 없음

### 변수 요약

| 개념 | 컬럼 | 유형 | 결측 수 | 결측률 | 범위 아래 | 범위 위 |
|---|---|---|---:|---:|---:|---:|
| age | `age` | numeric | 0 | 0.0 | 1504 | 0 |
| sex | `sex` | categorical | 0 | 0.0 | - | - |
| height_cm | `HE_ht` | numeric | 438 | 0.054 | 172 | 0 |
| weight_kg | `HE_wt` | numeric | 417 | 0.0514 | 495 | 0 |
| official_bmi | `HE_BMI` | numeric | 440 | 0.0543 | 0 | 0 |
| waist_cm | `HE_wc` | numeric | 793 | 0.0978 | 0 | 0 |
| leisure_vigorous_participation | `BE3_75` | categorical | 402 | 0.0496 | - | - |
| leisure_vigorous_days | `BE3_76` | numeric | 402 | 0.0496 | 0 | 0 |
| leisure_vigorous_hours | `BE3_77` | numeric | 402 | 0.0496 | 0 | 0 |
| leisure_vigorous_minutes | `BE3_78` | numeric | 402 | 0.0496 | 0 | 0 |
| leisure_moderate_participation | `BE3_85` | categorical | 402 | 0.0496 | - | - |
| leisure_moderate_days | `BE3_86` | numeric | 402 | 0.0496 | 0 | 0 |
| leisure_moderate_hours | `BE3_87` | numeric | 402 | 0.0496 | 0 | 0 |
| leisure_moderate_minutes | `BE3_88` | numeric | 402 | 0.0496 | 0 | 0 |
| strength_days | `BE5_1` | categorical | 402 | 0.0496 | - | - |
| aerobic_threshold | `pa_aerobic` | categorical | 2192 | 0.2703 | - | - |
| fasting_hours | `HE_fst` | numeric | 1347 | 0.1661 | 0 | 0 |
| fasting_glucose | `HE_glu` | numeric | 1345 | 0.1658 | 0 | 0 |
| hba1c | `HE_HbA1c` | numeric | 1347 | 0.1661 | 0 | 0 |
| official_diabetes_status | `HE_DM_HbA1c` | categorical | 2196 | 0.2708 | - | - |
| official_sbp | `HE_sbp` | numeric | 1188 | 0.1465 | 0 | 0 |
| sbp_round_1 | `HE_sbp1` | numeric | 1188 | 0.1465 | 0 | 0 |
| sbp_round_2 | `HE_sbp2` | numeric | 1188 | 0.1465 | 0 | 0 |
| sbp_round_3 | `HE_sbp3` | numeric | 1188 | 0.1465 | 0 | 0 |
| official_dbp | `HE_dbp` | numeric | 1188 | 0.1465 | 0 | 0 |
| dbp_round_1 | `HE_dbp1` | numeric | 1188 | 0.1465 | 0 | 0 |
| dbp_round_2 | `HE_dbp2` | numeric | 1188 | 0.1465 | 0 | 0 |
| dbp_round_3 | `HE_dbp3` | numeric | 1188 | 0.1465 | 0 | 0 |
| official_hypertension_status | `HE_HP` | categorical | 1849 | 0.228 | - | - |
| pregnancy_status | `HE_prg` | categorical | 395 | 0.0487 | - | - |
| pregnancy_month | `HE_dprg` | categorical | 8082 | 0.9965 | - | - |
| examination_weight | `wt_itvex` | numeric | 394 | 0.0486 | - | - |
| strata | `kstrata` | categorical | 0 | 0.0 | - | - |
| psu | `psu` | categorical | 0 | 0.0 | - | - |

### 공식값 재현 검사

- bmi_recalculation: eligible=7670, mismatch=0, match_rate=1.0
- systolic_round_mean: eligible=6922, mismatch=0, match_rate=1.0
- diastolic_round_mean: eligible=6922, mismatch=0, match_rate=1.0

### 후보 cohort·측정 이상 집계

- diabetes_nonpregnant_adult_measurement_cohort: eligible=5914, positive=729, positive_rate=0.1233; pregnancy_exclusion_applied=true
- hypertension_nonpregnant_adult_measurement_cohort: eligible=6233, positive=1037, positive_rate=0.1664; pregnancy_exclusion_applied=true
- waist_nonpregnant_adult_cohort: eligible=6240; pregnancy_exclusion_applied=true
- vigorous_yes_complete_duration: eligible=590, positive=590, positive_rate=1.0; pregnancy_exclusion_applied=false
- moderate_yes_complete_duration: eligible=1468, positive=1468, positive_rate=1.0; pregnancy_exclusion_applied=false

## 2020년

- 파일: `hn20_all.csv`
- 행 수: 7359
- 선택 컬럼 수: 35
- 선택적 누락 컬럼: 없음

### 변수 요약

| 개념 | 컬럼 | 유형 | 결측 수 | 결측률 | 범위 아래 | 범위 위 |
|---|---|---|---:|---:|---:|---:|
| age | `age` | numeric | 0 | 0.0 | 1226 | 0 |
| sex | `sex` | categorical | 0 | 0.0 | - | - |
| height_cm | `HE_ht` | numeric | 367 | 0.0499 | 140 | 0 |
| weight_kg | `HE_wt` | numeric | 292 | 0.0397 | 378 | 0 |
| official_bmi | `HE_BMI` | numeric | 370 | 0.0503 | 0 | 0 |
| waist_cm | `HE_wc` | numeric | 603 | 0.0819 | 0 | 0 |
| leisure_vigorous_participation | `BE3_75` | categorical | 265 | 0.036 | - | - |
| leisure_vigorous_days | `BE3_76` | numeric | 265 | 0.036 | 0 | 0 |
| leisure_vigorous_hours | `BE3_77` | numeric | 265 | 0.036 | 0 | 0 |
| leisure_vigorous_minutes | `BE3_78` | numeric | 265 | 0.036 | 0 | 0 |
| leisure_moderate_participation | `BE3_85` | categorical | 265 | 0.036 | - | - |
| leisure_moderate_days | `BE3_86` | numeric | 265 | 0.036 | 0 | 0 |
| leisure_moderate_hours | `BE3_87` | numeric | 265 | 0.036 | 0 | 0 |
| leisure_moderate_minutes | `BE3_88` | numeric | 265 | 0.036 | 0 | 0 |
| strength_days | `BE5_1` | categorical | 265 | 0.036 | - | - |
| aerobic_threshold | `pa_aerobic` | categorical | 1961 | 0.2665 | - | - |
| fasting_hours | `HE_fst` | numeric | 1023 | 0.139 | 0 | None |
| fasting_glucose | `HE_glu` | numeric | 1023 | 0.139 | 0 | 0 |
| hba1c | `HE_HbA1c` | numeric | 1025 | 0.1393 | 0 | 0 |
| official_diabetes_status | `HE_DM_HbA1c` | categorical | 1747 | 0.2374 | - | - |
| official_sbp | `HE_sbp` | numeric | 802 | 0.109 | 0 | 0 |
| sbp_round_1 | `HE_sbp1` | numeric | 802 | 0.109 | 0 | 0 |
| sbp_round_2 | `HE_sbp2` | numeric | 802 | 0.109 | 0 | 0 |
| sbp_round_3 | `HE_sbp3` | numeric | 802 | 0.109 | 0 | 0 |
| official_dbp | `HE_dbp` | numeric | 802 | 0.109 | 0 | 0 |
| dbp_round_1 | `HE_dbp1` | numeric | 802 | 0.109 | None | 0 |
| dbp_round_2 | `HE_dbp2` | numeric | 802 | 0.109 | 0 | 0 |
| dbp_round_3 | `HE_dbp3` | numeric | 802 | 0.109 | 0 | 0 |
| official_hypertension_status | `HE_HP` | categorical | 1550 | 0.2106 | - | - |
| pregnancy_status | `HE_prg` | categorical | 263 | 0.0357 | - | - |
| pregnancy_month | `HE_dprg` | categorical | 7348 | 0.9985 | - | - |
| examination_weight | `wt_itvex` | numeric | 263 | 0.0357 | - | - |
| strata | `kstrata` | categorical | 0 | 0.0 | - | - |
| psu | `psu` | categorical | 0 | 0.0 | - | - |

### 공식값 재현 검사

- bmi_recalculation: eligible=6989, mismatch=0, match_rate=1.0
- systolic_round_mean: eligible=6557, mismatch=0, match_rate=1.0
- diastolic_round_mean: eligible=6557, mismatch=0, match_rate=1.0

### 후보 cohort·측정 이상 집계

- diabetes_nonpregnant_adult_measurement_cohort: eligible=5615, positive=792, positive_rate=0.1411; pregnancy_exclusion_applied=true
- hypertension_nonpregnant_adult_measurement_cohort: eligible=5802, positive=948, positive_rate=0.1634; pregnancy_exclusion_applied=true
- waist_nonpregnant_adult_cohort: eligible=5874; pregnancy_exclusion_applied=true
- vigorous_yes_complete_duration: eligible=538, positive=538, positive_rate=1.0; pregnancy_exclusion_applied=false
- moderate_yes_complete_duration: eligible=1463, positive=1463, positive_rate=1.0; pregnancy_exclusion_applied=false

## 2021년

- 파일: `hn21_all.csv`
- 행 수: 7090
- 선택 컬럼 수: 35
- 선택적 누락 컬럼: 없음

### 변수 요약

| 개념 | 컬럼 | 유형 | 결측 수 | 결측률 | 범위 아래 | 범위 위 |
|---|---|---|---:|---:|---:|---:|
| age | `age` | numeric | 0 | 0.0 | 1138 | 0 |
| sex | `sex` | categorical | 0 | 0.0 | - | - |
| height_cm | `HE_ht` | numeric | 475 | 0.067 | 93 | 0 |
| weight_kg | `HE_wt` | numeric | 404 | 0.057 | 313 | 0 |
| official_bmi | `HE_BMI` | numeric | 486 | 0.0685 | 0 | 0 |
| waist_cm | `HE_wc` | numeric | 661 | 0.0932 | 0 | 0 |
| leisure_vigorous_participation | `BE3_75` | categorical | 362 | 0.0511 | - | - |
| leisure_vigorous_days | `BE3_76` | numeric | 362 | 0.0511 | 0 | 0 |
| leisure_vigorous_hours | `BE3_77` | numeric | 362 | 0.0511 | 0 | 0 |
| leisure_vigorous_minutes | `BE3_78` | numeric | 362 | 0.0511 | 0 | 0 |
| leisure_moderate_participation | `BE3_85` | categorical | 362 | 0.0511 | - | - |
| leisure_moderate_days | `BE3_86` | numeric | 362 | 0.0511 | 0 | 0 |
| leisure_moderate_hours | `BE3_87` | numeric | 362 | 0.0511 | 0 | 0 |
| leisure_moderate_minutes | `BE3_88` | numeric | 362 | 0.0511 | 0 | 0 |
| strength_days | `BE5_1` | categorical | 362 | 0.0511 | - | - |
| aerobic_threshold | `pa_aerobic` | categorical | 1774 | 0.2502 | - | - |
| fasting_hours | `HE_fst` | numeric | 1073 | 0.1513 | 0 | 0 |
| fasting_glucose | `HE_glu` | numeric | 1073 | 0.1513 | 0 | 0 |
| hba1c | `HE_HbA1c` | numeric | 1081 | 0.1525 | 0 | 0 |
| official_diabetes_status | `HE_DM_HbA1c` | categorical | 1743 | 0.2458 | - | - |
| official_sbp | `HE_sbp` | numeric | 782 | 0.1103 | 0 | 0 |
| sbp_round_1 | `HE_sbp1` | numeric | 781 | 0.1102 | 0 | 0 |
| sbp_round_2 | `HE_sbp2` | numeric | 782 | 0.1103 | 0 | 0 |
| sbp_round_3 | `HE_sbp3` | numeric | 781 | 0.1102 | 0 | 0 |
| official_dbp | `HE_dbp` | numeric | 782 | 0.1103 | 0 | 0 |
| dbp_round_1 | `HE_dbp1` | numeric | 781 | 0.1102 | None | 0 |
| dbp_round_2 | `HE_dbp2` | numeric | 782 | 0.1103 | 0 | 0 |
| dbp_round_3 | `HE_dbp3` | numeric | 781 | 0.1102 | 0 | 0 |
| official_hypertension_status | `HE_HP` | categorical | 1498 | 0.2113 | - | - |
| pregnancy_status | `HE_prg` | categorical | 359 | 0.0506 | - | - |
| pregnancy_month | `HE_dprg` | categorical | 7079 | 0.9984 | - | - |
| examination_weight | `wt_itvex` | numeric | 359 | 0.0506 | - | - |
| strata | `kstrata` | categorical | 0 | 0.0 | - | - |
| psu | `psu` | categorical | 0 | 0.0 | - | - |

### 공식값 재현 검사

- bmi_recalculation: eligible=6604, mismatch=0, match_rate=1.0
- systolic_round_mean: eligible=6308, mismatch=0, match_rate=1.0
- diastolic_round_mean: eligible=6308, mismatch=0, match_rate=1.0

### 후보 cohort·측정 이상 집계

- diabetes_nonpregnant_adult_measurement_cohort: eligible=5351, positive=758, positive_rate=0.1417; pregnancy_exclusion_applied=true
- hypertension_nonpregnant_adult_measurement_cohort: eligible=5584, positive=777, positive_rate=0.1391; pregnancy_exclusion_applied=true
- waist_nonpregnant_adult_cohort: eligible=5618; pregnancy_exclusion_applied=true
- vigorous_yes_complete_duration: eligible=552, positive=552, positive_rate=1.0; pregnancy_exclusion_applied=false
- moderate_yes_complete_duration: eligible=1477, positive=None, positive_rate=None; pregnancy_exclusion_applied=false

## 2022년

- 파일: `hn22_all.csv`
- 행 수: 6265
- 선택 컬럼 수: 35
- 선택적 누락 컬럼: 없음

### 변수 요약

| 개념 | 컬럼 | 유형 | 결측 수 | 결측률 | 범위 아래 | 범위 위 |
|---|---|---|---:|---:|---:|---:|
| age | `age` | numeric | 0 | 0.0 | 943 | 0 |
| sex | `sex` | categorical | 0 | 0.0 | - | - |
| height_cm | `HE_ht` | numeric | 116 | 0.0185 | 97 | 0 |
| weight_kg | `HE_wt` | numeric | 38 | 0.0061 | 283 | 0 |
| official_bmi | `HE_BMI` | numeric | 119 | 0.019 | 0 | 0 |
| waist_cm | `HE_wc` | numeric | 490 | 0.0782 | 0 | 0 |
| leisure_vigorous_participation | `BE3_75` | categorical | 8 | 0.0013 | - | - |
| leisure_vigorous_days | `BE3_76` | numeric | 8 | 0.0013 | 0 | 0 |
| leisure_vigorous_hours | `BE3_77` | numeric | 8 | 0.0013 | 0 | 0 |
| leisure_vigorous_minutes | `BE3_78` | numeric | 8 | 0.0013 | 0 | 0 |
| leisure_moderate_participation | `BE3_85` | categorical | 8 | 0.0013 | - | - |
| leisure_moderate_days | `BE3_86` | numeric | 8 | 0.0013 | 0 | 0 |
| leisure_moderate_hours | `BE3_87` | numeric | 8 | 0.0013 | 0 | 0 |
| leisure_moderate_minutes | `BE3_88` | numeric | 8 | 0.0013 | 0 | 0 |
| strength_days | `BE5_1` | categorical | 8 | 0.0013 | - | - |
| aerobic_threshold | `pa_aerobic` | categorical | 1404 | 0.2241 | - | - |
| fasting_hours | `HE_fst` | numeric | 704 | 0.1124 | 0 | 0 |
| fasting_glucose | `HE_glu` | numeric | 705 | 0.1125 | 0 | 0 |
| hba1c | `HE_HbA1c` | numeric | 720 | 0.1149 | 0 | 0 |
| official_diabetes_status | `HE_DM_HbA1c` | categorical | 1258 | 0.2008 | - | - |
| official_sbp | `HE_sbp` | numeric | 369 | 0.0589 | 0 | 0 |
| sbp_round_1 | `HE_sbp1` | numeric | 369 | 0.0589 | 0 | 0 |
| sbp_round_2 | `HE_sbp2` | numeric | 369 | 0.0589 | 0 | 0 |
| sbp_round_3 | `HE_sbp3` | numeric | 369 | 0.0589 | 0 | 0 |
| official_dbp | `HE_dbp` | numeric | 369 | 0.0589 | 0 | 0 |
| dbp_round_1 | `HE_dbp1` | numeric | 369 | 0.0589 | 0 | 0 |
| dbp_round_2 | `HE_dbp2` | numeric | 369 | 0.0589 | 0 | 0 |
| dbp_round_3 | `HE_dbp3` | numeric | 369 | 0.0589 | 0 | 0 |
| official_hypertension_status | `HE_HP` | categorical | 1063 | 0.1697 | - | - |
| pregnancy_status | `HE_prg` | categorical | 0 | 0.0 | - | - |
| pregnancy_month | `HE_dprg` | categorical | 6247 | 0.9971 | - | - |
| examination_weight | `wt_itvex` | numeric | 0 | 0.0 | - | - |
| strata | `kstrata` | categorical | 0 | 0.0 | - | - |
| psu | `psu` | categorical | 0 | 0.0 | - | - |

### 공식값 재현 검사

- bmi_recalculation: eligible=6146, mismatch=0, match_rate=1.0
- systolic_round_mean: eligible=5896, mismatch=0, match_rate=1.0
- diastolic_round_mean: eligible=5896, mismatch=0, match_rate=1.0

### 후보 cohort·측정 이상 집계

- diabetes_nonpregnant_adult_measurement_cohort: eligible=5008, positive=550, positive_rate=0.1098; pregnancy_exclusion_applied=true
- hypertension_nonpregnant_adult_measurement_cohort: eligible=5186, positive=720, positive_rate=0.1388; pregnancy_exclusion_applied=true
- waist_nonpregnant_adult_cohort: eligible=5070; pregnancy_exclusion_applied=true
- vigorous_yes_complete_duration: eligible=598, positive=None, positive_rate=None; pregnancy_exclusion_applied=false
- moderate_yes_complete_duration: eligible=1550, positive=None, positive_rate=None; pregnancy_exclusion_applied=false

## 2023년

- 파일: `hn23_all.csv`
- 행 수: 6929
- 선택 컬럼 수: 35
- 선택적 누락 컬럼: 없음

### 변수 요약

| 개념 | 컬럼 | 유형 | 결측 수 | 결측률 | 범위 아래 | 범위 위 |
|---|---|---|---:|---:|---:|---:|
| age | `age` | numeric | 0 | 0.0 | 1022 | 0 |
| sex | `sex` | categorical | 0 | 0.0 | - | - |
| height_cm | `HE_ht` | numeric | 114 | 0.0165 | 105 | 0 |
| weight_kg | `HE_wt` | numeric | 52 | 0.0075 | 296 | 0 |
| official_bmi | `HE_BMI` | numeric | 116 | 0.0167 | 0 | 0 |
| waist_cm | `HE_wc` | numeric | 419 | 0.0605 | 0 | 0 |
| leisure_vigorous_participation | `BE3_75` | categorical | None | None | - | - |
| leisure_vigorous_days | `BE3_76` | numeric | None | None | 0 | 0 |
| leisure_vigorous_hours | `BE3_77` | numeric | None | None | 0 | 0 |
| leisure_vigorous_minutes | `BE3_78` | numeric | None | None | 0 | 0 |
| leisure_moderate_participation | `BE3_85` | categorical | None | None | - | - |
| leisure_moderate_days | `BE3_86` | numeric | None | None | 0 | 0 |
| leisure_moderate_hours | `BE3_87` | numeric | None | None | 0 | 0 |
| leisure_moderate_minutes | `BE3_88` | numeric | None | None | 0 | 0 |
| strength_days | `BE5_1` | categorical | None | None | - | - |
| aerobic_threshold | `pa_aerobic` | categorical | 1541 | 0.2224 | - | - |
| fasting_hours | `HE_fst` | numeric | 745 | 0.1075 | 0 | None |
| fasting_glucose | `HE_glu` | numeric | 745 | 0.1075 | 0 | 0 |
| hba1c | `HE_HbA1c` | numeric | 764 | 0.1103 | 0 | 0 |
| official_diabetes_status | `HE_DM_HbA1c` | categorical | 1425 | 0.2057 | - | - |
| official_sbp | `HE_sbp` | numeric | 335 | 0.0483 | 0 | 0 |
| sbp_round_1 | `HE_sbp1` | numeric | 335 | 0.0483 | 0 | 0 |
| sbp_round_2 | `HE_sbp2` | numeric | 335 | 0.0483 | 0 | 0 |
| sbp_round_3 | `HE_sbp3` | numeric | 335 | 0.0483 | 0 | 0 |
| official_dbp | `HE_dbp` | numeric | 335 | 0.0483 | 0 | 0 |
| dbp_round_1 | `HE_dbp1` | numeric | 335 | 0.0483 | 0 | 0 |
| dbp_round_2 | `HE_dbp2` | numeric | 335 | 0.0483 | 0 | 0 |
| dbp_round_3 | `HE_dbp3` | numeric | 335 | 0.0483 | 0 | 0 |
| official_hypertension_status | `HE_HP` | categorical | 1122 | 0.1619 | - | - |
| pregnancy_status | `HE_prg` | categorical | 0 | 0.0 | - | - |
| pregnancy_month | `HE_dprg` | categorical | 6910 | 0.9973 | - | - |
| examination_weight | `wt_itvex` | numeric | 0 | 0.0 | - | - |
| strata | `kstrata` | categorical | 0 | 0.0 | - | - |
| psu | `psu` | categorical | 0 | 0.0 | - | - |

### 공식값 재현 검사

- bmi_recalculation: eligible=6813, mismatch=0, match_rate=1.0
- systolic_round_mean: eligible=6594, mismatch=0, match_rate=1.0
- diastolic_round_mean: eligible=6594, mismatch=0, match_rate=1.0

### 후보 cohort·측정 이상 집계

- diabetes_nonpregnant_adult_measurement_cohort: eligible=5505, positive=642, positive_rate=0.1166; pregnancy_exclusion_applied=true
- hypertension_nonpregnant_adult_measurement_cohort: eligible=5790, positive=712, positive_rate=0.123; pregnancy_exclusion_applied=true
- waist_nonpregnant_adult_cohort: eligible=5716; pregnancy_exclusion_applied=true
- vigorous_yes_complete_duration: eligible=658, positive=None, positive_rate=None; pregnancy_exclusion_applied=false
- moderate_yes_complete_duration: eligible=1776, positive=None, positive_rate=None; pregnancy_exclusion_applied=false

## 해석 제한

- 본 보고서는 코드·결측·범위·공식 파생값 재현을 위한 값 감사다.
- `pregnancy_exclusion_applied=false`인 후보 집계는 최종 cohort가 아니다.
- 결과를 모델 성능, 임상 진단 또는 미래 위험으로 해석하지 않는다.
- 이 보고서만으로 `do_not_train`을 해제하지 않는다.
