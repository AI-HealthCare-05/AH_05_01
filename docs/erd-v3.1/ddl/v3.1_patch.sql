-- ============================================================
--  TMTN ERD v3.1 패치
--  기준: tmtn_schema_v3_mysql8.sql (v3.0 · 114 테이블)
--  대상: MySQL 8 / MariaDB 10.11
--  2026-08-29
--
--  이 파일은 v3.0 DDL 을 "대체"하지 않습니다. 그 위에 얹습니다.
-- ============================================================


-- ------------------------------------------------------------
-- PATCH 1 · users 충돌 해소   ★ 다른 것보다 먼저
--
--   저장소 app/models/users.py 가 ERD 와 다릅니다.
--   161개 FK 대부분이 users.id 를 보므로 여기가 먼저 정리돼야 합니다.
--   지금은 실사용자 데이터가 없어 부담 없이 바꿀 수 있는 유일한 시점입니다.
-- ------------------------------------------------------------

-- 1-1. 이메일 UNIQUE  ← 지금 없습니다. 동시 가입 시 중복 계정이 생기는 버그
ALTER TABLE users
  ADD UNIQUE KEY uk_users_email (email);

-- 1-2. 생년월일: 전체 날짜 → 연·월만
--      A07 "정확한 날짜는 받지 않습니다. 연·월만 있으면 충분해요"
ALTER TABLE users
  ADD COLUMN birth_year  SMALLINT UNSIGNED NULL AFTER name,
  ADD COLUMN birth_month TINYINT  UNSIGNED NULL AFTER birth_year;

UPDATE users
   SET birth_year  = YEAR(birthday),
       birth_month = MONTH(birthday)
 WHERE birthday IS NOT NULL;

ALTER TABLE users
  MODIFY COLUMN birth_year  SMALLINT UNSIGNED NOT NULL,
  MODIFY COLUMN birth_month TINYINT  UNSIGNED NOT NULL,
  ADD CONSTRAINT chk_users_birth_month CHECK (birth_month BETWEEN 1 AND 12),
  ADD CONSTRAINT chk_users_birth_year  CHECK (birth_year  BETWEEN 1900 AND 2100),
  DROP COLUMN birthday;

-- 1-3. 전화번호 삭제
--      가입 흐름 어디에도 없습니다. "불필요한 데이터는 수집하지 않는다"
ALTER TABLE users
  DROP COLUMN phone_number;

-- 1-4. 닉네임 + 표시 이름
--      A07 이름(필수) + 닉네임(선택). "닉네임을 비우면 이름을 그대로 씁니다"
ALTER TABLE users
  ADD COLUMN nickname     VARCHAR(20) NULL AFTER name,
  ADD COLUMN display_name VARCHAR(20)
      GENERATED ALWAYS AS (COALESCE(nickname, name)) STORED AFTER nickname;

-- 1-5. 계정 상태
ALTER TABLE users
  ADD COLUMN status ENUM('ACTIVE','DEACTIVATED','DELETION_PENDING')
      NOT NULL DEFAULT 'ACTIVE' AFTER gender,
  ADD COLUMN deactivated_at DATETIME NULL AFTER status;

-- 1-6. 이름 정리 (선택)
ALTER TABLE users
  CHANGE COLUMN last_login last_login_at DATETIME NULL;


-- ------------------------------------------------------------
-- PATCH 2 · 쉼 (Figma v5)
--
--   v5 에서 쉼이 "사용자가 직접 누르는 행동" 이 되었습니다.
--   홈(B16)과 달력(D06) 두 경로가 생겼고, 취소(B17)도 가능해졌습니다.
-- ------------------------------------------------------------

-- 2-1. 어느 경로로 쉬었나
ALTER TABLE rest_days
  ADD COLUMN source ENUM('HOME','CALENDAR','SYSTEM') NOT NULL DEFAULT 'HOME'
      COMMENT 'HOME=B16 홈에서 / CALENDAR=D06 달력에서 / SYSTEM=자동'
      AFTER reason_code;

-- 2-2. 취소는 삭제가 아니라 표시
--      지우면 "썼다가 취소했다" 는 흐름이 안 남고 상한 악용도 못 봅니다
ALTER TABLE rest_days
  ADD COLUMN cancelled_at DATETIME NULL
      COMMENT 'B17 그래도 미션 해볼래요' AFTER marked_at;

