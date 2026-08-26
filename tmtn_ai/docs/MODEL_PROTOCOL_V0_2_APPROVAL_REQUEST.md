# TMTN 공식 모델 실험 프로토콜 v0.2 승인 요청

> 상태: 2026-08-26 병학님 전체 승인 회신 완료. 이 문서는 승인 요청 당시 기록이며, 최종 기록은 `MODEL_PROTOCOL_V0_2_APPROVAL_RECORD.md`를 따른다.

대상: 병학님  
요청일: 2026-08-25  
회신 목표: 2026-08-26  
상세 문서: `docs/MODEL_EXPERIMENT_PROTOCOL_V0_2.md`  
기계 판독 계약: `contracts/model_experiment_protocol_v0_2.yaml`

## 요청 배경

프로젝트 종료일이 2026-09-22이므로 미결정 방법론을 계속 검토 상태로 두지 않고, 문헌·KNHANES 공식 지침·팀의 기존 합의를 반영한 기본값으로 동결하려고 합니다. 실제 결과를 본 뒤 기준을 유리하게 바꾸지 않기 위해 학습 전에 승인합니다.

## 승인 요청 결정값

1. 허리둘레 후보: Linear Regression, ElasticNet, RandomForest의 사전 고정 grid
2. 질환 후보: Logistic Regression, RandomForest의 class weight 유·무와 development OOF Platt 보정
3. 허리둘레 gate: MAE 4.0 cm, MAE 95% CI 상한 4.5 cm, RMSE 5.5 cm
4. 질환 gate: ROC-AUC 0.65, 95% CI 하한 0.60, calibration slope 0.80~1.20, intercept ±0.10, ECE 0.05
5. 하위집단 정식 평가: event 100 이상 및 non-event 100 이상
6. 가중치 결측: 대치 금지, 가중 분석에서만 제외, coverage 95% 미만 경고
7. 2019~2021 통합 가중치: `wt_itvex / 3`
8. 95% CI: `kstrata` 내 PSU 단위 Rao-Wu bootstrap 2,000회
9. 앱 입력: 나이 80+ top-code, 키 100~220 cm, 몸무게 25~250 kg, 유산소 0~7일·10~960분, 근력 `0/1/2/3/4/5_plus`
10. 2022: 완전 동결 후 단 한 번 평가 전용
11. 2023: `opened_internal_benchmark`, 홍주님 환경에서만 행 단위 처리
12. 2024: 접근 금지

## 회신 방법

다음 중 하나로 회신 부탁드립니다.

- `v0.2 전체 승인합니다.`
- `v0.2 수정 요청: 항목 번호 / 현재값 / 제안값 / 이유`

전체 승인 전에는 `do_not_train: true`를 유지합니다. 승인이 완료되어 별도 authorized config와 체크섬을 `configs/model_development_v0_1.yaml`에 생성했습니다.
