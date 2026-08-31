# 02 · 도메인별 ERD

114개를 한 장에 그리면 아무도 못 읽습니다. **도메인 7개로 나눠 그렸습니다.**
각 도메인의 중심 테이블과 그 관계만 담았고, 코드 마스터 테이블은 생략했습니다.

> GitHub · VS Code · Obsidian 에서 그대로 렌더링됩니다.

---

## 0. 전체 흐름 — 완료 1건이 만드는 것

```mermaid
flowchart LR
    A["카드 3장 뽑기<br/>daily_card_sets"] --> B["1장 확정<br/>daily_challenges"]
    B --> C["행동 · 측정<br/>challenge_events / model_sessions"]
    C --> D["COMPLETE 이벤트<br/>1건"]
    D --> E1["카드첩 1장<br/>card_collection_entries"]
    D --> E2["댐 재료 1개<br/>energy_ledger"]
    D --> E3["적합도 질문 1건<br/>challenge_fit_ratings"]
    D --> E4["활동 기록 1건<br/>activity_entries"]
    E2 --> F["댐 단계<br/>user_dams"]
    B --> G["연속 기록<br/>user_streaks"]
    H["쉼 표시<br/>rest_days"] --> G
    E2 --> I["틈튼지수<br/>tmtn_index_runs"]
```

> **넷은 같은 이벤트 ID를 근거로 삼되 서로 합산하지 않습니다.**
> 카드첩 18장 · 재료 41개 · 완료 20일이 서로 다른 숫자인 게 정상입니다.

---

## 1. 사용자 · 인증 · 동의 · 온보딩 (24 테이블)

```mermaid
erDiagram
    users ||--o| user_profiles : "1:1"
    users ||--o{ auth_identities : "로그인 수단"
    users ||--o{ auth_sessions : "세션"
    users ||--o{ user_consents : "동의 이력"
    users ||--o{ body_profile_snapshots : "append-only"
    users ||--o{ activity_profile_snapshots : "append-only"
    users ||--o{ derived_body_estimates : "모델 역산 · 비노출"
    users ||--o| user_life_patterns : "기상·취침"
    users ||--o{ deletion_requests : "탈퇴·기록삭제"
    consent_documents ||--o{ user_consents : "문서 버전"

    users {
        bigint id PK
        varchar email UK "UNIQUE 꼭 필요"
        varchar hashed_password
        varchar name "필수"
        varchar nickname "선택"
        varchar display_name "생성컬럼 COALESCE(nickname,name)"
        smallint birth_year "연월만 받음"
        tinyint birth_month
        enum gender "MALE FEMALE"
        enum status "ACTIVE DEACTIVATED DELETION_PENDING"
    }
    body_profile_snapshots {
        bigint id PK
        bigint user_id FK
        int revision "고칠 때마다 +1"
        decimal height_cm
        decimal weight_kg
        decimal bmi "계산값"
        enum source "ONBOARDING MY_EDIT RECALC"
    }
    activity_profile_snapshots {
        bigint id PK
        enum strength_freq_code "NONE W1..W5_PLUS"
        enum strength_intensity_code "LIGHT MODERATE HARD"
        smallint aerobic_light_min_week
        smallint aerobic_moderate_min_week
        smallint aerobic_vigorous_min_week
        smallint moderate_equivalent_minutes "환산값"
        boolean aerobic_guideline_met
    }
    derived_body_estimates {
        bigint id PK
        boolean is_user_visible "CHECK 로 항상 false"
    }
```

**설계 의도 두 가지**

- **몸 정보·운동 정보는 덮어쓰지 않고 쌓습니다** (`revision` 증가). 틈튼지수를 다시 계산할 때
  "그때 어떤 값으로 냈는지"를 재현할 수 있어야 하기 때문입니다.
- **허리둘레는 모델이 역산하고 사용자에게 보여주지 않습니다.** `is_user_visible` 에 CHECK 제약을 걸어
  실수로라도 화면에 나가지 않게 막아 두었습니다.

---

## 2. 미션 원장 — CSV 200장 (16 테이블)