-- 2-3. UNIQUE 를 "살아 있는 쉼" 에만 걸기
--      is_active 가 취소된 행에서 NULL 이 되고,
--      MySQL 의 UNIQUE 는 NULL 을 중복으로 보지 않습니다.
--      → 같은 날을 쉼 → 취소 → 다시 쉼 이 가능하고,
--        살아 있는 쉼은 날짜당 하나뿐입니다.
ALTER TABLE rest_days DROP INDEX uk_rd;

ALTER TABLE rest_days
  ADD COLUMN is_active BOOLEAN
      GENERATED ALWAYS AS (CASE WHEN cancelled_at IS NULL THEN 1 ELSE NULL END) VIRTUAL,
  ADD UNIQUE KEY uk_rd_active (user_id, service_date, is_active);

-- 2-4. 주 상한을 코드에 박지 않기
CREATE TABLE IF NOT EXISTS rest_policies (
  policy_key     VARCHAR(40)      NOT NULL,
  weekly_limit   TINYINT UNSIGNED NOT NULL DEFAULT 2,
  effective_from DATE             NOT NULL,
  note           VARCHAR(200)     NULL,
  PRIMARY KEY (policy_key, effective_from),
  CONSTRAINT chk_rp_limit CHECK (weekly_limit BETWEEN 0 AND 7)
) ENGINE=InnoDB
  COMMENT='쉼 주당 상한. 상한이 바뀌어도 지난 주는 그때 값으로 판정한다';

INSERT INTO rest_policies (policy_key, weekly_limit, effective_from, note)
VALUES ('DEFAULT', 2, '2026-01-01', 'Figma v5 · 한 주에 두 번까지 쉴 수 있어요')
ON DUPLICATE KEY UPDATE note = VALUES(note);


-- ------------------------------------------------------------
-- PATCH 3 · 앱 missions.json ↔ ERD 매핑 뷰
--
--   앱은 camelCase(axis, rewardMaterial), 서버는 snake_case(element_code …)
--   값은 대부분 같고 이름만 다릅니다. 앱이 그대로 읽을 수 있는 뷰를 둡니다.
-- ------------------------------------------------------------

CREATE OR REPLACE VIEW v_mission_catalog_app AS
SELECT
  mt.mission_key                AS id,
  mt.element_code               AS axis,
  e.hanja                       AS axisHanja,
  e.label_ko                    AS axisLabel,
  md.name_ko                    AS area,
  mt.action_text                AS action,
  mt.measure_type               AS type,
  (mt.measure_class = 'MODEL_MEASURED') AS modelBacked,
  mt.number_min                 AS numMin,
  mt.number_max                 AS numMax,
  mt.number_step                AS numStep,
  mt.unit_code                  AS unit,
  mt.fortune_text               AS fortune,
  mt.one_liner_template         AS lineTemplate,
  mt.safety_note                AS safetyNote,
  mt.senior_safe                AS seniorSafe,
  e.material_code               AS rewardMaterial,
  e.material_name_ko            AS rewardName
FROM mission_templates mt
JOIN elements       e  ON e.element_code = mt.element_code
JOIN mission_domains md ON md.domain_code = mt.domain_code
WHERE mt.is_active = TRUE
  AND mt.review_status = 'APPROVED';


-- ------------------------------------------------------------
-- PATCH 4 · 재료 라벨   ★ 기획(지현님) 확정 후에만 실행
--
--   앱 데이터: 식사 미션은 土(다짐흙) 에 있고, 金(새잎) 은 기록입니다.
--   Figma  : 새잎 = "식사·기록" 이라고 적혀 있어 서로 어긋납니다.
--
--   아래는 "데이터를 그대로 두고 라벨만 맞추는" 방향입니다.
--   반대로 가려면 CSV 40행의 domain/element 를 옮겨야 합니다.
-- ------------------------------------------------------------

-- UPDATE elements SET label_ko = '식사 · 생활리듬' WHERE element_code = 'EARTH';
-- UPDATE elements SET label_ko = '기록'           WHERE element_code = 'METAL';


-- ------------------------------------------------------------
-- PATCH 5 · 확인 쿼리
-- ------------------------------------------------------------

-- 테이블 수 (전체 적재 후 114 여야 함)
-- SELECT COUNT(*) FROM information_schema.tables
--  WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE';

