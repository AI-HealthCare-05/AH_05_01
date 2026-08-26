# KNHANES 2019~2023 값 수준 감사 계획

> 계획 버전: v0.1  
> 작성일: 2026-08-24  
> 기준 계약: `contracts/data_contract_v0_3.yaml`  
> 감사 대상: 2019~2023 개발·검증·잠금 테스트 원자료  
> 제외 대상: 2024 participant value 분석  
> 학습 상태: `do_not_train: true`

## 1. 목적

6개년 스키마 감사와 공식 코드북 대조로 컬럼의 존재와 의미는 확인했다. 다음 단계에서는 2019~2023 participant value만 읽어 변수별 코드, 결측, 범위, skip logic, 라벨 생성 가능 cohort와 복합표본 설계값을 확인한다.

이 감사는 모델 성능 탐색이 아니다. 피처 선택, 상관분석, 모델 학습, calibration과 2024 평가는 수행하지 않는다.

## 2. 입력과 출력

### 입력

- `contracts/data_contract_v0_3.yaml`
- 향후 작성할 `contracts/value_audit_spec_v0_1.yaml`
- 2019~2023 KNHANES 원자료
- 제8기·제9기 공식 원시자료 이용지침서

### 코드 산출물

```text
contracts/value_audit_spec_v0_1.yaml
src/audit_raw_values.py
tests/test_audit_raw_values.py
```

### 로컬 실행 산출물

```text
reports/value_audit_2019_2023_v0_1.json
reports/value_audit_2019_2023_v0_1.md
```

보고서에는 원시 개인 행, 직접 식별자, 전체 행 예시를 포함하지 않는다. 최소 셀 크기 기준은 실행 전 별도로 확정한다.

## 3. 감사 경계

### 허용

- 변수별 빈도·결측·범위
- 연도별 cohort 수
- 공식 파생변수와 재구성 파생변수의 일치도
- 임신·공복·검사 가능 여부에 따른 제외 수
- 혈압 회차와 공식 평균의 일치도
- 복합표본 설계변수 유효성

### 금지

- 2024 participant value 열람·요약
- 피처와 라벨의 예측 상관 탐색
- 모델 적합 또는 성능 계산
- 결측 대치기의 fit
- 2022·2023 결과를 이용한 모델 선택
- train·validation·test CSV 생성

## 4. 공통 식별·연도 감사

| 감사 항목 | 확인 내용 | 실패 조건 |
|---|---|---|
| 연도 | 파일에 기대 연도만 존재 | 다른 연도 혼입 |
| ID | 결측·중복·연도 간 재등장 | 계약되지 않은 중복 |
| 연령 | 만 19세 이상 cohort 생성 가능 | 비정상 코드·단위 불명 |
| 성별 | KNHANES 코드별 빈도·결측 | 코드북 밖 값 |
| 행 수 | 원자료와 단계별 제외 수 | 이유 없는 행 손실 |

ID는 감사와 추후 누설 검사에 필요한 최소 형태로만 사용하며 보고서에 원시 ID를 출력하지 않는다.

## 5. 임신 및 eligibility 감사

### 대상 변수

- `HE_prg`
- `HE_dprg`

### 확인 항목

- 연도별 허용 코드와 라벨
- 구조적 비해당, 무응답, 순수 결측 구분
- 임신 여부와 임신 개월의 논리적 일관성
- 남성 또는 비대상 응답에서의 구조적 비해당 패턴
- 임신 여부를 확정할 수 없는 대상자 수
- 임신 제외 전후 허리둘레·당뇨·고혈압 cohort 수

### 계약해야 할 결과

```text
pregnancy_excluded = true인 명시적 코드
pregnancy_unknown = 임신 여부를 확정할 수 없는 코드 또는 모순 조합
```

P0 일반 성인 모델은 `pregnancy_excluded`와 `pregnancy_unknown`을 모두 학습 cohort에서 제외하는 것을 기본 후보로 검토한다. 최종 구현식은 감사 결과 후 v1.0 계약에서 고정한다.

## 6. 신체계측 감사

### 대상 변수

- `HE_ht`
- `HE_wt`
- `HE_wc`
- `HE_BMI`

### 확인 항목

- 단위와 소수점 정밀도
- 결측·측정 실패·특수코드
- 연도별 최소·최대·분위수
- 생리적으로 불가능하거나 단위 오류가 의심되는 값
- `HE_BMI`와 키·몸무게 재계산 BMI의 오차
- 임신 제외 전후 허리둘레 가용률

유효범위는 관측 최소·최대만 보고 정하지 않는다. 코드북, 측정 프로토콜과 사전 임상 범위를 함께 검토해 계약한다.

## 7. 여가 유산소 및 근력운동 감사

### 대상 변수

- 여가 고강도: `BE3_75`, `BE3_76`, `BE3_77`, `BE3_78`
- 여가 중강도: `BE3_85`, `BE3_86`, `BE3_87`, `BE3_88`
- 근력운동: `BE5_1`
- 공식 유산소 충족: `pa_aerobic`

### 문항별 확인

- 참여 여부 코드
- 주당 일수
- 하루 또는 회당 시간·분
- 구조적 비해당과 무응답
- 일수 0인데 시간이 존재하는 조합
- 참여 안 함인데 일수·시간이 존재하는 조합
- 시간과 분의 불가능 조합
- 하루 24시간 또는 주 7일을 넘는 논리 오류
- 연도별 문항 대상과 코드 변화

### 파생값 후보

```text
leisure_moderate_min_week
  = moderate_days_week * moderate_minutes_day

leisure_vigorous_min_week
  = vigorous_days_week * vigorous_minutes_day

leisure_aerobic_moderate_equivalent_min_week
  = leisure_moderate_min_week
  + 2 * leisure_vigorous_min_week
```

