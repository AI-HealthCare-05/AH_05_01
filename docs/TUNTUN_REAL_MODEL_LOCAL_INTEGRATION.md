# 틈튼지수 실모델(신체·당뇨·고혈압) 로컬 연동 가이드

`TUNTUN_INTEGRATED_QUICKSTART_2026-09-04.md`(모델 팀 전달분) 기준. 이 문서는 **앱 저장소 쪽**에서
뭘 어떻게 붙였는지와, 팀원이 로컬에서 실모델을 켜보려면 뭘 해야 하는지를 정리한다.

## 상태

- `PRODUCTION_RELEASE_GATE: BLOCKED` — 운영 배포 승인 아님. 로컬 검토용.
- 기본값은 **비활성**. `.env`에 `TUNTUN_LOCAL_MODEL_URL`을 채우지 않으면 지금과 완전히
  동일하게 Mock으로만 동작한다. 즉 이 브랜치를 그냥 머지해도 운영 동작에는 아무 영향이 없다.

## 왜 모델 파일이 이 저장소에 없는가

`waist_model.joblib`(26MB) 등 학습된 모델 파일과 그 검토용 소스(`review_src/`, 계약서,
provenance)는 이 저장소에 커밋하지 않았다. 모델이 계속 바뀔 예정이라(`docs/TMTN_READINESS_AUDIT.md`
§Git LFS·모델 레지스트리 권고 참고), 앱 코드와 같은 git 히스토리에 큰 바이너리를 쌓는 대신
별도 배포 채널(공유 드라이브 등)로 관리한다. 앱 저장소에는 **연동 코드만** 있다.

## 로컬에서 실모델 붙여보기

1. 모델 팀이 공유한 `tuntun_integrated_v0_1_20260904` 패키지를 받아서 로컬 아무 폴더에 풀어둔다
   (이 저장소 밖 — 예: `~/tuntun-model/`).
2. 패키지 안내(`TUNTUN_INTEGRATED_QUICKSTART_2026-09-04.md`)대로 Python 3.14.7 별도 가상환경을
   만들고 `requirements.txt`를 설치한다.
3. 그 가상환경에서 로컬 서비스를 띄운다:
   ```
   python tuntun_local_service.py --bundle . --manifest-sha256 <전달받은 해시> --port 8765
   ```
4. 앱 백엔드(Python 3.13.15) 쪽 `.env`에 한 줄 추가:
   ```
   TUNTUN_LOCAL_MODEL_URL=http://127.0.0.1:8765
   ```
5. 서버 재시작 후 `GET /tuntun-score/v2` 호출 — 신체정보(키·몸무게·생년월·성별)와 운동습관이
   모두 있는 계정이면 `isMock=false`, `componentScores[].source="model_inference"`로 응답이
   온다. 하나라도 없으면 원래대로 Mock.

## ⚠️ 아직 못 채운 것 — pregnancy_status

모델 입력 계약(`input_schema.json`)은 "`pregnancy_status`는 명시적으로 알아야 한다 — 미수집을
`nonpregnant`로 가정하면 안 된다"고 되어 있다. 지금 온보딩·건강정보 어디에도 이 값을 수집하는
필드가 없어서, `app/services/tuntun_score_service.py`의 `_get_pregnancy_status()`가 **항상
None을 돌려주고 실모델 호출 자체를 건너뛰게** 해뒀다. 즉 지금 상태로는 로컬 서비스를 띄워도
실제로는 절대 호출되지 않고 계속 Mock만 나온다(안전한 기본값).

**임신 상태를 언제·누구에게 수집할지 결정되면** `_get_pregnancy_status()`만 실제 값을 읽어
돌려주도록 채우면 나머지(HTTP 호출·DTO 매핑)는 이미 다 되어 있다.

## 변경한 파일

- `app/core/config.py`: `TUNTUN_LOCAL_MODEL_URL` 설정값 추가(기본 `None`)
- `app/dtos/tuntun_score.py`: `TuntunComponentScoreV2.source`에 `"model_inference"` 추가
  (모델 팀 전달 `integration/tuntun_score_dto_candidate.py`의 유일한 diff)
- `app/services/tuntun_score_service.py`: `_get_pregnancy_status()`(현재 항상 None),
  `_call_local_model_inference()`(로컬 HTTP 호출, 실패 시 조용히 Mock 폴백) 추가
- `envs/example.local.env`: 설정 예시 주석 추가(기본은 비활성)
