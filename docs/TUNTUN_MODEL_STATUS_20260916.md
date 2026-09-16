# N6 모델·실천 점수 연결 상태 — 2026-09-16

## 건강 모델은 동결본 유지

N6 허리 W2·당뇨 D0·고혈압 D0를 유지하며 근육량 모델은 사용하지 않는다. 이번 변경은 모델 재학습·재보정·모델 바이너리 교체가 아니다.

기준 릴리스 manifest SHA-256:
`bdca54917705cde75fc2d1b275c49a91ebfef05f56f45472d29e4b97ba77c2e9`

추론 소스 SHA-256:
`c3efa5a029020390873beee0fd486c64bd293561ad26cd81c35ba095c162c61d`

현재 저장소와 모델 담당 동결본의 위 파일 및 W2·D0 모델/보정기 해시가 일치한다. `tuntun_peer_bridge`의 manifest로 고정된 파일은 변경하지 않는다.

홍주님 수신 보고서의 2023 열린 내부 시간 벤치마크: 허리 가중 MAE 2.85cm(n=5,204), 당뇨 AUROC 0.776(n=5,033), 고혈압 AUROC 0.722(n=5,262). 허리 실측 <70cm는 평균 2.49cm 크게, 100+cm는 2.33cm 작게 예측했다. 당뇨 65–79세 AUROC 0.579, 고혈압 같은 연령 0.546으로 전체보다 낮다. 두 질환의 전체 공개 보정 구간에서 과대 예측 경향이 있다.

이는 당시 측정 양성 상태 평가이며 미래 발병·진단 적합성·새 틈튼지수 전체의 검증이 아니다. 원자료 및 개인별 예측은 이 변경에 포함하지 않는다. 고령층 표시 정책은 별도 검토 사항이다.

## 승인된 점수 정책과 이번 수정

- 건강 영역은 반올림 전 건강 방향 `peerPercentile`을 사용한다. 전체 10:20:20:50, 생활습관 내 초기 20:실천 80.
- 가입 설문 초기값 고정·신규 사용자의 확인된 빈 원장 실천 0 시작은 승인됐다. 초기 운동의 세부 배점·목표량은 아직 pending이므로 lifestyle/composite가 null인 상태를 유지한다.
- `composition_formula.py`, `practice_formula.py`의 계산식은 변경하지 않는다.
- ExerciseMissionSession의 accumulated_duration_seconds는 실제 활동시간으로 검증되지 않은 경과시간이다. 0초 여부와 무관하게 모든 additional 시간 가산을 보류한다. 기록과 재료 보상은 변경하지 않는다.
- `additional_time_policy=withheld_until_verified_activity_duration`를 반환한다. 오늘 가산 0과 기존 누적 실천 0은 다르다. 추가 운동만 한 날은 확인된 쉼으로 자동 바꾸지 않는다.
- 운동 domain의 기본 카드만 mission에 포함한다. 기존 카드 snapshot에 domain이 없으면 해당 카드가 참조한 버전의 template domain을 확인한다. 확인되지 않은 domain이나 비운동 카드는 점수 대상에서 제외한다.
- 조회 실패는 practice/daily/cumulative에 null을 반환한다. 브릿지도 실패한 경우 역시 가짜 0점을 만들지 않는다. 신규 여부가 확인되지 않은 빈 원장을 신규 시작으로 분류하지 않는다.
- 730일 이후 과거 누적 성취를 버리던 검토용 제한을 제거했다. 동일 총 단위라도 활동일과 세션 구성이 바뀌면 revision이 달라진다. 반환 revision은 조회 결과의 내용 해시이며 DB 트랜잭션 revision이나 경쟁 제어를 대체하지 않는다.

## API 소비자 확인

PracticeScoreResponse의 practice_score, daily_units, cumulative_units는 nullable이다. null을 0으로 치환하지 말고 미확인/조회 실패 상태를 표시한다. 추가 필드 ledger_state, ledger_revision, additional_time_policy를 제공한다. 기존 composite_score는 기존 계약대로 표시 반올림값이며 확정 전에는 null이다. 이전 산식의 종합 점수를 새 정책 점수로 표시하지 않는다.

완료 성공 → 최신 점수 재조회 → 화면 표시 연결은 Android 담당의 후속 작업이다. 모든 집계를 같은 DB snapshot으로 읽는 경쟁 제어, 쉼 의미 연결, 초기 점수 정책 확정·기존 사용자 전환은 별도 남은 과제다. 신규 0 시작을 재가입·기존 데이터 누락 복구에 적용하지 않는다.

## 검증

`python -B -m pytest -q tests_model_review/test_practice_score_policy.py`

양수 경과시간의 추가 가산 차단, 비운동 기본 카드 제외, 과거 성취 보존, 두 원장 조회 단계의 실패와 브릿지 실패 조합, 신규/기존 빈 원장 구분, 730일 초과 성취, 날짜별 revision을 로컬 mock 저장 계층으로 검사한다. 실제 DB·EC2·센서·APK 종단 검증을 대신하지 않는다.

APK (8) SHA-256 `61395e5c1c3939523c43c06f4c658734fd2b61347998695fa9e951c0c1ef214c`: 기존 데이터 유지 설치 성공, 9월 16일 홈·주간 일보·전날 기본/추가 운동 기록 확인, 일보 점수 25.8. APK DEX에서 `practice-score` 문자열은 발견되지 않았다. 이것만으로 모든 네트워크 경로를 단정하지 않지만 새 점수 API의 앱 연결 완료 근거도 아니다. 이번 코드 변경은 이 APK 및 EC2에 자동 배포되지 않는다.
