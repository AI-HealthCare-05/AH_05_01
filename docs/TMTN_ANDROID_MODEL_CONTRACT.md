# 앱 ↔ AI 계약 대조표

앱의 모델 입출력을 AI 팀 계약서에 맞춘 결과입니다. **계약서가 기준이고 앱이 따라갑니다.**
앱 편의를 위해 계약을 바꾸지 않습니다.

기준 문서 (브랜치 `codex/tmtn-waist-oof-v0-1`)

| 문서 | 무엇을 정하나 |
|---|---|
| `tmtn_ai/contracts/model_experiment_protocol_v0_2.yaml` | 피처 6개·tier·release gate·금지 해석 |
| `tmtn_ai/contracts/data_contract_v0_3.yaml` | 앱 원본 입력 8개·임신 가드·허리둘레 서빙 정책 |
| `tmtn_ai/contracts/activity_feature_contract_v0_1.yaml` | 유산소 환산식·근력 일수 매핑 |
| `tmtn_ai/docs/WAIST_OOF_DEVELOPMENT_V0_1_FREEZE_RECORD.md` | 동결된 후보(W2·RF)·seed |

---

## 1. 앱 입력 — **피그마 A07 · A08 화면 기준**

온보딩은 피그마 두 단계를 그대로 따릅니다.

| 단계 | 화면 | 받는 값 |
|---|---|---|
| 1 / 2 | A07 기본 정보 | 이름 · 닉네임(선택) · **생년월(연·월만)** · **성별(필수)** · 키 · 몸무게 · 임신 여부 |
| 2 / 2 | A08 운동 정보 | 근력 주 횟수 + 강도 · **유산소 강도별 주당 시간(저·중·고)** |

**허리둘레는 받지 않습니다.** 피그마에도 없고, 잰 값은 계약상 모델 입력으로 쓸 수 없어서
화면에 두면 "저 값으로 계산됐나?" 하는 오해만 생깁니다. 항상 모델이 추정합니다.

**성별은 필수입니다.** 6개 모델 피처 중 하나라 없으면 추정 자체가 불가능합니다.
피그마도 남성·여성 두 칩만 두고 건너뛰기를 주지 않았습니다.

| 계약서 필드 | 앱 필드 | 비고 |
|---|---|---|
| `age_years` | `ageYears` (birthYear·birthMonth 에서 계산) | 정확한 날짜는 안 받음 |
| `sex_code` | `sexCode: SexCode` | **KNHANES 코드 그대로 1=남 2=여** |
| `height_cm` | `heightCm` | |
| `weight_kg` | `weightKg` | |
| `leisure_aerobic_moderate_equivalent_min_week` | `aerobicModerateMinWeek` + `aerobicVigorousMinWeek` 에서 파생 | 아래 2장 |
| `strength_days_week` | `strengthDaysWeek` (0~5) | 5 = 주 5회 이상(top-code) |
| `pregnancy_status` | `pregnancyStatus` | **모델 피처 아님.** 자격·안전 판단용 |

계약에 없지만 피그마에 있어 받는 값 — **모델에는 넘기지 않습니다.**

| 앱 필드 | 쓰임 |
|---|---|
| `aerobicLightMinWeek` (저강도) | KNHANES 공식에 없음. 챌린지 추천 참고용 |
| `strengthIntensity` (가볍게/적당히/힘들게) | 계약에 없음. 챌린지 난이도용 |

## 2. ⚠️ 유산소 입력 방식 — 강호님 확인이 필요합니다

계약서는 앱 원본 입력을 이렇게 제시했습니다.

```yaml
app_raw_inputs:
  aerobic_days_week          # 주 며칠
  aerobic_minutes_per_session # 한 번에 몇 분
  aerobic_typical_intensity  # moderate | vigorous
app_formula_by_intensity:
  moderate: days * minutes
  vigorous: 2 * days * minutes
```

**피그마는 강도별 주당 총 시간을 받습니다** (저강도 60분 / 중강도 90분 / 고강도 0분).
그래서 앱은 이렇게 계산합니다.

```
leisure_aerobic_moderate_equivalent_min_week
  = aerobicModerateMinWeek + 2 × aerobicVigorousMinWeek
```

이건 계약서의 `app_formula_by_intensity` 가 아니라
**`knhanes_formula` 원식** 과 같은 모양입니다.

```yaml
knhanes_formula: leisure_moderate_min_week + 2 * leisure_vigorous_min_week
```

계약서 스스로 `app_formula_by_intensity` 방식의 한계를 이렇게 적어 두었습니다.

```yaml
limitations:
  - app_typical_intensity_simplifies_mixed_intensity_sessions
  - knhanes_leisure_feature_can_combine_moderate_and_vigorous_exactly
```

즉 **피그마 방식이 그 한계를 없애고 조사 자료와 더 정확히 맞습니다.**
다만 `app_raw_inputs` 목록과는 다른 모양이므로, 강호님께 이 한 가지만 확인받아야 합니다.

> 앱이 일수·1회시간·단일강도 대신 **강도별 주당 분**을 받아서
> `moderate + 2×vigorous` 로 파생 피처를 만들어도 괜찮은가?
> (학습 데이터 쪽은 `leisure_moderate_min_week` / `leisure_vigorous_min_week` 를 그대로 쓰므로
>  분포가 더 잘 맞을 것으로 보입니다)

