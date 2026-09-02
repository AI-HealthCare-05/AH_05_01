from tortoise import BaseDBAsyncClient

RUN_IN_TRANSACTION = True


async def upgrade(db: BaseDBAsyncClient) -> str:
    return """
        CREATE TABLE IF NOT EXISTS `deletion_jobs` (
    `id` CHAR(36) NOT NULL PRIMARY KEY,
    `user_id` CHAR(36) NOT NULL,
    `status` VARCHAR(10) NOT NULL COMMENT 'REQUESTED: REQUESTED\nPROCESSING: PROCESSING\nCOMPLETED: COMPLETED' DEFAULT 'REQUESTED',
    `requested_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `completed_at` DATETIME(6),
    `policy_version` VARCHAR(20) NOT NULL,
    `failure_code` VARCHAR(50)
) CHARACTER SET utf8mb4 COMMENT='문서 §9: user_id에 FK를 안 걸어둔 게 의도적(삭제 후에도 감사기록 남기려고).';
        CREATE TABLE IF NOT EXISTS `deletion_job_items` (
    `id` CHAR(36) NOT NULL PRIMARY KEY,
    `data_category` VARCHAR(50) NOT NULL,
    `action` VARCHAR(16) NOT NULL COMMENT 'ANONYMIZE: ANONYMIZE\nHARD_DELETE: HARD_DELETE\nRETAIN_FOR_AUDIT: RETAIN_FOR_AUDIT',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    `completed_at` DATETIME(6),
    `job_id` CHAR(36) NOT NULL,
    CONSTRAINT `fk_deletion_deletion_9fc07a0e` FOREIGN KEY (`job_id`) REFERENCES `deletion_jobs` (`id`) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COMMENT='기능명세서 §1.3 확인: job_id·data_category·action·status·completed_at.';
        CREATE TABLE IF NOT EXISTS `email_verification_requests` (
    `id` CHAR(36) NOT NULL PRIMARY KEY,
    `request_id` CHAR(36) NOT NULL UNIQUE,
    `email_normalized` VARCHAR(255) NOT NULL,
    `code_hash` VARCHAR(255) NOT NULL,
    `expires_at` DATETIME(6) NOT NULL,
    `verified_at` DATETIME(6),
    `attempt_count` INT NOT NULL DEFAULT 0,
    `resend_count` INT NOT NULL DEFAULT 0,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY `idx_email_verif_email_n_92ec6e` (`email_normalized`)
) CHARACTER SET utf8mb4 COMMENT='기능명세서 §1.4 확인됨. 34개 ERD 요약에는 없었지만 실제 스펙엔 명시된 테이블.';
        CREATE TABLE IF NOT EXISTS `password_reset_tokens` (
    `id` CHAR(36) NOT NULL PRIMARY KEY,
    `token_hash` VARCHAR(255) NOT NULL UNIQUE,
    `expires_at` DATETIME(6) NOT NULL,
    `used_at` DATETIME(6),
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `user_id` BIGINT NOT NULL,
    CONSTRAINT `fk_password_users_6176974d` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COMMENT='기능명세서 §1.5 확인됨. 34개 ERD 요약에는 없었지만 실제 스펙엔 명시된 테이블.';
        CREATE TABLE IF NOT EXISTS `sessions` (
    `id` CHAR(36) NOT NULL PRIMARY KEY,
    `device_id` VARCHAR(100),
    `refresh_token_hash` VARCHAR(255) NOT NULL,
    `fcm_token` VARCHAR(255),
    `issued_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `expires_at` DATETIME(6) NOT NULL,
    `last_seen_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    `revoked_at` DATETIME(6),
    `revoke_reason` VARCHAR(16) COMMENT 'LOGOUT: LOGOUT\nEXPIRED: EXPIRED\nACCOUNT_DELETION: ACCOUNT_DELETION\nSECURITY: SECURITY',
    `user_id` BIGINT NOT NULL,
    CONSTRAINT `fk_sessions_users_0c63f6d3` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COMMENT='문서: sessions는 user_id·expires_at·revoked_at·last_seen_at을 저장한다 (기능명세서 §1.5 근거).';
        CREATE TABLE IF NOT EXISTS `user_consents` (
    `id` CHAR(36) NOT NULL PRIMARY KEY,
    `purpose` VARCHAR(25) NOT NULL COMMENT 'TERMS_OF_SERVICE: TERMS_OF_SERVICE\nPRIVACY_POLICY: PRIVACY_POLICY\nNON_DIAGNOSTIC_NOTICE: NON_DIAGNOSTIC_NOTICE\nHEALTH_REFERENCE_ANALYSIS: HEALTH_REFERENCE_ANALYSIS\nNOTIFICATION: NOTIFICATION',
    `document_version` VARCHAR(20) NOT NULL,
    `status` VARCHAR(9) NOT NULL COMMENT 'AGREED: AGREED\nWITHDRAWN: WITHDRAWN' DEFAULT 'AGREED',
    `agreed_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `withdrawn_at` DATETIME(6),
    `user_id` BIGINT NOT NULL,
    UNIQUE KEY `uid_user_consen_user_id_a79551` (`user_id`, `purpose`, `document_version`),
    CONSTRAINT `fk_user_con_users_4a5cdd72` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COMMENT='문서: append-only, 법적 게이트. row 삭제 없이 withdrawn_at만 기록.';
        CREATE TABLE IF NOT EXISTS `health_input_snapshots` (
    `id` CHAR(36) NOT NULL PRIMARY KEY,
    `measured_at` DATETIME(6) NOT NULL,
    `input_values` JSON NOT NULL,
    `units` JSON NOT NULL,
    `source` VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `user_id` BIGINT NOT NULL,
    CONSTRAINT `fk_health_i_users_6589439d` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COMMENT='문서: \"예측 모델 입력 · append-only\".';
        CREATE TABLE IF NOT EXISTS `mission_template_versions` (
    `id` CHAR(36) NOT NULL PRIMARY KEY,
    `template_key` VARCHAR(50) NOT NULL,
    `version` INT NOT NULL DEFAULT 1,
    `five_element` VARCHAR(10) NOT NULL,
    `title` VARCHAR(50) NOT NULL,
    `guide_text` LONGTEXT NOT NULL,
    `exec_type` VARCHAR(23) NOT NULL COMMENT 'TIMER: TIMER\nCHECK: CHECK\nSENSOR_STEPS: SENSOR_STEPS\nSENSOR_FLOORS_CLIMBED: SENSOR_FLOORS_CLIMBED\nSENSOR_STEPS_IN_PLACE: SENSOR_STEPS_IN_PLACE\nSENSOR_RUNNING_DISTANCE: SENSOR_RUNNING_DISTANCE\nSENSOR_RUNNING_DURATION: SENSOR_RUNNING_DURATION\nSENSOR_WALKING_DURATION: SENSOR_WALKING_DURATION',
    `target_value` SMALLINT NOT NULL,
    `unit` VARCHAR(10) NOT NULL,
    `difficulty` SMALLINT NOT NULL,
    `senior_safe` BOOL NOT NULL DEFAULT 1,
    `is_active` BOOL NOT NULL DEFAULT 1,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY `uid_mission_tem_templat_788df4` (`template_key`, `version`)
) CHARACTER SET utf8mb4 COMMENT='문서: \"카드가 참조하는 원본\". 버전 관리 테이블이라 과거 버전도 남아있어야 함';
        CREATE TABLE IF NOT EXISTS `daily_card_sets` (
    `id` CHAR(36) NOT NULL PRIMARY KEY,
    `service_date` DATE NOT NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `user_id` BIGINT NOT NULL,
    UNIQUE KEY `uid_daily_card__user_id_08f7d4` (`user_id`, `service_date`),
    CONSTRAINT `fk_daily_ca_users_24a0631c` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COMMENT='문서: \"service_date당 1세트\". unique_together로 강제.';
        CREATE TABLE IF NOT EXISTS `card_options` (
    `id` CHAR(36) NOT NULL PRIMARY KEY,
    `option_index` INT NOT NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `card_set_id` CHAR(36) NOT NULL,
    `mission_template_version_id` CHAR(36) NOT NULL,
    UNIQUE KEY `uid_card_option_card_se_5f6f23` (`card_set_id`, `option_index`),
    CONSTRAINT `fk_card_opt_daily_ca_14013432` FOREIGN KEY (`card_set_id`) REFERENCES `daily_card_sets` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_card_opt_mission__6c9b6b79` FOREIGN KEY (`mission_template_version_id`) REFERENCES `mission_template_versions` (`id`) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COMMENT='문서: \"세트당 정확히 3개\". mission_template_versions를 참조하는 원본 스냅샷.';
        CREATE TABLE IF NOT EXISTS `daily_card_selections` (
    `id` CHAR(36) NOT NULL PRIMARY KEY,
    `selected_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `card_option_id` CHAR(36) NOT NULL,
    `card_set_id` CHAR(36) NOT NULL UNIQUE,
    CONSTRAINT `fk_daily_ca_card_opt_41f8b3c4` FOREIGN KEY (`card_option_id`) REFERENCES `card_options` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_daily_ca_daily_ca_d7239fac` FOREIGN KEY (`card_set_id`) REFERENCES `daily_card_sets` (`id`) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COMMENT='문서: \"winner 1개\". 세트당 정확히 하나만 확정될 수 있음.';
        CREATE TABLE IF NOT EXISTS `challenges` (
    `id` CHAR(36) NOT NULL PRIMARY KEY,
    `exec_type` VARCHAR(23) NOT NULL COMMENT 'TIMER: TIMER\nCHECK: CHECK\nSENSOR_STEPS: SENSOR_STEPS\nSENSOR_FLOORS_CLIMBED: SENSOR_FLOORS_CLIMBED\nSENSOR_STEPS_IN_PLACE: SENSOR_STEPS_IN_PLACE\nSENSOR_RUNNING_DISTANCE: SENSOR_RUNNING_DISTANCE\nSENSOR_RUNNING_DURATION: SENSOR_RUNNING_DURATION\nSENSOR_WALKING_DURATION: SENSOR_WALKING_DURATION',
    `mission_snapshot` JSON NOT NULL,
    `state` VARCHAR(9) NOT NULL COMMENT 'READY: READY\nACTIVE: ACTIVE\nPAUSED: PAUSED\nCOMPLETED: COMPLETED\nSKIPPED: SKIPPED' DEFAULT 'READY',
    `target_duration_seconds` INT,
    `accumulated_duration_seconds` INT NOT NULL DEFAULT 0,
    `started_at` DATETIME(6),
    `last_paused_at` DATETIME(6),
    `target_count` INT,
    `accumulated_count` INT NOT NULL DEFAULT 0,
    `last_synced_at` DATETIME(6),
    `version` INT NOT NULL DEFAULT 1,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    `selection_id` CHAR(36) NOT NULL UNIQUE,
    CONSTRAINT `fk_challeng_daily_ca_740c2fed` FOREIGN KEY (`selection_id`) REFERENCES `daily_card_selections` (`id`) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COMMENT='문서: \"option과 1:1\". state 전이는 낙관적 잠금(version)으로 관리(문서 §6).';
        CREATE TABLE IF NOT EXISTS `challenge_events` (
    `id` CHAR(36) NOT NULL PRIMARY KEY,
    `event_type` VARCHAR(8) NOT NULL COMMENT 'START: START\nPAUSE: PAUSE\nRESUME: RESUME\nCOMPLETE: COMPLETE\nSKIP: SKIP',
    `server_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `idempotency_key` VARCHAR(100) NOT NULL UNIQUE,
    `version` INT NOT NULL,
    `payload` JSON,
    `challenge_id` CHAR(36) NOT NULL,
    CONSTRAINT `fk_challeng_challeng_d5426968` FOREIGN KEY (`challenge_id`) REFERENCES `challenges` (`id`) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COMMENT='문서: \"append-only 원본 이벤트\". 기록·리포트·포인트의 유일한 출처(문서 §2).';
        CREATE TABLE IF NOT EXISTS `point_ledger` (
    `id` CHAR(36) NOT NULL PRIMARY KEY,
    `delta` INT NOT NULL,
    `element` VARCHAR(5) NOT NULL COMMENT 'WOOD: WOOD\nFIRE: FIRE\nEARTH: EARTH\nMETAL: METAL\nWATER: WATER',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `user_id` BIGINT NOT NULL,
    `source_event_id` CHAR(36) NOT NULL UNIQUE,
    CONSTRAINT `fk_point_le_users_fc722d44` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_point_le_challeng_e670b2b2` FOREIGN KEY (`source_event_id`) REFERENCES `challenge_events` (`id`) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COMMENT='문서: \"중복 보상 0건이 불변조건\". source_event_id UNIQUE가 이걸 강제(문서 §4).';
        CREATE TABLE IF NOT EXISTS `sensor_challenge_configs` (
    `speed_threshold_kmh` DECIMAL(4,1) NOT NULL,
    `continuous_qualifying_seconds` INT NOT NULL,
    `sampling_request_interval_ms` INT NOT NULL,
    `valid_duration_seconds` INT NOT NULL DEFAULT 0,
    `mission_version` VARCHAR(30) NOT NULL,
    `challenge_id` CHAR(36) NOT NULL PRIMARY KEY,
    CONSTRAINT `fk_sensor_c_challeng_9df70e7a` FOREIGN KEY (`challenge_id`) REFERENCES `challenges` (`id`) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COMMENT='OPTIONAL 1:1. SENSOR_RUNNING 전용 설정. 문서 §2 \"설계 변경 1건\" — 센서 전용 값을';
        CREATE TABLE IF NOT EXISTS `sensor_measurement_events` (
    `id` CHAR(36) NOT NULL PRIMARY KEY,
    `measurement_type` VARCHAR(20) NOT NULL,
    `event_timestamp_ns` BIGINT,
    `speed_kmh` DECIMAL(4,1),
    `value` INT,
    `qualification_state` VARCHAR(15) COMMENT 'BELOW_THRESHOLD: BELOW_THRESHOLD\nQUALIFYING: QUALIFYING\nQUALIFIED: QUALIFIED',
    `recorded_at` DATETIME(6) NOT NULL,
    `received_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `challenge_id` CHAR(36) NOT NULL,
    CONSTRAINT `fk_sensor_m_challeng_e102c5a3` FOREIGN KEY (`challenge_id`) REFERENCES `challenges` (`id`) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COMMENT='SENSOR_RUNNING/SENSOR_STEPS/SENSOR_FLOORS_CLIMBED 공통 센서 측정 로그.';
        CREATE TABLE IF NOT EXISTS `model_releases` (
    `id` CHAR(36) NOT NULL PRIMARY KEY,
    `model_version` VARCHAR(30) NOT NULL UNIQUE,
    `released_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `is_active` BOOL NOT NULL DEFAULT 1
) CHARACTER SET utf8mb4 COMMENT='문서 3장 다이어그램: \"model_releases ||--o{ assessment_jobs : 모델 버전\".';
        CREATE TABLE IF NOT EXISTS `assessment_jobs` (
    `id` CHAR(36) NOT NULL PRIMARY KEY,
    `status` VARCHAR(7) NOT NULL COMMENT 'PENDING: PENDING\nDONE: DONE\nFAILED: FAILED' DEFAULT 'PENDING',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `health_input_snapshot_id` CHAR(36) NOT NULL,
    `model_release_id` CHAR(36) NOT NULL,
    `user_id` BIGINT NOT NULL,
    CONSTRAINT `fk_assessme_health_i_e05ddf87` FOREIGN KEY (`health_input_snapshot_id`) REFERENCES `health_input_snapshots` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_assessme_model_re_fbd399c1` FOREIGN KEY (`model_release_id`) REFERENCES `model_releases` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_assessme_users_f9927a5d` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COMMENT='문서: health_input_snapshots ||--o{ assessment_jobs (입력 스냅샷 근거).';
        CREATE TABLE IF NOT EXISTS `assessment_results` (
    `id` CHAR(36) NOT NULL PRIMARY KEY,
    `score` DECIMAL(5,2) NOT NULL,
    `band` VARCHAR(20) NOT NULL,
    `factors` JSON,
    `model_version` VARCHAR(30) NOT NULL,
    `calibration_version` VARCHAR(30),
    `copy_version` VARCHAR(30),
    `access_status` VARCHAR(10) NOT NULL COMMENT 'ACCESSIBLE: ACCESSIBLE\nRESTRICTED: RESTRICTED' DEFAULT 'ACCESSIBLE',
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `job_id` CHAR(36) NOT NULL UNIQUE,
    CONSTRAINT `fk_assessme_assessme_df7b22bd` FOREIGN KEY (`job_id`) REFERENCES `assessment_jobs` (`id`) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COMMENT='문서: assessment_jobs ||--|| assessment_results (1:1), uq_result_per_job으로 중복 방지.';
        CREATE TABLE IF NOT EXISTS `tmtn_index_results` (
    `id` CHAR(36) NOT NULL PRIMARY KEY,
    `source_result_ids` JSON NOT NULL,
    `calculated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `display_state` VARCHAR(20) NOT NULL,
    `composite_formula_version` VARCHAR(30) NOT NULL,
    `user_id` BIGINT NOT NULL,
    CONSTRAINT `fk_tmtn_ind_users_0450c836` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COMMENT='34개 ERD 요약에는 이름이 없었지만, 기능명세서 §6(틈틈지수) 확인:';
        CREATE TABLE IF NOT EXISTS `card_fit_feedback` (
    `id` CHAR(36) NOT NULL PRIMARY KEY,
    `difficulty` VARCHAR(20) NOT NULL,
    `reason_codes` JSON NOT NULL,
    `submitted_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `challenge_id` CHAR(36) NOT NULL UNIQUE,
    CONSTRAINT `fk_card_fit_challeng_47039f2c` FOREIGN KEY (`challenge_id`) REFERENCES `challenges` (`id`) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COMMENT='기능명세서 §8.2.1 확인: \"card_fit_feedback은 challenge_id·difficulty·reason_codes·';
        CREATE TABLE IF NOT EXISTS `companion_states` (
    `five_element_completion_counts` JSON NOT NULL,
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    `user_id` BIGINT NOT NULL PRIMARY KEY,
    CONSTRAINT `fk_companio_users_7c9c0524` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COMMENT='기능명세서 §8.1 확인: \"companion_states는 five_element_completion_counts를 포함하며';
        CREATE TABLE IF NOT EXISTS `mission_replacements` (
    `id` CHAR(36) NOT NULL PRIMARY KEY,
    `reason` VARCHAR(100),
    `replaced_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `from_version_id` CHAR(36) NOT NULL,
    `to_version_id` CHAR(36) NOT NULL,
    `challenge_id` CHAR(36) NOT NULL UNIQUE,
    CONSTRAINT `fk_mission__mission__d5ec9fa9` FOREIGN KEY (`from_version_id`) REFERENCES `mission_template_versions` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_mission__mission__44a2b143` FOREIGN KEY (`to_version_id`) REFERENCES `mission_template_versions` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_mission__challeng_15c4d51e` FOREIGN KEY (`challenge_id`) REFERENCES `challenges` (`id`) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COMMENT='기능명세서 §3(카드 세트) 근거 확인: \"mission_replacements는 challenge_id·from_version_id·';
        CREATE TABLE IF NOT EXISTS `recommendation_preferences` (
    `id` CHAR(36) NOT NULL PRIMARY KEY,
    `effective_service_date` DATE NOT NULL,
    `adjustments` JSON NOT NULL,
    `source_feedback_ids` JSON NOT NULL,
    `created_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `user_id` BIGINT NOT NULL,
    CONSTRAINT `fk_recommen_users_34513df1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COMMENT='기능명세서 §8.2.2 확인: \"recommendation_preferences는 effective_service_date·';
        CREATE TABLE IF NOT EXISTS `daily_action_summary` (
    `id` CHAR(36) NOT NULL PRIMARY KEY,
    `service_date` DATE NOT NULL,
    `status` VARCHAR(20) NOT NULL,
    `card_title` VARCHAR(200) NOT NULL,
    `five_element` VARCHAR(10),
    `completed_at` DATETIME(6),
    `generated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    `user_id` BIGINT NOT NULL,
    UNIQUE KEY `uid_daily_actio_user_id_e94f0d` (`user_id`, `service_date`),
    CONSTRAINT `fk_daily_ac_users_e9f5670e` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COMMENT='ERD 문서 §2: \"파생 테이블은 캐시입니다. 원본을 덮어쓰지 않고 언제든 재계산\".';
        CREATE TABLE IF NOT EXISTS `fcm_device_tokens` (
    `id` CHAR(36) NOT NULL PRIMARY KEY,
    `token` VARCHAR(255) NOT NULL UNIQUE,
    `platform` VARCHAR(10) NOT NULL DEFAULT 'ANDROID',
    `is_revoked` BOOL NOT NULL DEFAULT 0,
    `registered_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `revoked_at` DATETIME(6),
    `user_id` BIGINT NOT NULL,
    CONSTRAINT `fk_fcm_devi_users_d2198b9f` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COMMENT='ERD 문서 §6: 계정 삭제 수락 시 sessions와 함께 동기적으로 revoke됨.';
        CREATE TABLE IF NOT EXISTS `notification_settings` (
    `timezone` VARCHAR(50) NOT NULL DEFAULT 'Asia/Seoul',
    `slots` JSON NOT NULL,
    `weekdays` JSON NOT NULL,
    `quiet_hours` JSON,
    `enabled` BOOL NOT NULL DEFAULT 1,
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    `user_id` BIGINT NOT NULL PRIMARY KEY,
    CONSTRAINT `fk_notifica_users_ea1f99f3` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COMMENT='기능명세서 §1(마이·설정) 확인: \"notification_settings는 user_id·timezone·slots·';
        CREATE TABLE IF NOT EXISTS `profile_accessibility` (
    `large_controls` BOOL NOT NULL DEFAULT 0,
    `reduced_motion` BOOL NOT NULL DEFAULT 0,
    `preferred_text_scale_hint` VARCHAR(20),
    `updated_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    `user_id` BIGINT NOT NULL PRIMARY KEY,
    CONSTRAINT `fk_profile__users_53374ec1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COMMENT='기능명세서 §1(마이·설정) 확인: \"profile_accessibility는 user_id·large_controls·';
        CREATE TABLE IF NOT EXISTS `model_audit_logs` (
    `id` CHAR(36) NOT NULL PRIMARY KEY,
    `action` VARCHAR(50) NOT NULL,
    `actor` VARCHAR(100),
    `logged_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `model_release_id` CHAR(36) NOT NULL,
    CONSTRAINT `fk_model_au_model_re_52532df4` FOREIGN KEY (`model_release_id`) REFERENCES `model_releases` (`id`) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COMMENT='OPTIONAL. AI 모델(assessment 등) 배포/변경 이력 감사 로그로 추정.';
        CREATE TABLE IF NOT EXISTS `qa_release_checks` (
    `id` CHAR(36) NOT NULL PRIMARY KEY,
    `release_id` CHAR(36) NOT NULL,
    `check_name` VARCHAR(100) NOT NULL,
    `passed` BOOL NOT NULL DEFAULT 0,
    `checked_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) CHARACTER SET utf8mb4 COMMENT='OPTIONAL. 컬럼 상세 전혀 없음 — QA 체크리스트 항목별 통과여부 정도로 최소 추정.';
        CREATE TABLE IF NOT EXISTS `release_approvals` (
    `id` CHAR(36) NOT NULL PRIMARY KEY,
    `release_id` CHAR(36) NOT NULL,
    `role_key` VARCHAR(30) NOT NULL,
    `status` VARCHAR(8) NOT NULL COMMENT 'PENDING: PENDING\nAPPROVED: APPROVED\nREJECTED: REJECTED' DEFAULT 'PENDING',
    `approved_at` DATETIME(6),
    UNIQUE KEY `uid_release_app_release_b9c275` (`release_id`, `role_key`)
) CHARACTER SET utf8mb4 COMMENT='OPTIONAL. uq_approval_per_role — 같은 release에 같은 role이 중복 승인 못 함(문서 §5).';
        DROP TABLE IF EXISTS `device_tokens`;
        DROP TABLE IF EXISTS `mission_sessions`;
        DROP TABLE IF EXISTS `action_cards`;
        DROP TABLE IF EXISTS `mission_records`;"""


