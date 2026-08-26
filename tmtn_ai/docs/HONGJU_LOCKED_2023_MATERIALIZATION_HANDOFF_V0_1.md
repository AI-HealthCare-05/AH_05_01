# 홍주님 전달용: KNHANES 2023 잠금 테스트 생성 절차 v0.1

## 1. 담당 범위

홍주님은 KNHANES 2023 원자료 custodian으로서 다음 작업을 담당한다.

1. 2023 원자료에서 frozen canonical 규칙 실행
2. 허리둘레·당뇨·고혈압 task cohort 생성
3. 개발자용 피처와 제한 정답키 분리
4. 정답키 접근 제한 및 무결성 manifest 보관
5. 추후 동결 예측에 대한 1회 평가

강호님과 병학님은 2023 라벨 파일, 라벨 분포, 양성 수를 확인하지 않는다.

## 2. 전달할 코드

개별 파일을 복사하기보다 `tmtn_ai` 프로젝트 전체를 같은 버전으로 전달한다. 핵심 파일은 다음과 같다.

- `contracts/locked_2023_materialization_contract_v0_1.yaml`
- `src/canonical_etl.py`
- `src/locked_2023_canonical.py`
- `src/materialize_locked_2023.py`
- `src/activity_features.py`
- `src/measurement_harmonization.py`
- `tests/test_materialize_locked_2023.py`

`src/canonical_etl.py`는 2019~2022 감사 시점의 frozen 구현이므로 수정하지 않는다. 2023 연도 확장은 별도 모듈에서만 수행한다.

## 3. 먼저 수행할 안전 검증

프로젝트 루트에서 가상환경과 의존성을 준비한 뒤 합성 테스트만 실행한다.

```powershell
python -m pip install -r requirements.txt
python -m pytest tests/test_materialize_locked_2023.py -q
```

이 테스트는 합성 데이터만 사용하며 hn23 원자료를 읽지 않는다.

현재 제공 계약에는 `do_not_materialize: true`가 설정돼 있어 실제 명령을 실행해도 원자료를 읽기 전에 중단된다. 같은 계약 파일을 직접 수정해 잠금을 해제하지 않는다.

## 4. 실제 실행 전 홍주님 확인사항

- [ ] 개발자 전달 경로 확정
- [ ] 정답키 제한 경로 확정
- [ ] 두 경로가 같거나 포함 관계가 아님
- [ ] 정답키 경로는 홍주님만 읽을 수 있음
- [ ] 개발자는 정답키 경로를 열 수 없음
- [ ] hn23 파일의 형식과 실제 경로 확인
- [ ] 합성 테스트 통과
- [ ] 코드·계약 검토 완료 의사 전달

위 확인을 받은 뒤 데이터·AI팀이 새로운 승인 계약 버전을 생성한다. 새 버전에서만 `do_not_materialize: false`를 적용한다.

## 5. 승인 후 실행 명령 형태

아래 명령의 계약 파일은 아직 생성되지 않은 승인 버전 예시다. 현재 v0.1 검토 계약으로 실행하지 않는다.

```powershell
python -m src.materialize_locked_2023 `
  --contract 'contracts/locked_2023_materialization_contract_v0_2_authorized.yaml' `
  --raw-2023 'D:\실제경로\hn23_all.csv' `
  --developer-output 'D:\tmtn\locked_2023_developer' `
  --locked-label-output 'E:\restricted\tmtn_locked_2023_labels'
```

CSV 외 지원 형식은 기존 원자료 판독기를 따르며, 실제 파일 형식에 맞게 경로를 지정한다.

## 6. 생성되는 파일

개발자 전달 경로:

```text
locked_2023_canonical_v0_2/
├─ waist/test_2023_features.csv
├─ diabetes/test_2023_features.csv
├─ hypertension/test_2023_features.csv
├─ developer_manifest.json
└─ contract_snapshot.yaml
```

홍주님 제한 경로:

```text
locked_2023_canonical_v0_2/
├─ waist/test_2023_labels.csv
├─ diabetes/test_2023_labels.csv
├─ hypertension/test_2023_labels.csv
├─ locked_manifest.json
└─ contract_snapshot.yaml
```

개발자용 파일에는 라벨이 없고, 제한 파일에는 복합키와 해당 task 라벨만 있다. 전체 canonical 행 파일은 생성하지 않는다.

## 7. 실행 후 인계

홍주님은 다음 정보만 데이터·AI팀에 전달한다.

- materialization 성공 여부
- `developer_manifest.json`
- 개발자용 피처 패키지
- 피처 패키지 SHA-256

다음 정보는 모델 동결 전 전달하지 않는다.

- 정답키 파일
- `locked_manifest.json`의 정답 파일 상세
- 라벨 분포·양성 수·평균
- 2023 성능 결과

정답키는 홍주님 제한 경로에 그대로 보관한다. 이메일·메신저·Notion에 첨부하지 않는다.

## 8. 평가 시점

강호님과 병학님이 모델, 피처, 전처리, 결측 처리, calibration, 임계값을 동결한 뒤 task별 2023 예측 파일을 홍주님에게 전달한다. 홍주님은 정답키와 결합해 사전 정의된 지표를 한 번 계산한다.

결과 확인 후 2023에 맞춰 재튜닝하면 그 이후 2023 결과는 잠금 테스트 성능으로 사용할 수 없다.