**저강도는 파생 피처에 넣지 않습니다.** KNHANES 공식에 없는 값이라 그대로 뺐습니다.

## 3. 허리둘레 서빙 정책 — 가장 조심한 부분

계약서가 두 가지를 동시에 요구합니다.

```yaml
# protocol_v0_2
features.estimated_waist_cm:
  raw_app_input: false
  measured_waist_as_disease_input: prohibited     # 잰 값을 모델 입력으로 쓰면 안 됨

# data_contract_v0_3
waist_serving_policy:
  measured_value_overrides_estimate: true          # 화면 표시는 잰 값이 우선
```

그래서 앱은 **두 값을 분리해서** 들고 다닙니다 (`WaistForServing`).

| 쓰임 | 값 |
|---|---|
| 화면 표시 | 잰 값이 있으면 잰 값, 없으면 추정값 |
| 모델 입력 | **항상 추정값** (`modelInputCm`) |

`TodayViewModel` 에서 지수 모델에 넘기는 줄은 `waistForServing.modelInputCm` 입니다.
여기를 `measuredWaistCm` 로 바꾸면 계약 위반입니다. 주석으로 표시해 뒀습니다.

기록 필드도 계약서 이름 그대로 담습니다 — `waist_source` `waist_estimator_version` `input_snapshot_id`.

## 4. 임신·연령 가드

```yaml
pregnancy:
  model_cohort_policy: exclude_from_all_p0_models
  serving_rule: return_unsupported_population_without_general_adult_scores
  challenge_service_policy: separate_safe_path_not_blanket_exclusion
```

- `ModelResult.UnsupportedPopulation` 상태를 새로 만들었습니다. **일반 성인 점수를 대신 보여주지 않습니다.**
- 만 19세 미만도 같은 처리입니다 (`scope.minimum_age_years: 19`).
- 다만 **챌린지 서비스는 막지 않습니다.** 카드·미션·기록은 그대로 됩니다.

## 5. 결과 해석 문구

계약서 `prohibited_interpretations` 를 앱 문구에 반영했습니다.

| 금지 | 앱에서 |
|---|---|
| `future_incidence_probability` | "앞으로 질환이 생길 확률이 아닙니다" 를 고지문에 명시 |
| `clinical_diagnosis` | "비진단용 참고 정보입니다" 상시 노출 |
| `treatment_recommendation` | 치료·복약 관련 문구 없음 |

질환 확률의 의미는 `current_single_visit_measurement_abnormality_reference`,
즉 **"지금 이 한 번의 측정이 기준치를 벗어날 가능성"** 입니다.

---

## 6. ⚠️ 팀에서 확인이 필요한 것

### 6-1. 틈튼지수는 AI 계약 범위 밖입니다

```yaml
objective.tmtn_index:
  included: false
  reason: separately_validated_composite_formula_required
```

강호님 파이프라인이 내주는 출력은 셋뿐입니다.

- `estimated_waist_cm`
- `diabetes_measurement_abnormality_probability`
- `hypertension_measurement_abnormality_probability`

**틈튼지수(0~100 합성 점수)를 만들어 줄 사람이 지금 없습니다.**
앱 홈과 참고 탭의 핵심 숫자가 이것이라 기획상 큰 구멍입니다. 셋 중 하나를 정해야 합니다.

1. 팀이 합성 공식을 따로 정의하고 검증한다 (누가·언제)
2. 틈튼지수를 걷어내고 질환 확률 두 개를 그대로 보여준다
3. MVP 에서는 지수를 "참고 지표" 로만 두고 발표에서 모델 산출물과 분리해 설명한다

### 6-2. 아직 열려 있는 계약 항목

| 항목 | 계약서 상태 |
|---|---|
| 성별 매핑 제품 정책 | `knhanes_1_male_2_female_confirmed_product_policy_pending` — "선택 안 함" 을 어떻게 다룰지 미정 |
| 80세 이상 top-code | `serving_rule_status: pending` |
| 허리둘레 유효 범위 | `valid_range_status: ...final_serving_bounds_pending` |
| 임신 서빙 가드 구현 | `open_items` 에 `implement_pregnancy_serving_guard` 로 남아 있음 |

앱은 위 항목들을 **보수적으로** 처리했습니다.
성별 미입력 → 추정 안 함 / 임신 → UnsupportedPopulation / 허리둘레 50~150cm 로 제한.
계약이 확정되면 여기에 맞춰 다시 고칩니다.

### 6-3. 지금 붙어 있는 건 여전히 임시 구현입니다

인터페이스만 계약에 맞췄고, 안에 든 계산식은 근거 없는 임시값입니다.
`isPlaceholder = true` 라서 화면에 "샘플 값" 이 계속 뜹니다.

동결된 실모델(W2 · RandomForest · seed 42)을 넣을 때 확인할 것

- 피처 순서는 `WaistInputTier.features` 그대로 — **순서가 어긋나면 에러 없이 값만 틀립니다**
- `estimatorVersion` 에 실제 버전 문자열을 넣습니다
- `ModelRegistry.waistEstimator` 한 줄만 바꾸면 화면은 그대로 동작합니다
