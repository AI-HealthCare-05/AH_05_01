# TMTN 임상·신체활동 참고 지침 검토 및 모델 계약 결정

> 결정일: 2026-08-24  
> 적용 대상: KNHANES 2019~2024 기반 허리둘레·당뇨·고혈압 P0 모델  
> 상태: 강호 검토·승인 완료, 병학 공동 검토 요청 완료  
> 학습 상태: `do_not_train: true`

## 1. 검토 목적

KNHANES 데이터 ver3에서 제안된 primary 라벨, 임신자 처리, 운동 입력, 모델 명칭과 검증 구조가 최신 임상·신체활동 참고 지침 및 제품 목적에 맞는지 검토하고 데이터 계약에 반영할 결정을 고정한다.

본 결정은 의료 진단 모델을 만들기 위한 것이 아니다. TMTN은 사용자가 제공한 제한된 입력으로 현재 건강검진 측정 이상 가능성을 참고 정보로 제공하는 제품이며, 임상 진단·치료 결정을 대체하지 않는다.

## 2. 참고 문서

- `2025 당뇨병 진료지침_전문_최종본.pdf`
  - 당뇨병 진단기준, 반복 확인, HbA1c 해석 주의, 운동요법 검토
- `2022년 고혈압 진료지침.pdf`
  - 진료실 혈압 기준, 반복 측정, 가정·활동혈압 검토
- `2026년 고혈압 진료지침.pdf`
  - 고혈압 선별·진단, 치료 목표, 임신중 고혈압, 운동 권고 검토
- `WHO guidelines on physical activity and sedentary behaviour.pdf`
  - 성인·만성질환자·임신부의 신체활동 영역과 권장량 검토
- `한국인을 위한 신체활동 지침서 개정판.pdf`
  - 한국 성인의 중강도·고강도 환산, 근력운동, 임신부 지침 검토

## 3. 결정 요약

| ID | 최종 결정 | 계약 반영 |
|---|---|---|
| A1′ | 임상적 질환 위험이 아닌 현재 단일 방문 측정 이상 가능성을 primary로 사용 | 출력명·라벨 의미 제한 |
| A2′ | 임신자를 P0 모델 cohort에서 제외하고 앱 eligibility로 차단 | 학습 제외 + serving guard |
| A3′ | 여가 유산소의 일수·시간·대표 강도를 받아 중강도 환산 분/주 생성 | F2-leisure 정렬 |
| A4 | 입력 피처 세트 F와 질환 파이프라인 D 명칭 분리 | F0/F1/F2, D0/D1/D2 |
| A5 | 2019~2021 개발, 2022 검증, 2023 잠금 테스트, 2024 개봉 진단 | 연도 역할 고정 |

## 4. A1′ — Primary 출력 의미

### 4.1 당뇨 측정 이상 primary

```text
eligibility:
age >= 19
AND not pregnant
AND fasting_hours >= 8
AND fasting_glucose and HbA1c are both available

positive:
fasting_glucose >= 126 mg/dL
OR HbA1c >= 6.5%
```

### 4.2 혈압 측정 이상 primary

```text
eligibility:
age >= 19
AND not pregnant
AND official SBP and DBP are available

positive:
SBP >= 140 mmHg
OR DBP >= 90 mmHg
```

### 4.3 허용되는 제품 의미

- 현재 단일 방문 혈당 측정 이상 가능성
- 현재 단일 방문 혈압 측정 이상 가능성
- 건강검진 참고 정보

### 4.4 금지되는 제품 의미

- 당뇨병 또는 고혈압 확진
- 미래 발병 확률
- 개인의 임상적 진단 또는 치료 필요성 판정
- 한 번의 예측 결과를 이용한 약물 시작·중단 안내

### 4.5 치료 중 정상 측정자

- 측정값이 기준 미만이면 measurement primary에서는 음성이다.
- 진단·복약을 포함한 공식 composite comparator에서는 질환 보유 상태를 유지할 수 있다.
- 두 결과는 서로 다른 estimand이므로 한 지표로 합치지 않는다.

## 5. A2′ — 임신자 제외 및 serving guard

### 5.1 모델 cohort

임신자는 다음 P0 cohort에서 모두 제외한다.

- 허리둘레 추정 모델
- 당뇨 측정 이상 모델
- 고혈압 측정 이상 모델

### 5.2 앱 및 API

- 임신 여부는 예측 피처가 아니라 `eligibility/safety field`로 수집한다.
- 임신 중이거나 임신 여부 확인이 필요한 경우 일반 성인용 P0 점수를 제공하지 않는다.
- API는 `unsupported_population` 계열 상태와 사용자용 안전 문구를 반환한다.
- 임신부를 앱과 생활습관 챌린지에서 전면 제외하지 않는다.
- 임신부용 챌린지는 별도 안전 기준과 기획·QA 승인을 거친다.