-- 이번 주 남은 쉼  (B16 "이번 주 남은 쉼 1회 / 2회")
-- SELECT p.weekly_limit - COUNT(r.id) AS remaining, p.weekly_limit
--   FROM rest_policies p
--   LEFT JOIN rest_days r
--          ON r.user_id = ? AND r.week_key = ? AND r.cancelled_at IS NULL
--  WHERE p.policy_key = 'DEFAULT'
--    AND p.effective_from <= CURRENT_DATE
--  GROUP BY p.weekly_limit
--  ORDER BY p.effective_from DESC LIMIT 1;


-- ------------------------------------------------------------
-- PATCH 6 · 화면 역추적에서 발견한 빠진 것 4개
--            (05_화면_역추적_검증.md 참고)
-- ------------------------------------------------------------

-- 6-①  A04 이메일 인증번호 · A11 인증번호 오류 · F15 이메일 변경
--
--       가입이 여기서 막힙니다. 1단계에 반드시 포함하세요.
--       화면 문구: "남은 시도 3회 중 1회 사용" / "남은 시간 4:32"
CREATE TABLE IF NOT EXISTS email_verifications (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id       BIGINT UNSIGNED NULL COMMENT '가입 전에는 NULL',
  purpose       ENUM('SIGN_UP','EMAIL_CHANGE') NOT NULL,
  email_hash    CHAR(64)        NOT NULL COMMENT '원문 미보관. password_reset_requests 와 같은 방식',
  code_hash     CHAR(64)        NOT NULL COMMENT '6자리 숫자를 해시로만 보관',
  attempts      TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'A11 남은 시도 표시',
  max_attempts  TINYINT UNSIGNED NOT NULL DEFAULT 5,
  resend_count  TINYINT UNSIGNED NOT NULL DEFAULT 0,
  requested_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at    DATETIME        NOT NULL COMMENT '보통 +5분. A04 남은 시간',
  verified_at   DATETIME        NULL,
  PRIMARY KEY (id),
  KEY ix_ev_lookup (email_hash, purpose, requested_at),
  KEY ix_ev_user (user_id),
  CONSTRAINT fk_ev_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT chk_ev_attempts CHECK (attempts <= max_attempts)
) ENGINE=InnoDB
  COMMENT='A04·A11 가입 인증, F15 이메일 변경. 코드는 해시로만 보관';

-- 6-②  B14 · B14b 첫 진입 코치마크를 봤는지
--
--       앱에 저장하면 기기를 바꾸거나 지웠다 깔면 다시 뜹니다.
ALTER TABLE user_profiles
  ADD COLUMN coachmark_seen_at DATETIME NULL
      COMMENT 'B14·B14b 첫 진입 코치마크 완료 시각' AFTER onboarded_at;

-- 6-③  B11 대체 미션을 요청한 이유
--
--       "시간이 없어요 / 장소가 마땅치 않아요 / 몸이 무거워요 / 날씨가 안 맞아요"
--       쌓이면 추천 조정(recommendation_preferences)의 근거가 됩니다.
CREATE TABLE IF NOT EXISTS substitution_reason_codes (
  code       VARCHAR(40)      NOT NULL,
  label_ko   VARCHAR(40)      NOT NULL,
  sort_order TINYINT UNSIGNED NOT NULL DEFAULT 0,
  is_active  BOOLEAN          NOT NULL DEFAULT TRUE,
  PRIMARY KEY (code)
) ENGINE=InnoDB COMMENT='B11 대체 미션 요청 이유';

INSERT INTO substitution_reason_codes (code, label_ko, sort_order) VALUES
  ('NO_TIME',    '시간이 없어요',        1),
  ('NO_PLACE',   '장소가 마땅치 않아요', 2),
  ('LOW_ENERGY', '몸이 무거워요',        3),
  ('WEATHER',    '날씨가 안 맞아요',     4)
ON DUPLICATE KEY UPDATE label_ko = VALUES(label_ko);

ALTER TABLE daily_challenges
  ADD COLUMN substitution_reason_code VARCHAR(40) NULL
      COMMENT 'B11 왜 바꿨나' AFTER substituted_from_option_id,
  ADD CONSTRAINT fk_dch_sub_reason
      FOREIGN KEY (substitution_reason_code)
      REFERENCES substitution_reason_codes(code);


-- ============================================================
--  패치 후 테이블 수 : 114 + email_verifications
--                          + rest_policies
--                          + substitution_reason_codes
--                        = 117
-- ============================================================