```mermaid
erDiagram
    elements ||--o{ mission_domains : "오행 5축"
    elements ||--o{ mission_templates : "주 재료"
    mission_domains ||--o{ mission_templates : "영역"
    mission_templates ||--o{ mission_template_slots : "시간대"
    mission_templates ||--o{ mission_template_places : "장소"
    mission_templates ||--o{ mission_template_metrics : "측정 지표"
    mission_templates ||--o{ mission_template_revisions : "문구 이력"
    mission_templates ||--o{ content_reviews : "검수"
    content_reviews ||--o{ content_review_checks : "항목별"

    elements {
        char element_code PK "WOOD FIRE EARTH METAL WATER"
        char hanja "木 火 土 金 水"
        varchar material_code "BRANCH STONE SOIL LEAF WATERWAY"
        varchar material_name_ko "나뭇가지 받침돌 다짐흙 새잎 물길"
        varchar label_ko "도감 라벨 - 01번 문서 3장 참고"
        varchar color_token "디자인 토큰 이름"
    }
    mission_templates {
        int id PK
        varchar mission_key UK "WALK_SLOW_01"
        char element_code FK
        varchar domain_code FK
        enum measure_type "SELF_CHECK SELF_TIMER MODEL_ACTIVE_TIME MODEL_STAIR_COUNT MODEL_DISTANCE"
        enum measure_class "USER_PERFORMED MODEL_MEASURED"
        varchar action_text "천천히 걷기"
        varchar one_liner_template "{place}에서 {num}{unit} 천천히 걷기!"
        varchar fortune_text "오늘의 운세 한 줄"
        smallint number_min
        smallint number_max
        boolean senior_safe
        int fallback_template_id FK "대체 미션 B11"
        enum review_status "APPROVED 여야 카드 후보"
    }
```

**중요** — `review_status = APPROVED` 인 것만 카드 후보가 됩니다.
검수 안 끝난 문구가 사용자에게 나가지 않게 하는 장치입니다.

---

## 3. 카드 · 챌린지 · 카드첩 (12 테이블)

```mermaid
erDiagram
    users ||--o{ daily_card_sets : "하루 1세트"
    daily_card_sets ||--|{ daily_card_options : "3장"
    daily_card_options ||--o| daily_challenges : "확정된 1장"
    daily_challenges ||--o{ challenge_events : "원장"
    daily_challenges ||--o| challenge_notes : "한 줄 회고"
    daily_challenges ||--o| challenge_fit_ratings : "적합도"
    daily_challenges ||--o| card_collection_entries : "카드첩"
    daily_challenges ||--o{ energy_ledger : "재료"
    mission_templates ||--o{ daily_card_options : "스냅샷"

    daily_card_sets {
        bigint id PK
        bigint user_id FK
        date service_date "user+date UNIQUE"
        enum status "UNSELECTED SELECTED EXPIRED"
        bigint selected_option_id FK
        enum generator "RULE LLM FALLBACK"
    }
    daily_card_options {
        bigint id PK
        tinyint slot_no "1 2 3"
        int mission_template_id FK
        int template_revision "그때의 문구를 박제"
        varchar action_text "복사본"
        varchar one_liner_text "완성 문장"
        varchar fortune_text
        smallint lucky_number "B06 행운의 숫자"
        varchar recommendation_reason_code "B10 추천 이유"
    }
    daily_challenges {
        bigint id PK
        date service_date "user+date UNIQUE"
        enum status "READY ACTIVE PAUSED COMPLETED SKIPPED EXPIRED"
        decimal target_value
        decimal achieved_value
        enum decided_by "USER_CONFIRM TIMER MODEL MANUAL_FALLBACK"
        int elapsed_seconds
        bigint substituted_from_option_id FK "B12 대체 미션"
    }
    challenge_events {
        binary id PK "UUID"
        enum event_type "START PAUSE RESUME COMPLETE SKIP RESTORE SUBSTITUTE"
        datetime server_at
        datetime client_at "오프라인 복구용"
        varchar idempotency_key UK "중복 완료 차단"
    }
```

**설계 의도**

- `daily_card_options` 는 미션 마스터를 **복사해서 박제**합니다.
  나중에 마스터 문구를 고쳐도 **이미 뽑은 카드는 안 바뀝니다.**
- 완료는 `challenge_events` 에 **이벤트로** 남고, `daily_challenges.status` 는 그 결과를 요약한 것입니다.
  진실은 언제나 이벤트 쪽입니다.
- `idempotency_key` — 네트워크가 끊겨 앱이 완료를 두 번 보내도 재료는 한 번만 쌓입니다.

---

## 4. 모델 · 자동 측정 (11 테이블)

