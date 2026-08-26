from pathlib import Path

import yaml


PROJECT_ROOT = Path(__file__).resolve().parents[1]
PROTOCOL_PATH = PROJECT_ROOT / "contracts" / "model_experiment_protocol_v0_2.yaml"
CUSTODIAN_PATH = PROJECT_ROOT / "contracts" / "custodian_2023_operation_contract_v0_2.yaml"


def _load(path: Path) -> dict:
    return yaml.safe_load(path.read_text(encoding="utf-8"))


def test_jointly_approved_protocol_still_requires_separate_authorization() -> None:
    protocol = _load(PROTOCOL_PATH)

    assert protocol["status"] == "jointly_approved"
    assert protocol["authorization"]["review_contract_do_not_train"] is True
    assert protocol["authorization"]["training_requires_separate_authorized_config"] is True
    assert protocol["authorization"]["kangho"]["status"] == "approved"
    assert protocol["authorization"]["byeonghak"]["status"] == "approved"
    assert protocol["authorization"]["byeonghak"]["approved_at"].isoformat() == "2026-08-26"


def test_year_roles_are_disjoint_and_2022_is_evaluation_only() -> None:
    protocol = _load(PROTOCOL_PATH)
    roles = protocol["year_roles"]

    development = set(roles["development"]["years"])
    evaluation = set(roles["evaluation_only_temporal_validation"]["years"])
    benchmark = set(roles["opened_internal_benchmark"]["years"])
    forbidden = set(roles["forbidden"]["years"])

    assert development == {2019, 2020, 2021}
    assert evaluation == {2022}
    assert benchmark == {2023}
    assert forbidden == {2024}
    sets = [development, evaluation, benchmark, forbidden]
    assert all(not left & right for i, left in enumerate(sets) for right in sets[i + 1 :])
    assert roles["evaluation_only_temporal_validation"]["fitting"] == "prohibited"
    assert roles["evaluation_only_temporal_validation"]["model_or_threshold_selection"] == "prohibited"


def test_six_feature_schema_matches_custodian_contract() -> None:
    protocol = _load(PROTOCOL_PATH)
    custodian = _load(CUSTODIAN_PATH)

    assert protocol["features"]["F2_leisure"] == custodian["six_feature_schema"]


def test_numeric_release_gates_are_frozen() -> None:
    protocol = _load(PROTOCOL_PATH)
    waist = protocol["waist_submodel"]["release_gate_2022"]
    disease = protocol["disease_models"]["release_gate_2022"]

    assert waist["overall_mae_cm_max"] == 4.0
    assert waist["overall_mae_95ci_upper_max"] == 4.5
    assert waist["overall_rmse_cm_max"] == 5.5
    assert waist["supported_subgroup_mae_ratio_to_overall_max"] == 1.25
    assert disease["roc_auc_min"] == 0.65
    assert disease["roc_auc_95ci_lower_min"] == 0.60
    assert disease["calibration_intercept_abs_max"] == 0.10
    assert disease["calibration_slope_min"] == 0.80
    assert disease["calibration_slope_max"] == 1.20
    assert disease["ece_10bin_max"] == 0.05


def test_survey_weight_and_uncertainty_policy_are_frozen() -> None:
    protocol = _load(PROTOCOL_PATH)
    survey = protocol["survey_design_evaluation"]
    uncertainty = protocol["uncertainty"]

    assert survey["pooled_weight"]["development_2019_2021"] == "wt_itvex_divided_by_3"
    assert survey["missing_or_nonpositive_weight"]["imputation"] == "prohibited"
    assert survey["valid_weight_coverage"]["minimum"] == 0.95
    assert uncertainty["method"] == "rao_wu_rescaled_bootstrap"
    assert uncertainty["resampling_unit"] == "psu"
    assert uncertainty["replicates"] == 2000
    assert uncertainty["minimum_valid_replicates"] == 1900


def test_serving_contract_never_coerces_unsupported_values() -> None:
    protocol = _load(PROTOCOL_PATH)
    serving = protocol["serving_input_contract"]

    assert serving["height_cm"]["outside_policy"] == "unsupported_without_clipping"
    assert serving["weight_kg"]["outside_policy"] == "unsupported_without_clipping"
    assert serving["strength_days_week"]["allowed"] == [0, 1, 2, 3, 4, "5_plus"]
    assert serving["strength_days_week"]["app_four_plus_mapping_to_five_plus"] == "prohibited"
    assert serving["missing_data"]["zero_or_mean_imputation"] == "prohibited"


def test_2023_contract_remains_custodian_only() -> None:
    protocol = _load(PROTOCOL_PATH)
    custodian = _load(CUSTODIAN_PATH)

    assert protocol["custodian_2023"]["status"] == "opened_internal_benchmark"
    assert protocol["custodian_2023"]["row_level_storage"] == "custodian_only"
    assert custodian["custodian_storage"]["feature_root"]["developer_export"] == "prohibited"
    assert custodian["custodian_storage"]["prediction_root"]["developer_export"] == "prohibited"
