# KNHANES 측정값 조화 영향 감사

> 집계 전용 결과 — 행·ID 미포함, 모델 학습·성능평가 미수행

- 구현 버전: `v0.1-aggregate-only`
- 감사 연도: 2019, 2020, 2021, 2022
- 주 분석 정책: 공식 원측정값
- 연구자 전환값: 민감도 분석 전용

## 2019

- 당뇨 측정 cohort: 5914
- HbA1c 전환 적용: False
- HbA1c 기준 재분류: {'comparable_count': 5914, 'changed_count': 0, 'agreement_rate': 1.0, 'cells': [{'value': '0_to_0', 'count': 5285, 'suppressed': False}, {'value': '1_to_1', 'count': 629, 'suppressed': False}, {'value': 'not_comparable', 'count': 2196, 'suppressed': False}]}
- 당뇨 측정 라벨 재분류: {'comparable_count': 5914, 'changed_count': 0, 'agreement_rate': 1.0, 'cells': [{'value': '0_to_0', 'count': 5185, 'suppressed': False}, {'value': '1_to_1', 'count': 729, 'suppressed': False}, {'value': 'not_comparable', 'count': 2196, 'suppressed': False}]}
- 혈압 측정 cohort: 6233
- 혈압 전환 적용: False
- 수축기 공식 평균 재현: {'tolerance_mmHg': 0.1, 'comparable_count': 6922, 'mismatch_count': 0, 'match_rate': 1.0, 'maximum_absolute_error': 0.0}
- 이완기 공식 평균 재현: {'tolerance_mmHg': 0.1, 'comparable_count': 6922, 'mismatch_count': 0, 'match_rate': 1.0, 'maximum_absolute_error': 0.0}
- 고혈압 측정 라벨 재분류: {'comparable_count': 6233, 'changed_count': 0, 'agreement_rate': 1.0, 'cells': [{'value': '0_to_0', 'count': 5196, 'suppressed': False}, {'value': '1_to_1', 'count': 1037, 'suppressed': False}, {'value': 'not_comparable', 'count': 1877, 'suppressed': False}]}

## 2020

- 당뇨 측정 cohort: 5615
- HbA1c 전환 적용: False
- HbA1c 기준 재분류: {'comparable_count': 5615, 'changed_count': 0, 'agreement_rate': 1.0, 'cells': [{'value': '0_to_0', 'count': 4903, 'suppressed': False}, {'value': '1_to_1', 'count': 712, 'suppressed': False}, {'value': 'not_comparable', 'count': 1744, 'suppressed': False}]}
- 당뇨 측정 라벨 재분류: {'comparable_count': 5615, 'changed_count': 0, 'agreement_rate': 1.0, 'cells': [{'value': '0_to_0', 'count': 4823, 'suppressed': False}, {'value': '1_to_1', 'count': 792, 'suppressed': False}, {'value': 'not_comparable', 'count': 1744, 'suppressed': False}]}
- 혈압 측정 cohort: 5802
- 혈압 전환 적용: False
- 수축기 공식 평균 재현: {'tolerance_mmHg': 0.1, 'comparable_count': 6557, 'mismatch_count': 0, 'match_rate': 1.0, 'maximum_absolute_error': 0.0}
- 이완기 공식 평균 재현: {'tolerance_mmHg': 0.1, 'comparable_count': 6557, 'mismatch_count': 0, 'match_rate': 1.0, 'maximum_absolute_error': 0.0}
- 고혈압 측정 라벨 재분류: {'comparable_count': 5802, 'changed_count': 0, 'agreement_rate': 1.0, 'cells': [{'value': '0_to_0', 'count': 4854, 'suppressed': False}, {'value': '1_to_1', 'count': 948, 'suppressed': False}, {'value': 'not_comparable', 'count': 1557, 'suppressed': False}]}

## 2021