### 5.3 값 수준 감사

- `HE_prg`, `HE_dprg` 코드와 결측을 연도별로 확인한다.
- 두 변수의 모순 조합을 확인한다.
- 임신 여부를 확정할 수 없는 행의 제외 규칙을 계약한다.

## 6. A3′ — 여가 유산소 입력

### 6.1 앱 원시 응답

```text
aerobic_days_week
aerobic_minutes_per_session
aerobic_typical_intensity: moderate | vigorous
```

### 6.2 모델 파생 피처

```text
moderate:
leisure_aerobic_moderate_equivalent_min_week
  = aerobic_days_week * aerobic_minutes_per_session

vigorous:
leisure_aerobic_moderate_equivalent_min_week
  = 2 * aerobic_days_week * aerobic_minutes_per_session
```

### 6.3 범위와 한계

- 앱 문구는 여가 목적 유산소운동으로 제한한다.
- 직장·이동·가사 활동을 포함하는 전체 신체활동으로 표현하지 않는다.
- `MET-minutes`라는 명칭을 사용하지 않는다.
- 대표 강도 한 개를 받는 P0 방식은 혼합 강도 운동을 단순화한다는 점을 문서화한다.
- 앱 UI 응답 필드는 늘어나지만 모델 입력 피처는 파생값 한 개이므로 6개 모델 피처 계약은 유지된다.
- UI 질문 수를 반드시 6개로 제한해야 한다면 이 계약을 사용하지 않고, 비가중 여가 운동시간 피처를 별도 계약해야 한다.

### 6.4 KNHANES 정렬

- `F2-leisure`는 여가 중강도·고강도 일수 및 시간 문항으로 생성한다.
- 고강도 1분을 중강도 2분으로 환산한다.
- 공식 branch-aware skip logic을 재현한다.
- `pa_aerobic`에서 빈도·시간을 역추정하지 않는다.
- `F2-total`은 전체 신체활동을 사용하는 연구 비교용으로만 둔다.

## 7. A4 — F 피처 세트와 D 파이프라인

```text
F0 = age + sex + height + weight

F1 = F0
     + pa_aerobic
     + strength_days_week

F2-total = F0
           + total moderate-equivalent activity minutes/week
           + strength_days_week

F2-leisure = F0
             + leisure aerobic moderate-equivalent minutes/week
             + strength_days_week

D0 = selected F set only
D1 = D0 + out-of-fold estimated waist
D2 = D1 + future independently approved submodel outputs
```

- BMI는 키·몸무게로 서버 재계산하며 독립 앱 입력으로 세지 않는다.
- `F2-leisure + D1`은 P0 주 후보이지 사전 확정된 최종 모델이 아니다.
- F0는 선택형 입력을 건너뛰거나 운동 입력이 부족한 경우의 fallback 후보다.

## 8. A5 — 검증 연도

```text
2019~2021: development + repeated stratified CV
2019~2021: auxiliary random 80/20 comparison
2022: temporal validation
2023: locked internal test
2024: opened diagnostic evaluation; no tuning
future year/external cohort: independent final release evaluation
```

- 2022·2023·2024는 random 8:2 모집단에 포함하지 않는다.
- 모델·전처리·결측 처리·보정 선택은 2023 잠금 테스트 전에 고정한다.
- 2023 또는 2024 결과에 맞춰 동일 버전을 재튜닝하지 않는다.
- 2024를 sealed 또는 independent holdout이라고 부르지 않는다.

## 9. 아직 확정되지 않은 항목

- 앱 성별 범주와 KNHANES 이진 성별 코드의 매핑·지원 범위
- `HE_prg`, `HE_dprg`의 값 수준 임신 제외 구현식
- HbA1c 2022년 이후 연구자 전환식 적용 여부
- 혈압계 변경에 따른 연구자 전환식 적용 여부
- 변수별 특수코드와 유효범위
- 대표 운동강도 질문의 최종 사용자 문구
- 모델별 공개 성능·calibration·하위집단 게이트 수치
- 미래 독립 최종 평가자료와 custodian

## 10. 다음 단계 진입 조건

다음 작업은 2019~2023 값 수준 감사이며, 다음 조건이 충족되기 전까지 실제 학습을 시작하지 않는다.

- `data_contract_v0_3.yaml` 공동 검토
- `value_audit_spec_v0_1` 승인
- 값 수준 감사 실행 및 결과 검토
- 데이터 계약 v1.0 승인
- `do_not_train`을 버전 변경으로 명시적으로 해제
