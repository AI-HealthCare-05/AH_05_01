# TMTN 허리둘레 개발 OOF v0.1 동결 기록

## 1. 동결 상태

- 동결일: 2026-08-26
- 상태: `DEVELOPMENT_WAIST_OOF_FROZEN`
- 범위: 2019~2021 nested OOF 개발 체크포인트
- 사용 연도: 2019~2021
- 2022·2023·2024 접근: 없음
- 공식 2022 release gate: 미수행
- production 모델 승인: 아님

이 기록은 허리둘레 서브모델의 개발 OOF 산출물을 후속 D0·D1 실험에서 재현 가능하게 사용하기 위한 동결 기록이다. 외부 공개 또는 제품 적용 승인을 의미하지 않는다.

## 2. 동결 결정

### 주 분석 후보

- 입력 tier: `W2`
- primary seed: `42`
- stability seeds: `42`, `1042`, `2042`
- 모델: `random_forest_regressor`
- hyperparameter:
  - `n_estimators: 500`
  - `max_depth: 12`
  - `min_samples_leaf: 5`
  - `max_features: 0.7`
  - `criterion: squared_error`
  - `bootstrap: true`
  - `random_state: 42`

W2는 세 stability seed 모두에서 W0보다 개발 OOF MAE가 낮았고, 30개 outer-fold 선택에서 동일한 후보가 선택됐다. 따라서 W2/seed 42를 D1 주 분석용 허리둘레 OOF 후보로 고정한다.

### 보존 후보

- `W0`: 운동 피처를 사용하지 않는 reference/sensitivity 후보로 보존
- `W2 seed 1042, 2042`: seed 안정성 및 D1 채택 규칙 검증용으로 보존
- W2 채택은 허리둘레 서브모델의 개발 후보 결정이며 질환모델 D1의 최종 채택을 의미하지 않는다.

## 3. 동결 산출물

제한 저장소에 다음 파일을 보존한다.

```text
artifacts/model_development_v0_1/waist/
├─ oof/W0_seed{42,1042,2042}.csv
├─ oof/W2_seed{42,1042,2042}.csv
├─ waist_oof_results.json
└─ manifest_sha256.json
```

- 행 단위 OOF CSV는 Git에 올리지 않는다.
- 파일별 SHA-256은 `WAIST_OOF_DEVELOPMENT_V0_1_RECEIPT.json`에 기록된 값을 사용한다.
- 기록된 8개 제한 산출물의 해시는 독립 재계산 결과와 일치했다.
- 기존 파일을 재실행으로 덮어쓰지 않는다. 변경이 필요하면 새 버전 디렉터리와 새 receipt를 생성한다.

## 4. 검증 근거

- task frame·fold registry 검증: `91/91 PASS`
- 전체 테스트: `136 PASS`
- 각 OOF 행은 해당 행을 학습하지 않은 outer-training 모델에서 생성
- outer holdout과 inner-training key 교집합: 0
- fold 밖 scaler·전처리 적합 차단
- OOF key 결측·중복·라벨 불일치 차단
- 2022·2024 혼입 fail-closed 검증 통과
- 실행 코드·config·protocol·산출물 SHA-256 기록 완료

상세 지표와 해석 제한은 `WAIST_OOF_DEVELOPMENT_V0_1_REPORT.md`, 실행 영수증과 해시는 `WAIST_OOF_DEVELOPMENT_V0_1_RECEIPT.json`을 기준으로 한다.

## 5. 후속 D0·D1 사용 규칙

1. D0 당뇨·고혈압 모델에는 `estimated_waist_cm`과 실측 `waist_cm`을 모두 입력하지 않는다.
2. D1에서만 `W2`의 nested cross-fitted `estimated_waist_cm`을 추가한다.
3. D1의 각 질환 outer holdout 행에는 해당 행을 학습하지 않은 허리둘레 모델의 예측만 연결한다.
4. 실측 `waist_cm`은 질환모델 입력으로 사용할 수 없다.
5. D1과 matched D0는 동일한 행·fold·seed에서 비교한다.
6. D1 최종 채택은 사전 승인된 Brier·calibration·2022 paired 비교 규칙을 모두 통과한 경우에만 가능하다.
7. D1이 통과하지 못하면 D0를 유지하고 허리둘레 추정값은 별도 참고 출력으로만 보존한다.

## 6. 변경 통제

다음 변경은 v0.1 동결 해제가 아니라 새 버전으로만 수행한다.

- 입력 tier 또는 피처 정의 변경
- 후보 모델 또는 hyperparameter grid 변경
- fold registry 또는 seed 변경
- OOF 재생성
- 결측 처리 변경
- 2022 평가 결과를 이용한 개발 재튜닝

새 버전은 변경 사유, 새 config·protocol snapshot, 새 테스트 결과, 새 artifact manifest와 SHA-256 receipt를 별도로 남겨야 한다.

