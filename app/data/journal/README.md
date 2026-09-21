# 틈튼일보 읽을거리 데이터

이 폴더가 실제 서버의 읽을거리 원본이다. 앱은 `GET /api/v1/tuntun-score/editorial`로 읽는다.

- `knowledge.json`: 식탁 21개, 움직임 9개, 하루 돌보기 10개. 공식 출처 대조를 마친 초안이다.
- `sources.json`: 공식 자료 15개: 기관, 원문 URL, 정확한 위치, 확인일, 자료 버전.
- `approvals.json`: 실제 검수·이용 범위 확인 기록. 운영 공개 승인 항목은 없다. 내부 시연 승인은 아래 별도 기록을 따른다.

문장 자체의 `review_status`나 `production_publishable`만 바꾸면 공개되지 않는다. 승인 기록의 문장 ID·리비전·내용 해시·출처 ID·출처 버전이 모두 맞아야 한다. 확인 기한이 지난 출처와 해당 연령대에 적용할 수 없는 문장은 제외한다.

## 문장을 추가할 때

1. 공식 원문에서 해당 주장과 적용 대상을 확인한다. 개인의 식사 상태나 질환 개선을 추정하지 않는다.
2. 새 ID와 정확한 출처 위치로 `knowledge.json`에 초안을 추가한다.
3. `app.services.journal_editorial_service.content_hash`로 내용 해시를 계산한다.
4. 검수·이용 범위 확인 후 `approvals.json`에 실제 기록을 추가한다. `reviewer`, `approval_id`, `rights_review_id`는 실제 담당자와 확인 기록이어야 한다.
5. 문장이나 출처 버전을 수정하면 새 해시로 재검수한다. 기존 승인을 복사해 유지하지 않는다.

승인 항목의 필드: `id`, `revision`, `content_sha256`, `source_id`, `source_edition`, `status="approved"`, `reviewer`, `approval_id`, `rights_status="cleared_for_app_summary"`, `rights_review_id`, `expires_on`.

최신 프롬프트는 `xai_workbench/prompts/`의 v6.2이며 초안 작성·검토 이력은 로컬 작업실에 남긴다. `python -m xai_workbench.export_app_catalog`로 확인된 추가 메모를 앱 카탈로그·검토용 앱 자료에 동기화할 수 있다. 이 명령은 공개 승인을 생성하지 않는다. 운영 요청에서는 LLM이 건강 문장을 새로 쓰지 않는다. 승인된 고정 문장을 주제별로 섞어 날짜별로 제공한다.

`related_readings`에는 선택 지표와 관련된 일반 지식이 최대 두 편 들어간다. 본문 지면과 같은 승인·연령·유효기간 검사를 거친다. 개인의 SHAP 결과를 해당 식습관의 증거로 사용하지 않는다.

개인별 SHAP는 이 지식 목록을 설명하지 않는다. 별도 개인 결과 API가 사용자의 입력과 모델 계산 결과를 함께 묶어 제공한다.

## 2026-09-20 내부 시연 승인

사용자가 현재 승인 대기 자료를 모두 승인 처리하도록 요청했다. 기존에 명시한 팀 내부 검토·시연 서버 범위에서 40건의 내용 해시·출처 버전을 `internal_approvals.json`에 기록했다. `ENV=local/dev`만 이를 읽으며 `prod`는 읽지 않는다. `knowledge.json`의 `review_status=approved`는 `review_scope=team_internal_demo`와 함께 해석한다. 출처 `rights_status`와 `production_publishable=false`는 그대로이며 실제로 확인하지 않은 권리/임상 검수 기록을 만들지 않았다. 날짜별로 각 지면 1편씩 순환한다.
