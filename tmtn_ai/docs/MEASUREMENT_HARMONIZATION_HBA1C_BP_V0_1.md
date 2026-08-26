# KNHANES HbA1c·혈압 측정값 조화 설계 v0.1

작성일: 2026-08-24  
상태: 계약·합성 구현 완료, 2019~2022 집계 감사 대기  
모델 학습: 금지

## 1. 목적

2019~2022 KNHANES에서 알려진 두 측정 체계 변경이 측정값 기반 당뇨·고혈압 라벨에 미치는 영향을 확인한다.

- 2022년 HbA1c 임상검사 분석기관 변경
- 2021년 성인 혈압계의 Microlife WatchBP Office 전환

이 단계는 모델 성능평가가 아니다. 공식 전환식을 결정론적으로 적용해 분포 이동과 라벨 재분류 규모만 집계한다.

## 2. 사전 등록 정책

| 항목 | P0 주 분석 | 민감도 분석 |
|---|---|---|
| HbA1c | 공식 원자료 `HE_HbA1c` | 2022년 이후 연구자 전환값 |
| 혈압 | 공식 원자료 `HE_sbp`, `HE_dbp` | 2021년 이후 성인 Microlife→Greenlight 전환값 |

공식 통계가 전환식을 적용하지 않은 원자료를 사용하고, 전환식은 연구자의 연도 추이 비교용으로 제공됐기 때문에 원자료를 주 분석으로 유지한다. 혈압 전환식은 나이와 맥압을 포함하므로 전환값으로 주 타깃을 정의하면 모델 입력인 나이가 타깃 생성에도 들어가는 순환성이 생긴다.

집계 결과가 이전 연도 분포에 더 가까워 보인다는 이유나 향후 모델 성능이 더 좋다는 이유로 주 분석 정책을 바꾸지 않는다. 정책 변경은 공식 근거와 공동 승인에 따른 별도 계약 버전에서만 허용한다.

## 3. HbA1c 전환 후보

제9기 공식 이용지침서는 2022년 분석기관이 이전 기관보다 HbA1c를 낮게 측정하는 경향을 기록하고 다음 연구자용 전환식을 제공한다.

```text
2022년 이후 전환 HbA1c
= 1.034 × 원 HbA1c - 0.0448
```

- 2019~2021: 전환하지 않고 원자료와 동일
- 2022: 원자료와 전환값을 모두 유지해 영향만 비교
- 전환값을 원자료 컬럼에 덮어쓰지 않음
- 반올림·클리핑하지 않음
- 이중 전환 금지

### 확인 라벨

```text
eligibility:
age >= 19
AND nonpregnant
AND HE_fst >= 8
AND HE_glu available
AND HE_HbA1c available

raw diabetes measurement label:
HE_glu >= 126 OR raw HE_HbA1c >= 6.5

sensitivity label:
HE_glu >= 126 OR converted HE_HbA1c >= 6.5
```

## 4. 혈압 전환 후보

성인 혈압계 계보는 다음과 같다.

| 연도 | 성인 측정기기 |
|---:|---|
| 2019 | 수은혈압계 |
| 2020 | Greenlight300 비수은 청진형 |
| 2021~2022 | Microlife WatchBP Office 비수은 진동형 |

2020년 Greenlight300은 수은혈압계와의 차이가 허용 오차 범위로 판단되어 전환식이 제공되지 않았다. 2021년 이후 성인 Microlife 값은 다음 연구자용 Greenlight 환산식을 민감도 분석에 사용한다.

```text
pulse_pressure = raw_sbp - raw_dbp

converted_sbp =
    10.773
    + 0.771 × raw_sbp
    + 0.039 × age
    + 0.374 × pulse_pressure

converted_dbp =
    13.480
    + 0.952 × raw_dbp
    - 0.051 × age
    - 0.098 × pulse_pressure
```

