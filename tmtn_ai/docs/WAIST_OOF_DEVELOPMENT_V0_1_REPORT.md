# TMTN 허리둘레 개발 OOF v0.1 결과

## 상태

- 실행일: 2026-08-26
- 상태: `DEVELOPMENT_OOF_COMPLETE`
- 사용 연도: 2019~2021만 사용
- 대상자 수: 16,403
- stability seed: 42, 1042, 2042
- outer/inner fold: 5 / 4
- 2022·2023·2024 접근: 없음
- 공식 2022 release gate 판정: 아직 수행하지 않음

## 입력 tier

- W0: 나이, 성별, 키, 몸무게
- W2: W0 + 여가 유산소 중강도 환산 분/주 + 근력운동 일수/주

## 개발 OOF 집계

| tier | MAE 평균 (범위) | RMSE 평균 (범위) | 5 cm 이내 평균 | 10 cm 이내 평균 | 절대 평균 잔차 평균 |
|---|---:|---:|---:|---:|---:|
| W0 | 2.9108 (2.9089~2.9128) | 3.7129 (3.7098~3.7165) | 83.52% | 99.03% | 0.0148 cm |
| W2 | 2.8643 (2.8619~2.8662) | 3.6434 (3.6417~3.6458) | 84.03% | 99.14% | 0.0044 cm |

W2는 세 seed 모두에서 W0보다 MAE가 0.045~0.047 cm 낮았다. MAE seed 표준편차는 W0 0.0020 cm, W2 0.0022 cm로 작았다.

## 후보 선택 안정성

W0/W2 × seed 3개 × outer fold 5개의 총 30개 선택에서 모두 다음 후보가 선택됐다.

```text
random_forest_regressor(
  n_estimators=500,
  max_depth=12,
  min_samples_leaf=5,
  max_features=0.7,
  criterion="squared_error",
  bootstrap=true,
  random_state=42
)
```

후보와 hyperparameter grid는 `contracts/model_experiment_protocol_v0_2.yaml`의 사전 승인 범위를 사용했다.

## 누설·재현성 검증

- task frame·fold registry 사전 검증: 91/91 PASS
- 전체 합성·회귀 테스트: 136 PASS
- outer holdout과 해당 inner-training key 교집합: 0
- 각 OOF 행은 해당 행이 포함되지 않은 outer-training 모델에서 예측
- ElasticNet scaler는 sklearn Pipeline 안에서 fold별 적합
- OOF key 결측·중복·라벨 정렬 오류 차단
- 2022·2024 혼입 fail-closed 테스트 통과
- 산출물 7개의 SHA-256 독립 재계산 일치

## 해석 제한

- 이 결과는 2019~2021 개발 OOF 결과이며 2022 temporal evaluation 결과가 아니다.
- protocol의 `release_gate_2022` 수치와 비교해 공개 통과를 선언하지 않는다.
- Rao-Wu 95% 신뢰구간, 정식 하위집단 gate, survey-weighted sensitivity는 아직 수행하지 않았다.
- W2의 개선 폭은 일관되지만 작으므로 D1 채택 여부는 질환모델의 matched D0 비교와 calibration 기준까지 통과한 후 판단한다.
- 허리둘레 OOF CSV는 참여자 key를 포함하므로 Git에 올리지 않는다.

## 로컬 제한 산출물

```text
artifacts/model_development_v0_1/waist/
├─ oof/W0_seed{42,1042,2042}.csv
├─ oof/W2_seed{42,1042,2042}.csv
├─ waist_oof_results.json
└─ manifest_sha256.json
```

행 단위 OOF, 실행 로그, 향후 모델 및 calibration artifact는 `.gitignore`의 `artifacts/` 규칙으로 제외한다.

## 다음 단계

1. 본 결과와 실행 영수증을 Git 체크포인트로 고정
2. D0 당뇨·고혈압 nested OOF 파이프라인 구현
3. D1 aligned nested stacking 안전성 검토
4. 안전성이 증명된 경우에만 D1 구현
5. matched D0·F0·calibration·하위집단·가중 sensitivity 진행

