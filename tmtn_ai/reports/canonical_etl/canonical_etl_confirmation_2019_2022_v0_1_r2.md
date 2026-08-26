# KNHANES canonical ETL 확인 감사

> 집계 전용 결과 — canonical 행·ID 미출력, 분할·학습·성능평가 미수행

- 구현 버전: `v0.1-r2-aggregate-only`
- 감사 연도: 2019, 2020, 2021, 2022
- 모델 입력 allowlist와 금지목록 중복: 없음

## 2019

- 입력/출력 행 수: 8110 / 8110
- 행 보존: True
- ID 품질: {'missing_count': 0, 'duplicate_after_first_count': 0}
- P0 일반 성인: {'count': 6267, 'rate': 0.7727}
- F0 완전성: {'count': 6232, 'rate': 0.7684}
- F1 완전성: {'count': 5867, 'rate': 0.7234}
- F2-leisure 완전성: {'count': 5876, 'rate': 0.7245}
- 허리둘레 타깃: {'eligible': {'count': 6240, 'rate': 0.7694}}
- 당뇨 원측정 라벨: {'eligible_count': 5914, 'positive_count': 729, 'positive_rate_among_eligible': 0.1233, 'nonmissing_label_outside_eligibility_count': 0}
- 고혈압 원측정 라벨: {'eligible_count': 6233, 'positive_count': 1037, 'positive_rate_among_eligible': 0.1664, 'nonmissing_label_outside_eligibility_count': 0}

## 2020

- 입력/출력 행 수: 7359 / 7359
- 행 보존: True
- ID 품질: {'missing_count': 0, 'duplicate_after_first_count': 0}
- P0 일반 성인: {'count': 5911, 'rate': 0.8032}
- F0 완전성: {'count': 5815, 'rate': 0.7902}
- F1 완전성: {'count': 5324, 'rate': 0.7235}
- F2-leisure 완전성: {'count': 5331, 'rate': 0.7244}
- 허리둘레 타깃: {'eligible': {'count': 5874, 'rate': 0.7982}}
- 당뇨 원측정 라벨: {'eligible_count': 5615, 'positive_count': 792, 'positive_rate_among_eligible': 0.1411, 'nonmissing_label_outside_eligibility_count': 0}
- 고혈압 원측정 라벨: {'eligible_count': 5802, 'positive_count': 948, 'positive_rate_among_eligible': 0.1634, 'nonmissing_label_outside_eligibility_count': 0}

## 2021

- 입력/출력 행 수: 7090 / 7090
- 행 보존: True
- ID 품질: {'missing_count': 0, 'duplicate_after_first_count': 0}
- P0 일반 성인: {'count': 5674, 'rate': 0.8003}
- F0 완전성: {'count': 5574, 'rate': 0.7862}
- F1 완전성: {'count': 5224, 'rate': 0.7368}
- F2-leisure 완전성: {'count': 5234, 'rate': 0.7382}
- 허리둘레 타깃: {'eligible': {'count': 5618, 'rate': 0.7924}}
- 당뇨 원측정 라벨: {'eligible_count': 5351, 'positive_count': 758, 'positive_rate_among_eligible': 0.1417, 'nonmissing_label_outside_eligibility_count': 0}
- 고혈압 원측정 라벨: {'eligible_count': 5584, 'positive_count': 777, 'positive_rate_among_eligible': 0.1391, 'nonmissing_label_outside_eligibility_count': 0}

## 2022

- 입력/출력 행 수: 6265 / 6265
- 행 보존: True
- ID 품질: {'missing_count': 0, 'duplicate_after_first_count': 0}
- P0 일반 성인: {'count': 5304, 'rate': 0.8466}
- F0 완전성: {'count': 5197, 'rate': 0.8295}
- F1 완전성: {'count': 4763, 'rate': 0.7603}
- F2-leisure 완전성: {'count': 4772, 'rate': 0.7617}
- 허리둘레 타깃: {'eligible': {'count': 5070, 'rate': 0.8093}}
- 당뇨 원측정 라벨: {'eligible_count': 5008, 'positive_count': 550, 'positive_rate_among_eligible': 0.1098, 'nonmissing_label_outside_eligibility_count': 0}
- 고혈압 원측정 라벨: {'eligible_count': 5186, 'positive_count': 720, 'positive_rate_among_eligible': 0.1388, 'nonmissing_label_outside_eligibility_count': 0}

## 제한

- 후보 유효범위 위반은 값을 보존하고 사유 플래그만 남긴다. 자동 제외 기준이 아니다.
- 결측 대치·스케일링·피처 선택은 train-only 파이프라인으로 연기한다.
- 이 보고서의 비가중 비율은 유병률이 아니다.
- 2023·2024 값은 사용하지 않는다.