```mermaid
erDiagram
    models ||--o{ model_versions : "버전"
    model_versions ||--o{ model_sessions : "이 버전으로 측정"
    daily_challenges ||--o| model_sessions : "1:1"
    model_sessions ||--o{ model_session_intervals : "구간 원장"
    model_sessions ||--o{ model_session_checkpoints : "백그라운드 복구용"
    model_sessions ||--o{ model_measurements : "원시 샘플"
    model_sessions ||--o| model_session_results : "최종"
    users ||--o{ device_capabilities : "기기 지원 여부"
    users ||--o{ device_permission_states : "권한 상태"

    model_sessions {
        bigint id PK
        enum measure_type "MODEL_ACTIVE_TIME MODEL_STAIR_COUNT MODEL_DISTANCE"
        int elapsed_seconds "총 경과"
        int effective_seconds "유효 - 이게 목표와 비교됨"
        int auto_paused_seconds
        int manual_paused_seconds
        int unverified_gap_seconds "앱이 죽은 구간"
        int recovered_seconds
        enum quality_level "GOOD FAIR POOR"
        enum signal_level "GOOD FAIR WEAK - C13"
        boolean ran_in_background "C15"
        datetime fallback_to_manual_at "C16 직접 체크로 전환"
    }
    model_session_intervals {
        bigint id PK
        int seq
        enum kind "ACTIVE AUTO_PAUSE MANUAL_PAUSE EXCLUDED BACKGROUND UNVERIFIED_GAP"
        datetime started_at
        datetime ended_at
        decimal value_delta
    }
```

**왜 구간 원장을 따로 두나**

"8분 중 5분 4초"를 화면에 정직하게 보여주려면 **어느 구간이 왜 빠졌는지**를 남겨야 합니다.
`UNVERIFIED_GAP` 은 앱이 강제 종료된 구간입니다 — 이 시간은 **유효 시간에 넣지 않습니다.**
C17 앱 복구 화면이 바로 이 구간을 사용자에게 알려 주는 화면입니다.

---

## 5. 비버 · 댐 · 연속 기록 (13 테이블) ★ v5에서 제일 중요

```mermaid
erDiagram
    users ||--o| user_dams : "댐 1개"
    users ||--o{ energy_ledger : "재료 원장 - 차감 불가"
    users ||--o{ user_element_counters : "재료 5종 집계"
    users ||--o{ rest_days : "쉼"
    users ||--o| user_streaks : "연속 기록 캐시"
    users ||--o{ weekly_reports : "주간 리포트"
    dam_stages ||--o{ user_dams : "1~5단계"
    user_dams ||--o{ dam_stage_advancements : "승급 이력 G07"
    elements ||--o{ energy_ledger : "어떤 재료"
    weekly_reports ||--o{ weekly_report_materials : "그 주 재료 내역"
    beaver_lines ||--o{ weekly_reports : "비버 한마디"

    energy_ledger {
        bigint id PK
        char element_code FK
        smallint amount "항상 양수 - 차감 없음"
        binary source_event_id FK "어느 완료에서 왔나"
        date service_date
    }
    user_dams {
        bigint user_id PK
        tinyint current_stage_no FK
        int total_materials "누적 41개"
    }
    dam_stages {
        tinyint stage_no PK
        varchar name_ko "몸통 연결하기"
        smallint required_materials "5 15 35 70 120"
        enum asset_status "PLANNED IN_PRODUCTION READY"
    }
    rest_days {
        bigint id PK
        date service_date "user+date UNIQUE"
        char week_key "ISO 주 - 월요일 시작"
        varchar reason_code
        datetime marked_at
    }
    user_streaks {
        bigint user_id PK
        smallint current_streak "14"
        smallint longest_streak "21 - CHECK longest >= current"
        date last_service_date
    }
    weekly_reports {
        bigint id PK
        char week_key "user+week UNIQUE"
        date window_start "월요일"
        date window_end "일요일"
        tinyint completed_days "실천"
        tinyint rest_days_count "쉼"
        tinyint missed_days "미완료"
        smallint materials_week
        enum best_slot "가장 잘 지킨 시간대"
    }
```

**v5 쉼 규칙이 이 세 테이블로 그대로 표현됩니다**

| 화면이 말하는 것 | 어디서 나오나 |
|---|---|
| 달력의 **실천** | `daily_challenges.status = COMPLETED` |
| 달력의 **쉼** | `rest_days` 에 그 날짜가 있음 |
| 달력의 **미완료** | 지난 날짜인데 위 둘 다 없음 (파생 — 저장하지 않음) |
| "이번 주 남은 쉼 1회 / 2회" | `COUNT(rest_days WHERE week_key = 이번주)` 를 2에서 뺌 |
| "연속 기록 14일째" | `user_streaks.current_streak` |
| "가장 오래 이어간 기록 21일" | `user_streaks.longest_streak` |
| "쉼은 기록을 끊지 않아요" | 연속 계산에서 `rest_days` 날짜를 **건너뜀** |

