# KNHANES 운동 피처 확인 감사

> 집계 전용 결과 — 행·ID 미포함, 모델 학습 미수행

- 구현 버전: `v0.1.1-aggregate-only-domain-checks`
- 감사 연도: 2019, 2020, 2021
- 누락 연도: 없음

## 2019

- 행 수: 8110
- pa_aerobic 비교 가능: 5918
- 불일치: 0
- 일치율: 1.0
- 전체 활동 완전성: {'complete_count': 5918, 'complete_rate': 0.7297}
- 여가 활동 완전성: {'complete_count': 5927, 'complete_rate': 0.7308}
- 영역별 완전성: {'work_vigorous': {'complete_count': 5927, 'complete_rate': 0.7308}, 'leisure_vigorous': {'complete_count': 5927, 'complete_rate': 0.7308}, 'work_moderate': {'complete_count': 5926, 'complete_rate': 0.7307}, 'leisure_moderate': {'complete_count': 5927, 'complete_rate': 0.7308}, 'transport_moderate': {'complete_count': 5920, 'complete_rate': 0.73}}
- 예상 밖 코드 확인 필요 컬럼: 없음

## 2020

- 행 수: 7359
- pa_aerobic 비교 가능: 5398
- 불일치: 0
- 일치율: 1.0
- 전체 활동 완전성: {'complete_count': 5398, 'complete_rate': 0.7335}
- 여가 활동 완전성: {'complete_count': 5409, 'complete_rate': 0.735}
- 영역별 완전성: {'work_vigorous': {'complete_count': 5411, 'complete_rate': 0.7353}, 'leisure_vigorous': {'complete_count': 5409, 'complete_rate': 0.735}, 'work_moderate': {'complete_count': 5410, 'complete_rate': 0.7352}, 'leisure_moderate': {'complete_count': 5409, 'complete_rate': 0.735}, 'transport_moderate': {'complete_count': 5401, 'complete_rate': 0.7339}}
- 예상 밖 코드 확인 필요 컬럼: 없음

## 2021

- 행 수: 7090
- pa_aerobic 비교 가능: 5316
- 불일치: 0
- 일치율: 1.0
- 전체 활동 완전성: {'complete_count': 5316, 'complete_rate': 0.7498}
- 여가 활동 완전성: {'complete_count': 5327, 'complete_rate': 0.7513}
- 영역별 완전성: {'work_vigorous': {'complete_count': 5331, 'complete_rate': 0.7519}, 'leisure_vigorous': {'complete_count': 5328, 'complete_rate': 0.7515}, 'work_moderate': {'complete_count': 5325, 'complete_rate': 0.7511}, 'leisure_moderate': {'complete_count': 5328, 'complete_rate': 0.7515}, 'transport_moderate': {'complete_count': 5321, 'complete_rate': 0.7505}}
- 예상 밖 코드 확인 필요 컬럼: 없음

## 제한

- 공식 pa_aerobic은 일·이동·여가 전체 활동 지표다.
- 여가 중강도환산분과 공식 pa_aerobic을 서로 대체하지 않는다.
- 이 결과로 모델 학습 또는 성능 평가를 수행하지 않는다.
- 2022·2023은 규칙 수정에 사용하지 않으며 2024는 접근하지 않는다.