- 당뇨 측정 cohort: 5351
- HbA1c 전환 적용: False
- HbA1c 기준 재분류: {'comparable_count': 5351, 'changed_count': 0, 'agreement_rate': 1.0, 'cells': [{'value': '0_to_0', 'count': 4675, 'suppressed': False}, {'value': '1_to_1', 'count': 676, 'suppressed': False}, {'value': 'not_comparable', 'count': 1739, 'suppressed': False}]}
- 당뇨 측정 라벨 재분류: {'comparable_count': 5351, 'changed_count': 0, 'agreement_rate': 1.0, 'cells': [{'value': '0_to_0', 'count': 4593, 'suppressed': False}, {'value': '1_to_1', 'count': 758, 'suppressed': False}, {'value': 'not_comparable', 'count': 1739, 'suppressed': False}]}
- 혈압 측정 cohort: 5584
- 혈압 전환 적용: True
- 수축기 공식 평균 재현: {'tolerance_mmHg': 0.1, 'comparable_count': 6308, 'mismatch_count': 0, 'match_rate': 1.0, 'maximum_absolute_error': 0.0}
- 이완기 공식 평균 재현: {'tolerance_mmHg': 0.1, 'comparable_count': 6308, 'mismatch_count': 0, 'match_rate': 1.0, 'maximum_absolute_error': 0.0}
- 고혈압 측정 라벨 재분류: {'comparable_count': 5584, 'changed_count': 274, 'agreement_rate': 0.9509, 'cells': [{'value': '0_to_0', 'count': 4534, 'suppressed': False}, {'value': '0_to_1', 'count': None, 'suppressed': True}, {'value': '1_to_0', 'count': None, 'suppressed': True}, {'value': '1_to_1', 'count': 776, 'suppressed': False}, {'value': 'not_comparable', 'count': 1506, 'suppressed': False}]}

## 2022

- 당뇨 측정 cohort: 5008
- HbA1c 전환 적용: True
- HbA1c 기준 재분류: {'comparable_count': 5008, 'changed_count': 66, 'agreement_rate': 0.9868, 'cells': [{'value': '0_to_0', 'count': 4505, 'suppressed': False}, {'value': '0_to_1', 'count': 66, 'suppressed': False}, {'value': '1_to_1', 'count': 437, 'suppressed': False}, {'value': 'not_comparable', 'count': 1257, 'suppressed': False}]}
- 당뇨 측정 라벨 재분류: {'comparable_count': 5008, 'changed_count': 45, 'agreement_rate': 0.991, 'cells': [{'value': '0_to_0', 'count': 4413, 'suppressed': False}, {'value': '0_to_1', 'count': 45, 'suppressed': False}, {'value': '1_to_1', 'count': 550, 'suppressed': False}, {'value': 'not_comparable', 'count': 1257, 'suppressed': False}]}
- 혈압 측정 cohort: 5186
- 혈압 전환 적용: True
- 수축기 공식 평균 재현: {'tolerance_mmHg': 0.1, 'comparable_count': 5896, 'mismatch_count': 0, 'match_rate': 1.0, 'maximum_absolute_error': 0.0}
- 이완기 공식 평균 재현: {'tolerance_mmHg': 0.1, 'comparable_count': 5896, 'mismatch_count': 0, 'match_rate': 1.0, 'maximum_absolute_error': 0.0}
- 고혈압 측정 라벨 재분류: {'comparable_count': 5186, 'changed_count': 280, 'agreement_rate': 0.946, 'cells': [{'value': '0_to_0', 'count': 4186, 'suppressed': False}, {'value': '0_to_1', 'count': 280, 'suppressed': False}, {'value': '1_to_1', 'count': 720, 'suppressed': False}, {'value': 'not_comparable', 'count': 1079, 'suppressed': False}]}

## 해석 제한

- 전환 후 분포가 이전 연도와 가까워진다는 이유만으로 정책을 선택하지 않는다.
- 혈압 전환식은 나이와 맥압을 포함하므로 주 라벨에 사용하지 않는다.
- 80세 이상은 원시 나이 80으로 top-coding되어 혈압 전환 민감도에 제한이 있다.
- 이 감사는 모델 성능 또는 임상 타당성 평가가 아니다.
- 2023·2024 값은 사용하지 않는다.
