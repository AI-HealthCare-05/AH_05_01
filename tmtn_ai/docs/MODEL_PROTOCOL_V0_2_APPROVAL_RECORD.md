# TMTN 공식 모델 실험 프로토콜 v0.2 공동 승인 기록

## 결론

`MODEL_EXPERIMENT_PROTOCOL_V0_2`의 방법론 기준과 금지 원칙을 박강호·병학 공동 기준으로 승인한다.

## 승인 기록

- 박강호: 2026-08-25 승인
- 병학: 2026-08-26 승인
- 병학 승인 근거 메시지: “수정할 것이 없으며 기준과 금지 원칙을 그대로 적용”한다는 취지의 팀 메시지
- 승인 대상: `contracts/model_experiment_protocol_v0_2.yaml`
- 최초 canonical 생성 config: `configs/model_development_v0_1.yaml`
- task frame 이후 실행 config: `configs/model_development_v0_2.yaml`
- v0.2 변경 사유: canonical이 eligibility 밖 실측 허리둘레를 보존하는 계약을 task 생성기에 정확히 반영

## 승인 범위

- 2019~2021 개발·nested cross-fitting·후보 및 hyperparameter 선택
- 개발 OOF만 이용한 calibration 적합
- 고정된 6개 피처 및 task별 eligibility·라벨 규칙
- 사전 합의된 모델 후보, grid, 통과 기준, 하위집단·가중치·신뢰구간 정책

## 승인 범위 밖

- 2022 열람·적합·선택·반복 평가
- 개발자의 2023 행 단위 자료 열람
- 2024 접근
- 종합 틈틈지수 공식의 임시 생성 또는 공개
- 이 검토 대화에서 실제 KNHANES 학습·성능 평가

2022는 전체 파이프라인과 모델·전처리·calibration을 동결한 뒤 한 번만 평가한다. 2023은 홍주 custodian 환경에서 동결 번들을 실행하고 사전 선언된 집계 성능만 공유한다.
