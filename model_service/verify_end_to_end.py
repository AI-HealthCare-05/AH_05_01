"""Authenticated FastAPI route -> real model worker -> validated personal snapshot.

Uses synthetic in-memory repositories only. Never connects to the application DB.
"""

# ruff: noqa: E402 -- standalone verifier adds the repository before importing the API

import argparse
import asyncio
import json
import os
import secrets
import subprocess
import sys
from datetime import UTC, date, datetime, timedelta
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import AsyncMock

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from httpx import ASGITransport, AsyncClient

from app.core import config
from app.dependencies.security import get_request_user
from app.main import app
from app.services.personal_xai_service import PersonalXaiService, personal_jobs
from app.services.weekly_xai_service import WeeklyXaiService
from model_service.shap_story import make_story


class HistoryRepo:
    """합성 연결 검증에서만 쓰는 원장. 실제 DB에는 접속하지 않는다."""

    def __init__(self):
        self.rows = {}

    async def save(self, user_id, snapshot):
        observed = date.fromisoformat(snapshot.reference_date)
        monday = observed - timedelta(days=observed.weekday())
        self.rows[(user_id, monday)] = SimpleNamespace(week_start=monday, snapshot=snapshot.model_dump(mode="json"))

    async def list_since(self, user_id, start, end):
        return [row for (owner, week), row in self.rows.items() if owner == user_id and start <= week <= end]


class Repo:
    def __init__(self, value):
        self.value = value

    async def get_latest(self, _):
        return self.value

    async def get_latest_by_purpose(self, _, purpose):
        return self.value


async def verify(out):
    service = PersonalXaiService()
    service.history_repo = HistoryRepo()
    history = WeeklyXaiService()
    history.history_repo = service.history_repo
    history.record_repo.get_card_sets_in_range = AsyncMock(return_value=[])
    history.record_repo.get_notes_in_range = AsyncMock(return_value={})
    user = SimpleNamespace(id=900000001, birth_year=1991, birth_month=1, gender="MALE", is_pregnant=None)
    service.health_repo = Repo(
        SimpleNamespace(id="synthetic-health-64", input_values={"height_cm": 170, "weight_kg": 64})
    )
    service.habit_repo = Repo(
        SimpleNamespace(
            id="synthetic-habit",
            strength_weekly_count=4,
            strength_frequency_unit="days",
            strength_intensity="MODERATE",
            aerobic_low_minutes=60,
            aerobic_moderate_minutes=120,
            aerobic_high_minutes=0,
            recorded_at=datetime(2026, 9, 1, 3, 0, tzinfo=UTC),
        )
    )
    service.consent_repo = Repo(SimpleNamespace(status="AGREED"))
    history.consent_repo = service.consent_repo
    out.mkdir(parents=True, exist_ok=True)
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        assert (await client.get("/api/v1/tuntun-score/personal")).status_code == 401
        app.dependency_overrides[get_request_user] = lambda: user
        app.dependency_overrides[PersonalXaiService] = lambda: service
        app.dependency_overrides[WeeklyXaiService] = lambda: history

        async def poll(name):
            first = await client.get("/api/v1/tuntun-score/personal")
            assert first.json()["status"] == "pending"
            immediate = first.json()["activity_comparison"]
            assert immediate["status"] == "ready" and immediate["group_key"] == "19-39:1"
            assert immediate["survey_recorded_at"] == "2026-09-01T03:00:00Z"
            assert first.headers["cache-control"] == "no-store"
            for _ in range(90):
                await asyncio.sleep(1)
                response = await client.get("/api/v1/tuntun-score/personal")
                body = response.json()
                if body["status"] != "pending":
                    assert body["status"] == "ready", body.get("reason")
                    assert body["activity_comparison"] == body["snapshot"]["activity_comparison"]
                    assert immediate == body["activity_comparison"]
                    (out / (name + ".synthetic.json")).write_text(
                        json.dumps(body, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
                    )
                    print(name + ": authenticated real calculation ready", flush=True)
                    return body["snapshot"]
            raise AssertionError("Personal calculation timed out")

        first = await poll("base")
        service.health_repo.value = SimpleNamespace(
            id="synthetic-health-72", input_values={"height_cm": 170, "weight_kg": 72}
        )
        second = await poll("changed")
        assert first["snapshot_id"] != second["snapshot_id"] and first["input_revision"] != second["input_revision"]
        assert first["domains"][0]["contributions"] != second["domains"][0]["contributions"]
        weekly = await client.get("/api/v1/tuntun-score/personal/history")
        assert weekly.status_code == 200 and weekly.headers["cache-control"] == "no-store"
        point = weekly.json()["points"][-1]
        assert point["status"] == "recorded" and point["diabetes"] == second["domains"][0]["output_value"]
        assert len(service.history_repo.rows) == 1
        for snapshot in (first, second):
            for domain in snapshot["domains"]:
                assert domain["narrative"] == make_story(snapshot["domains"], domain["domain"])
        assert first["domains"][0]["narrative"]["summary"] != first["domains"][1]["narrative"]["summary"]
        service.consent_repo.value.status = "WITHDRAWN"
        withdrawn = (await client.get("/api/v1/tuntun-score/personal")).json()
        assert withdrawn["reason"] == "consent_required" and withdrawn["activity_comparison"] is None
        assert not any(key[0] == user.id for key in personal_jobs.jobs)
        assert (await client.get("/api/v1/tuntun-score/personal/history")).json()["reason"] == "consent_required"
        app.dependency_overrides.clear()
    report = {
        "synthetic_only": True,
        "real_model_worker": True,
        "authentication_required": True,
        "async_job_polling": True,
        "activity_available_before_shap": True,
        "activity_matches_final_normalized_input": True,
        "saved_survey_date_preserved": True,
        "source_input_change_invalidates_snapshot": True,
        "narratives_match_current_numeric_shap": True,
        "diabetes_and_hypertension_have_distinct_summaries": True,
        "consent_withdrawal_hides_and_clears_result": True,
        "no_store_header": True,
        "database_used": False,
        "weekly_history_matches_latest_real_calculation": True,
        "weekly_history_hidden_after_withdrawal": True,
    }
    (out / "verification.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime", type=Path, required=True)
    parser.add_argument("--payload", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--port", type=int, default=0)
    args = parser.parse_args()
    token = secrets.token_urlsafe(48)
    env = {
        **os.environ,
        "TUNTUN_XAI_TOKEN": token,
        "NUMBA_CACHE_DIR": str(args.out.resolve() / "numba-cache"),
        "MPLCONFIGDIR": str(args.out.resolve() / "mpl-cache"),
    }
    config.TUNTUN_XAI_URL, config.TUNTUN_XAI_TOKEN, config.ENV = f"http://127.0.0.1:{args.port}", token, "local"
    process = subprocess.Popen(
        [
            str(args.runtime),
            "-B",
            str(ROOT / "model_service/server.py"),
            "--payload",
            str(args.payload),
            "--port",
            str(args.port),
        ],
        env=env,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        encoding="utf-8",
        creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
    )
    try:
        line = process.stdout.readline()
        if "worker ready" not in line:
            raise RuntimeError("Model worker failed to start: " + process.stderr.read()[-1500:])
        config.TUNTUN_XAI_URL = "http://" + line.strip().split(" on ")[-1]
        print("Local model worker loaded; verifying authenticated route", flush=True)
        asyncio.run(verify(args.out))
        print("End-to-end verification passed", flush=True)
    finally:
        process.terminate()
        process.wait(timeout=15)
        diagnostics = process.stderr.read()
        if diagnostics:
            print(diagnostics[-2000:])
