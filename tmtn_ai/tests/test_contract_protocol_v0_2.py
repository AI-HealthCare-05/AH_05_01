from pathlib import Path

import yaml


PROJECT_ROOT = Path(__file__).resolve().parents[1]
CUSTODIAN_CONTRACT_PATH = (
    PROJECT_ROOT / "contracts" / "custodian_2023_operation_contract_v0_2.yaml"
)
EXPERIMENT_PROTOCOL_PATH = (
    PROJECT_ROOT / "contracts" / "model_experiment_protocol_v0_1.yaml"
)


def _load(path: Path) -> dict:
    return yaml.safe_load(path.read_text(encoding="utf-8"))


def test_review_contracts_remain_locked() -> None:
    custodian = _load(CUSTODIAN_CONTRACT_PATH)
    experiment = _load(EXPERIMENT_PROTOCOL_PATH)

    assert custodian["do_not_materialize"] is True
    assert custodian["do_not_score"] is True
    assert custodian["do_not_release_results"] is True
    assert experiment["do_not_train"] is True
    assert experiment["do_not_open_2022_before_freeze"] is True
    assert experiment["do_not_access_2023"] is True
    assert experiment["do_not_access_2024"] is True


def test_year_roles_are_disjoint_and_match_the_joint_direction() -> None:
    experiment = _load(EXPERIMENT_PROTOCOL_PATH)
    roles = experiment["year_roles"]

    development = set(roles["development"]["years"])
    temporal = set(roles["temporal_validation"]["years"])
    benchmark = set(roles["opened_internal_benchmark"]["years"])
    forbidden = set(roles["forbidden"]["years"])

    assert development == {2019, 2020, 2021}
    assert temporal == {2022}
    assert benchmark == {2023}
    assert forbidden == {2024}
    all_roles = [development, temporal, benchmark, forbidden]
    assert all(not left & right for i, left in enumerate(all_roles) for right in all_roles[i + 1 :])


def test_six_feature_contract_matches_between_documents() -> None:
    custodian = _load(CUSTODIAN_CONTRACT_PATH)
    experiment = _load(EXPERIMENT_PROTOCOL_PATH)

    assert custodian["six_feature_schema"] == experiment["primary_features"]["F2_leisure"]
    assert custodian["six_feature_schema"] == [
        "age_years",
        "sex_code",
        "height_cm",
        "weight_kg",
        "leisure_aerobic_moderate_equivalent_min_week",
        "strength_days_week",
    ]


def test_2023_rows_stay_inside_the_custodian_environment() -> None:
    custodian = _load(CUSTODIAN_CONTRACT_PATH)

    assert custodian["purpose"]["dataset_role"] == "opened_internal_benchmark"
    assert custodian["purpose"]["independent_final_holdout"] is False
    assert custodian["custodian_storage"]["feature_root"]["developer_export"] == "prohibited"
    assert custodian["custodian_storage"]["prediction_root"]["developer_export"] == "prohibited"
    prohibited = set(custodian["roles"]["developers"]["prohibited_access"])
    assert "row_level_2023_features" in prohibited
    assert "row_level_2023_labels" in prohibited
    assert "row_level_2023_predictions" in prohibited


def test_inference_and_evaluation_have_separate_label_boundaries() -> None:
    custodian = _load(CUSTODIAN_CONTRACT_PATH)
    stages = {stage["stage"]: stage for stage in custodian["execution_stages"]}

    assert stages["inference"]["label_access"] == "prohibited"
    assert "custodian_task_labels" not in stages["inference"]["inputs"]
    assert "custodian_task_labels" in stages["evaluation"]["inputs"]
    assert custodian["frozen_inference_bundle"]["label_path_visible_to_inference_entrypoint"] == "prohibited"


def test_2022_is_evaluation_only_and_calibration_uses_development_oof() -> None:
    experiment = _load(EXPERIMENT_PROTOCOL_PATH)

    temporal = experiment["year_roles"]["temporal_validation"]
    assert temporal["role"] == "evaluation_only_after_full_development_freeze"
    assert "model_selection" in temporal["prohibited"]
    assert "calibration_fit" in temporal["prohibited"]
    assert experiment["calibration"]["fit_source"] == (
        "2019_2021_cross_fitted_out_of_fold_predictions_only"
    )
    assert experiment["calibration"]["fit_on_2022"] == "prohibited"
    assert experiment["freeze_and_bundle"]["include_2022_in_refit"] is False


def test_current_materializer_must_be_refactored_before_authorization() -> None:
    custodian = _load(CUSTODIAN_CONTRACT_PATH)

    assert custodian["upstream"]["current_materializer_status"] == (
        "refactor_required_before_authorization"
    )
    required = set(custodian["activation_gate"]["required"])
    assert "materializer_refactored_for_custodian_only_features" in required
    assert custodian["activation_gate"]["current"] == "blocked"