> **주 2회 상한은 반드시 서버가 셉니다.** 앱에서 세면 기기를 바꾸거나 앱을 지웠다 깔면 숫자가 틀어집니다.
> `ix_rd_week (user_id, week_key)` 인덱스가 그 계산을 위해 있습니다.

> `user_streaks` 는 **캐시**입니다. 진실은 `daily_challenges` + `rest_days` 이고,
> 숫자가 의심스러우면 언제든 다시 계산할 수 있어야 합니다.

---

## 6. 틈튼지수 · 건강 참고 (12 테이블)

```mermaid
erDiagram
    users ||--o{ tmtn_index_runs : "산출 이력"
    tmtn_index_runs ||--o{ tmtn_index_contributions : "항목별 기여"
    tmtn_index_runs ||--o{ tmtn_index_inputs : "쓰인 값 스냅샷"
    tmtn_index_bands ||--o{ tmtn_index_runs : "구간"
    body_profile_snapshots ||--o{ tmtn_index_runs : "그때의 몸 정보"
    activity_profile_snapshots ||--o{ tmtn_index_runs : "그때의 운동량"
    knhanes_cohort_prevalence ||--o{ tmtn_index_runs : "또래 참고"
    tmtn_index_sources ||--o{ tmtn_index_runs : "출처 표기"

    tmtn_index_runs {
        bigint id PK
        date service_date
        date window_start "최근 7일"
        date window_end
        tinyint score "68"
        varchar band_code "보통 구간"
        tinyint prev_score "달라진 이유 계산용"
        enum status "COMPUTED INSUFFICIENT_INPUT FAILED"
        tinyint record_days_actual "3 - E05"
        tinyint record_days_required "5 - E05"
        json missing_inputs
        varchar method_version "재현성"
    }
    tmtn_index_contributions {
        bigint run_id FK
        varchar domain_code
        decimal contribution_pct "NULL 허용 - 검증 전에는 % 안 씀"
        boolean is_reflected
        boolean is_excluded
        varchar exclude_reason
    }
```

**설계 의도**

- **"산출 불가"도 정상 상태입니다.** `status = INSUFFICIENT_INPUT` + `record_days_actual/required` 로
  E05 화면의 "3일 / 7일 · 필요한 기록 5일"이 그대로 나옵니다. 오류가 아닙니다.
- `contribution_pct` 는 **NULL 허용**입니다. 모델 검증이 끝나기 전에는 화면에 %를 쓰지 않습니다.
- `method_version` · `calibration_version` — 나중에 "이 68점은 어떻게 나온 건가"를 재현하기 위한 것입니다.
  **비진단용 참고 정보**라고 화면에 쓰는 이상, 근거를 남길 수 있어야 합니다.

---

## 7. 알림 · 피드백 · 실험 · 운영 (26 테이블)

```mermaid
erDiagram
    users ||--o| notification_preferences : "on off"
    users ||--o{ notification_schedules : "아침 점심뒤 자기전"
    users ||--o{ device_tokens : "푸시 토큰"
    notification_schedules ||--o{ notification_deliveries : "발송 이력"
    users ||--o| user_life_patterns : "기상 취침"
    user_life_patterns ||--o{ notification_slot_rules : "규칙으로 시각 도출"
    users ||--o{ card_feedbacks : "C08 적합도"
    users ||--o{ support_tickets : "F21 문의"
    experiments ||--o{ experiment_variants : ""
    experiment_variants ||--o{ experiment_assignments : ""
    users ||--o{ experiment_assignments : ""
    error_codes ||--o{ service_maintenances : "H03"
    idempotency_keys }o--|| users : "중복 요청 차단"
    outbox_events }o--|| users : "이벤트 발행"
```

**알림 시각은 저장하지 않고 규칙으로 계산합니다**

A10에서 받은 기상 7:00 · 취침 23:30 에서 `notification_slot_rules` 를 거쳐
아침 7:00 · 점심뒤 13:00 · 자기전 22:30 이 나옵니다.
사용자가 기상 시각을 바꾸면 **세 알림이 자동으로 따라옵니다.** 뷰 `v_derived_notification_times` 가 그 계산입니다.