실제 구현은 공식 branch-aware skip logic을 따라야 하며, 일반적인 `fillna(0)`로 만들지 않는다.

### 재현 검사

- 공식 SAS 방식으로 `pa_aerobic`을 재구성한다.
- 원자료 `pa_aerobic`과 행 수준 일치율을 연도별로 보고한다.
- 불일치 행은 원시 ID 대신 불일치 사유 범주와 개수만 보고한다.

## 8. 당뇨 측정 이상 감사

### 대상 변수

- `HE_fst`
- `HE_glu`
- `HE_HbA1c`
- comparator `HE_DM_HbA1c`
- 진단·치료 구성 변수는 comparator 재현 확인에만 사용

### Primary cohort 후보

```text
age >= 19
AND nonpregnant
AND HE_fst >= 8
AND HE_glu available
AND HE_HbA1c available
```

### Primary positive 후보

```text
HE_glu >= 126
OR HE_HbA1c >= 6.5
```

### 확인 항목

- 공복시간 코드·단위·결측
- 검사값 측정 가능 범위·특수코드
- 공복혈당과 HbA1c 동시 가용률
- eligibility 단계별 제외 수
- primary 양성률의 연도별·성별·연령군별 기술 통계
- 치료 중 정상 측정자 규모
- primary와 공식 comparator의 2x2 일치표
- 작은 셀 비공개 기준 준수

### HbA1c 조화

2022년 이후 원자료와 연구자 전환식 적용값을 모두 생성하되, 모델링하지 않고 다음만 비교한다.

- 평균·분포 이동
- `6.5%` 경계 통과 인원 변화
- 연도별 primary 양성률 변화

어느 값을 채택할지는 감사 결과와 공식통계 정책을 함께 검토한 뒤 고정한다.

## 9. 고혈압 측정 이상 감사

### 대상 변수

- `HE_sbp`, `HE_dbp`
- `HE_sbp1`, `HE_sbp2`, `HE_sbp3`
- `HE_dbp1`, `HE_dbp2`, `HE_dbp3`
- comparator `HE_HP`

### Primary positive 후보

```text
HE_sbp >= 140
OR HE_dbp >= 90
```

### 확인 항목

- 회차별 측정값 결측·범위·특수코드
- 2·3차 평균과 공식 `HE_sbp`, `HE_dbp`의 허용 오차 내 일치도
- 측정 실패 또는 불완전 회차 처리
- 연도별 혈압계 변경 전후 분포 이동
- primary 양성률의 연도별·성별·연령군별 기술 통계
- 치료 중 정상 측정자 규모
- primary와 공식 comparator의 2x2 일치표

연구자 전환값은 원자료와 별도 컬럼으로 생성하며 중복 변환을 금지한다. 채택 여부는 연도 안정성 비교 후 고정한다.

## 10. 복합표본 설계 감사

### 대상 변수

- `wt_itvex`
- `kstrata`
- `psu`

### 확인 항목

- 결측, 0, 음수 가중치
- 연도별 가중치 범위
- 층화·PSU 코드 가용성
- cohort 필터 후 singleton 또는 작은 설계 셀
- 연도 통합 분석 시 가중치 처리 정책에 필요한 정보

모델 평가는 향후 가중·비가중 결과를 함께 보고하지만, 어느 결과를 주 지표로 사용할지는 공개 게이트 전에 별도 승인한다.

## 11. 보고서 최소 구성

```text
1. 실행 환경·코드·계약·입력 해시
2. 연도별 원자료 행 수
3. 변수별 dtype·허용 코드·관측 코드
4. 결측·구조적 비해당·무응답 비율
5. 범위 및 논리 오류 수
6. 임신 제외 흐름
7. 운동 skip logic 및 pa_aerobic 재현 결과
8. 당뇨 cohort·라벨 생성 흐름
9. 고혈압 cohort·라벨 생성 흐름
10. HbA1c·혈압 조화 민감도 기술 통계
11. 복합표본 설계변수 감사
12. 계약 변경이 필요한 미결정 목록
```

## 12. 역할

### 병학님

- 원시변수 코드·문항 skip logic 검토
- 임신·운동·공복·혈압 회차의 canonical 변환 초안
- 공식 생성변수 재현 규칙 검토
- 복합표본 설계변수 감사 기준 검토

### 강호

- 감사 사양과 자동 실패 조건 설계
- 모델 입력·출력 의미와 누설 경계 검토
- F0/F1/F2 및 D0/D1 비교에 필요한 감사 항목 검토
- 보고서 manifest·재현성 규칙 검토

### 공동

- 특수코드·유효범위·제외 기준 승인
- HbA1c·혈압 조화 정책 결정
- 값 수준 감사 결과와 `data_contract_v1.0` 승인

## 13. 완료 조건

- [ ] 2019~2023 모든 대상 파일 감사 성공
- [ ] 문항별 특수코드와 구조적 비해당 규칙 확정
- [ ] 임신 제외 구현식 확정
- [ ] 운동 파생식과 `pa_aerobic` 재현 결과 승인
- [ ] 당뇨·고혈압 primary cohort 흐름 승인
- [ ] HbA1c·혈압 조화 정책 확정 또는 명시적 보류
- [ ] 복합표본 설계값 검증
- [ ] 입력·계약·코드·출력 해시 기록
- [ ] 강호·병학 공동 검토 완료

완료 후에도 자동으로 학습이 허용되지는 않는다. 감사 결과를 반영한 `data_contract_v1.0`을 별도로 승인하고 `do_not_train`을 명시적으로 해제해야 한다.

