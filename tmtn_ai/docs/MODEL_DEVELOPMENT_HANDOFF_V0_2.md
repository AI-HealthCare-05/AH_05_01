# TMTN 2019~2021 공식 모델개발 handoff v0.2

## 상태

- 작성일: 2026-08-26
- 상태: `READY_FOR_DEVELOPMENT_ONLY_MODELING`
- 실제 모델 학습·성능평가: 아직 미수행
- 허용 연도: 2019~2021
- 2022: 전체 동결 전 접근 금지
- 2023: 홍주 custodian 전용
- 2024: 접근 금지

## 승인 기준

- 실행 config: `configs/model_development_v0_2.yaml`
- config SHA-256: `ec65d546dad92b7ed169f0dc885e3dcf771a625567e4e62bd4806b821c209da9`
- protocol: `contracts/model_experiment_protocol_v0_2.yaml`
- canonical snapshot SHA-256: `dcfd76c8749469da2bd0432d497b23d8f3635ea42b85afcb821e7346b0474745`

## 개발 task frame

루트:

`C:\Users\FORYOUCOM\PycharmProjects\AH_05_01\data\05_cohorts\tmtn_development_frames\development_v0_2`

- manifest SHA-256: `57995724b49c30eff94564de55f6e51588f9fd59e4abedb8e579aeec790c6a0b`
- 허리둘레: 16,403행 / SHA-256 `f6677ef29ab58d848b9341cf5f22c05cf9f75738b5be8edc67dd1592cebf03ba`
- 당뇨: 15,720행 / SHA-256 `46c82d17cfe8ef87fcc4d0684f49ab17a8d29191ba87cade918ebc6a731aa854`
- 고혈압: 16,325행 / SHA-256 `fafbecbae9abde5ad07a3517f57415e83e28295400a5e02773bb817a9052aedd`

검증 완료 항목:

- 2019~2021만 포함
- 복합키 결측·중복 0
- 고정 6개 피처 결측 0
- 라벨 결측·비정상값 0
- 질환 task에 실측 허리둘레 없음
- survey weight·strata·PSU 결측 및 비양수 weight 0
- feature imputation·학습·평가 미수행

## nested fold registry

루트:

`C:\Users\FORYOUCOM\PycharmProjects\AH_05_01\data\06_splits\tmtn_nested_folds\development_v0_2`

- 허리둘레 manifest SHA-256: `a5050ae6d544462c733f7af99667e8f5cf10e0e77ceeeda2daa88043a40c9f2b`
- 당뇨 manifest SHA-256: `4849150e7e2562212fd9c7172e91f2dad6a0db82fec8bbc1237ac7df2cdd04dd`
- 고혈압 manifest SHA-256: `d619c676f879a70a36437588e9052933fc248a31680150c3daa4a2618b537016`

각 task에 대해 seed `42`, `1042`, `2042`를 생성했다.

- outer folds: 5
- inner folds: 4
- outer key: 정확히 한 번 배정
- inner `(key, outer_fold)`: 중복 없음
- 각 key: 자신이 holdout인 outer fold를 제외한 4개 outer-training registry에 포함
- 각 outer holdout과 해당 inner registry 교집합: 0
- outer holdout과 inner registry 합집합: 전체 task key와 일치
- 2022 이후 연도: 없음
- 동일 task frame·seed 재계산: 완전 일치
- 세 stability seed의 배정: 서로 다름

## 새 모델개발 채팅의 첫 작업

새 채팅은 행 단위 결과나 샘플을 답변에 출력하지 않고 다음 순서로 시작한다.

1. 이 handoff와 네 manifest의 SHA-256 재검증
2. config·protocol snapshot 검증
3. task frame과 fold registry 연결 해시 검증
4. 2022·2023·2024 경로·행 접근 차단 테스트
5. 승인된 모델 grid를 코드로 구현하고 합성 테스트 수행
6. 실제 2019~2021 nested OOF 학습 시작

모든 scaler·전처리는 fold 내부 학습자료에만 적합한다. D1의 `estimated_waist_cm`은 해당 행을 학습하지 않은 허리둘레 모델의 nested OOF 출력만 사용한다. 2022는 모델·전처리·calibration·평가 코드와 artifact를 완전히 동결한 뒤 별도 one-shot 절차에서만 연다.

## 중단 조건

- handoff 또는 manifest 해시 불일치
- 2022·2023·2024 파일이나 행 발견
- key·fold registry 불일치
- 실측 허리둘레의 질환모델 입력 포함
- fold 외부에서 적합된 전처리 발견
- 기존 산출물 덮어쓰기 필요
- 승인된 grid·gate 변경 필요

하나라도 해당하면 학습하지 않고 원인과 필요한 새 버전을 먼저 보고한다.