- 2019~2020: 전환하지 않고 원자료와 동일
- 2021~2022: 원자료와 전환값을 병렬 비교
- 맥압은 공식 원 수축기·이완기 혈압으로 계산
- 전환값을 반올림하거나 임상 범위로 클리핑하지 않음
- 원시 `age=80`은 80세 이상 top-code이므로 전환 민감도 해석에 제한이 있음

공식 최종 혈압은 2·3차 평균으로 재현한다.

```text
HE_sbp_reproduced = (HE_sbp2 + HE_sbp3) / 2
HE_dbp_reproduced = (HE_dbp2 + HE_dbp3) / 2
```

허용 오차는 0.1mmHg다.

## 5. 데이터 사용 경계

| 연도 | 이번 감사에서의 역할 |
|---:|---|
| 2019~2021 | 개발 자료의 측정 계보 및 전환 영향 확인 |
| 2022 | HbA1c 변경 영향 확인 전용, 모델 선택·튜닝 금지 |
| 2023 | 접근 금지, locked model test 유지 |
| 2024 | 접근 금지 |

2022년은 전환 정책을 모델 점수로 선택하는 데 사용하지 않는다. 공식 문서에 근거해 정책을 먼저 등록하고, 2022년에서는 그 영향 크기만 집계한다.

## 6. 구현물

- 계약: `contracts/measurement_harmonization_contract_v0_1.yaml`
- 전환 코드: `src/measurement_harmonization.py`
- 집계 감사: `src/audit_measurement_harmonization.py`
- 합성 테스트: `tests/test_measurement_harmonization.py`
- 감사 테스트: `tests/test_audit_measurement_harmonization.py`

## 7. 로컬 실행 명령

```powershell
python -m src.audit_measurement_harmonization `
  --contract 'contracts/measurement_harmonization_contract_v0_1.yaml' `
  --year-file '2019=C:\Users\FORYOUCOM\PycharmProjects\AH_05_01\data\01_raw\knhanes\year=2019\hn19_all.csv' `
  --year-file '2020=C:\Users\FORYOUCOM\PycharmProjects\AH_05_01\data\01_raw\knhanes\year=2020\hn20_all.csv' `
  --year-file '2021=C:\Users\FORYOUCOM\PycharmProjects\AH_05_01\data\01_raw\knhanes\year=2021\hn21_all.csv' `
  --year-file '2022=C:\Users\FORYOUCOM\PycharmProjects\AH_05_01\data\01_raw\knhanes\year=2022\hn22_all.csv' `
  --output-json 'reports\measurement_harmonization\measurement_harmonization_2019_2022_v0_1.json' `
  --output-md 'reports\measurement_harmonization\measurement_harmonization_2019_2022_v0_1.md'
```

이 명령은 집계 결과만 생성하며 행·ID·표본 예시는 출력하지 않는다.

## 8. 실행 후 판정 항목

1. 원자료 SHA-256이 기존 값 감사 자료와 동일한가?
2. 혈압 2·3차 평균과 공식 최종값의 불일치가 0건인가?
3. HbA1c 전환으로 6.5% 기준 및 당뇨 측정 라벨이 얼마나 재분류되는가?
4. 혈압 전환으로 140/90 기준 라벨이 얼마나 재분류되는가?
5. 전환값의 결측·비유한값이 새로 발생하는가?
6. 80세 top-code 대상 규모가 혈압 민감도 해석에 기록됐는가?
7. 결과에 참가자 행·ID가 없고 모델 학습·평가가 수행되지 않았는가?

## 9. 팀 공동 확인 필요

다음 사항은 집계 결과 확인 후 병학님과 함께 승인한다.

- 공식 원측정값을 P0 주 분석으로 유지
- 연구자 전환값을 민감도 분석으로만 유지
- 모델 카드에 2021년 혈압계 및 2022년 HbA1c 분석기관 변경을 명시
- 전환 민감도 결과를 공개 성능이 아니라 측정체계 제한으로 보고

이 공동 승인이 끝나기 전까지 `do_not_train: true`를 유지한다.
