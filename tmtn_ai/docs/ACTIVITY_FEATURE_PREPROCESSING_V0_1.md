# 틈튼 운동 피처 전처리 설계 v0.1

작성일: 2026-08-24  
계약: `contracts/activity_feature_contract_v0_1.yaml`  
구현: `src/activity_features.py`  
확인 감사: `src/audit_activity_features.py`  
상태: 합성 테스트 통과, 2019~2021 집계 확인 대기, 모델 학습 금지

## 1. 설계 결론

운동 피처를 하나의 산식으로 처리하지 않고 두 트랙으로 분리한다.

| 트랙 | 범위 | 출력 | 사용처 |
|---|---|---|---|
| KNHANES 공식 재현 | 일+이동+여가 | `pa_aerobic_reproduced` | 공식 변수 재현 감사, 연구 comparator |
| 앱 정렬 | 여가 유산소운동 | `leisure_aerobic_moderate_equivalent_min_week` | 6개 입력 모델의 1차 운동 피처 후보 |

공식 `pa_aerobic`은 앱 여가운동 피처가 아니며, 이진값에서 빈도나 시간을 역산하지 않는다.

## 2. 공식 `pa_aerobic` 재현

### 영역별 주당 시간

참여 여부가 `2=아니오`이면 해당 영역은 0분이다. `1=예`이면 일수 1~7, 시간 0~23, 분 0~59가 모두 유효해야 한다. `8/9`, `88/99`, 결측 또는 잘못된 조합은 0이 아니라 결측이다.

```text
weekly_minutes = days * (hours * 60 + minutes)
```

### 전체 산식

```text
total_vigorous_min_week =
    work_vigorous_min_week
    + leisure_vigorous_min_week

total_moderate_min_week =
    work_moderate_min_week
    + leisure_moderate_min_week
    + transport_moderate_min_week

total_moderate_equivalent_min_week =
    total_moderate_min_week
    + 2 * total_vigorous_min_week

pa_aerobic_reproduced =
    total_moderate_min_week >= 150
    OR total_vigorous_min_week >= 75
    OR total_moderate_equivalent_min_week >= 150
```

5개 영역이 모두 계산 가능한 경우에만 `0/1`을 출력한다.

## 3. 앱 정렬 여가운동 피처

### KNHANES 개발 자료

```text
leisure_aerobic_moderate_equivalent_min_week =
    leisure_moderate_min_week
    + 2 * leisure_vigorous_min_week
```

여가 중강도와 고강도 분기를 모두 계산할 수 있어야 한다.

### 앱 입력

```text
중강도:
days_week * minutes_per_session

고강도:
2 * days_week * minutes_per_session
```

앱은 대표 강도 하나를 받으므로 혼합 강도 세션을 단순화한다. KNHANES에서는 두 강도를 별도로 합산할 수 있다. 모델에는 동일한 단위의 최종 수치만 전달하지만 이 측정 차이는 모델 카드와 QA 문서에 기록한다.

### 입력 일관성

- 0일: 회당 시간은 0 또는 미입력, 강도는 선택하지 않아도 됨
- 1~7일: 회당 시간은 양수, 강도는 `moderate` 또는 `vigorous`
- 회당 시간 기술 범위: 0~1,440분
- 비현실적 상한에 대한 제품 입력 제한은 별도 승인 필요

## 4. 근력운동

| `BE5_1` | 실제 의미 | 모델값 | top-coded |
|---:|---|---:|---|
| 1 | 0일 | 0 | false |
| 2 | 1일 | 1 | false |
| 3 | 2일 | 2 | false |
| 4 | 3일 | 3 | false |
| 5 | 4일 | 4 | false |
| 6 | 5일 이상 | 5 | true |
| 8 | 구조적 비해당 | 결측 | 결측 |
| 9 | 모름/무응답 | 결측 | 결측 |

공식 근력운동 실천 여부는 원시코드 `3/4/5/6`일 때 1이다. 원시코드 `>=2`로 구현하지 않는다.

## 5. 코드 출력

`derive_knhanes_activity_features(frame)`은 입력 프레임을 변경하지 않고 파생 컬럼만 반환한다.

### 모델 후보

- `leisure_aerobic_moderate_equivalent_min_week`
- `strength_days_week`

### 감사·비교 전용

- 5개 영역별 `*_min_week`
- `total_vigorous_min_week`
- `total_moderate_min_week`
- `total_moderate_equivalent_min_week`
- `pa_aerobic_reproduced`
- `pa_aerobic_official`
- `pa_aerobic_match`
- `pa_muscle_reproduced`
- `leisure_meets_150_equivalent`

### 품질 플래그

- `total_activity_complete`
- `leisure_activity_complete`
- `strength_top_coded`

## 6. 임상 참고자료 반영 범위

- 한국·WHO 지침의 150분/75분 및 근력 2일 기준은 임계값의 공중보건적 정합성 근거다.
- KNHANES 전처리의 직접 산식 근거는 공식 이용지침서의 SAS다.
- 당뇨병·고혈압 지침의 운동 처방은 안전·설명 콘텐츠에 사용하며 모델 피처 결측 처리나 질환 라벨을 변경하지 않는다.
- 앱 강도 설명은 토크테스트 또는 RPE를 사용할 수 있지만 제품 문구 승인이 필요하다.

상세 대조 결과는 `docs/CLINICAL_REFERENCE_NOTES_REVIEW_2026_08_24.md`를 따른다.

## 7. 2019~2021 집계 확인 명령

이 명령은 공식 산식 일치도와 파생 피처 분포를 집계할 뿐 행·ID를 출력하거나 모델을 학습하지 않는다. 2022·2023은 규칙 수정용 확인에서 차단되고 2024는 금지된다.

```powershell
python -m src.audit_activity_features `
  --contract 'contracts/activity_feature_contract_v0_1.yaml' `
  --year-file '2019=C:\Users\FORYOUCOM\PycharmProjects\AH_05_01\data\01_raw\knhanes\year=2019\hn19_all.csv' `
  --year-file '2020=C:\Users\FORYOUCOM\PycharmProjects\AH_05_01\data\01_raw\knhanes\year=2020\hn20_all.csv' `
  --year-file '2021=C:\Users\FORYOUCOM\PycharmProjects\AH_05_01\data\01_raw\knhanes\year=2021\hn21_all.csv' `
  --output-json 'reports\activity_audit\activity_confirmation_2019_2021_v0_2.json' `
  --output-md 'reports\activity_audit\activity_confirmation_2019_2021_v0_2.md'
```

## 8. 확인 게이트

- 입력 원자료 SHA-256이 값 감사 때와 동일
- `pa_aerobic` 비교 가능 수 확인
- 불일치 0건 또는 불일치 원인을 공식 SAS와 대조해 문서화
- 전체·여가 활동 완전성 확인
- 5개 활동 영역별 완전성 확인
- 원시 컬럼별 예상 밖 코드가 0인지 확인
- 특수코드를 0으로 변환하지 않았는지 확인
- `pa_muscle`이 존재하면 공식값과 재현값 비교
- 보고서에 원시 행·ID가 없음
- `model_training_performed=false`

## 9. 아직 하지 않는 것

- 실제 canonical train/test CSV 생성
- 결측 대치 fit
- 피처 선택
- 모델 학습·튜닝·성능 평가
- 2022·2023 결과에 맞춘 산식 변경
- 2024 값 접근