async def downgrade(db: BaseDBAsyncClient) -> str:
    return """
        DROP TABLE IF EXISTS `daily_card_sets`;
        DROP TABLE IF EXISTS `deletion_job_items`;
        DROP TABLE IF EXISTS `daily_action_summary`;
        DROP TABLE IF EXISTS `sensor_challenge_configs`;
        DROP TABLE IF EXISTS `qa_release_checks`;
        DROP TABLE IF EXISTS `model_releases`;
        DROP TABLE IF EXISTS `mission_replacements`;
        DROP TABLE IF EXISTS `sensor_measurement_events`;
        DROP TABLE IF EXISTS `challenge_events`;
        DROP TABLE IF EXISTS `daily_card_selections`;
        DROP TABLE IF EXISTS `profile_accessibility`;
        DROP TABLE IF EXISTS `notification_settings`;
        DROP TABLE IF EXISTS `password_reset_tokens`;
        DROP TABLE IF EXISTS `tmtn_index_results`;
        DROP TABLE IF EXISTS `card_options`;
        DROP TABLE IF EXISTS `release_approvals`;
        DROP TABLE IF EXISTS `recommendation_preferences`;
        DROP TABLE IF EXISTS `fcm_device_tokens`;
        DROP TABLE IF EXISTS `companion_states`;
        DROP TABLE IF EXISTS `sessions`;
        DROP TABLE IF EXISTS `assessment_results`;
        DROP TABLE IF EXISTS `email_verification_requests`;
        DROP TABLE IF EXISTS `card_fit_feedback`;
        DROP TABLE IF EXISTS `point_ledger`;
        DROP TABLE IF EXISTS `assessment_jobs`;
        DROP TABLE IF EXISTS `user_consents`;
        DROP TABLE IF EXISTS `health_input_snapshots`;
        DROP TABLE IF EXISTS `mission_template_versions`;
        DROP TABLE IF EXISTS `model_audit_logs`;
        DROP TABLE IF EXISTS `deletion_jobs`;
        DROP TABLE IF EXISTS `challenges`;"""


MODELS_STATE = (
    "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"
    "+fvJS6yQURkRrYurj1BcfVQz8u//D2AHRD4="
)
