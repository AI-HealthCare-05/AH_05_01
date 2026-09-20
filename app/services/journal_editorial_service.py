"""Select source-checked, approved copy; never generate medical prose at request time.

Catalog and approvals are deployment-owned files. No query parameter, client flag,
LLM output or article's own review_status can grant publication permission.
"""

import hashlib
import json
import logging
from datetime import date
from pathlib import Path
from urllib.parse import urlsplit

from app.core import config
from app.core.config import Env
from app.core.time_utils import service_today
from app.dtos.journal_editorial import (
    JournalEditorialResponse,
    JournalKnowledgeArticle,
    JournalReadingSection,
    JournalSource,
)
from app.models.users import User

logger = logging.getLogger(__name__)
CATALOG_DIR = Path(__file__).resolve().parents[1] / "data" / "journal"
CONTENT_KEYS = (
    "id",
    "section",
    "topic",
    "title",
    "text",
    "source_id",
    "locator",
    "allowed_claim_scope",
    "audience_min_age",
    "audience_max_age",
    "revision",
    "numeric_slot",
)
SOURCE_HOSTS = frozenset(
    {
        "www.foodsafetykorea.go.kr",
        "health.kdca.go.kr",
        "www.mohw.go.kr",
        "health.seoulmc.or.kr",
        "www.nhs.uk",
        "www.who.int",
        "www.cdc.gov",
        "www.niddk.nih.gov",
    }
)
RELATED_READING_IDS = {
    "diabetes": ["table.carbs_vs_sugars", "daily.a1c", "table.juice", "daily.glucose_meter", "movement.walk_habit"],
    "hypertension": ["daily.bp_prepare", "table.hidden_salt", "daily.bp_posture", "table.flavour", "daily.bp_log"],
}


def reading_cycle(articles):
    """Interleave topics so a series of sleep articles does not occupy every day."""
    groups = {}
    for article in sorted(articles, key=lambda a: a.id):
        groups.setdefault(article.topic or article.id, []).append(article)
    return [
        group[i]
        for i in range(max((len(g) for g in groups.values()), default=0))
        for group in groups.values()
        if i < len(group)
    ]


def content_hash(item: dict) -> str:
    canonical = {key: item[key] for key in CONTENT_KEYS}
    return hashlib.sha256(
        json.dumps(canonical, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()
    ).hexdigest()


def valid_source_url(url: str) -> bool:
    try:
        parsed = urlsplit(url)
        return (
            parsed.scheme == "https"
            and parsed.hostname in SOURCE_HOSTS
            and not parsed.username
            and not parsed.password
            and parsed.port in (None, 443)
        )
    except (TypeError, ValueError):
        return False


def age_bounds(user: User, today: date) -> tuple[int, int] | None:
    """Birth day is not collected. Restricted copy must fit every possible age."""
    year, month = user.birth_year, user.birth_month
    if type(year) is not int or not 1900 <= year <= today.year:
        return None
    age = today.year - year
    if type(month) is not int or not 1 <= month <= 12 or month == today.month:
        return age - 1, age
    age -= int(month > today.month)
    return age, age


def eligibility_errors(
    item: dict,
    source: dict,
    approval: dict,
    *,
    today: date,
    ages: tuple[int, int] | None,
    preview: bool = False,
    internal_review: bool = False,
) -> list[str]:
    errors = []
    try:
        if content_hash(item) != item["content_sha256"]:
            errors.append("content_changed")
        if source["id"] != item["source_id"] or source["evidence_status"] != "source_checked":
            errors.append("source_unverified")
        if not valid_source_url(source["url"]):
            errors.append("source_url_invalid")
        if not date.fromisoformat(source["checked_on"]) <= today <= date.fromisoformat(source["recheck_by"]):
            errors.append("source_review_due")
        low, high = item["audience_min_age"], item["audience_max_age"]
        if low is not None or high is not None:
            if ages is None or (low is not None and ages[0] < low) or (high is not None and ages[1] > high):
                errors.append("audience_mismatch")
        if not item["title"].strip() or not item["text"].strip() or not item["locator"].strip():
            errors.append("empty_copy")
        if not preview:
            errors.extend(approval_errors(item, source, approval, today, internal_review=internal_review))
    except (KeyError, TypeError, ValueError, AttributeError):
        errors.append("invalid_catalog_record")
    return errors


def approval_errors(
    item: dict, source: dict, approval: dict, today: date, *, internal_review: bool = False
) -> list[str]:
    errors = []
    internal_approval = (
        internal_review
        and approval.get("scope") == "team_internal_demo"
        and date.fromisoformat(approval.get("approved_on", "9999-12-31")) <= today
    )
    if approval.get("scope") == "team_internal_demo" and not internal_approval:
        errors.append("internal_only")
    if not (
        approval.get("status") == "approved"
        and approval.get("reviewer")
        and approval.get("approval_id")
        and all(approval.get(key) == item[key] for key in ("id", "revision", "content_sha256", "source_id"))
    ):
        errors.append("content_unapproved")
    if approval.get("source_edition") != source["edition"] or (
        not internal_approval
        and not (approval.get("rights_status") == "cleared_for_app_summary" and approval.get("rights_review_id"))
    ):
        errors.append("source_use_unconfirmed")
    if today > date.fromisoformat(approval.get("expires_on", "0001-01-01")):
        errors.append("approval_expired")
    return errors


def select_editorial(
    catalog: list,
    sources: list,
    approvals: list,
    *,
    today: date,
    ages: tuple[int, int] | None,
    preview: bool = False,
    internal_review: bool = False,
) -> JournalEditorialResponse:
    """Preview is an offline export facility, never exposed by the HTTP route."""
    source_map = {source["id"]: source for source in sources}
    approval_map = {approval["id"]: approval for approval in approvals}
    if (
        len(source_map) != len(sources)
        or len(approval_map) != len(approvals)
        or len({item["id"] for item in catalog}) != len(catalog)
    ):
        raise ValueError("Duplicate journal IDs")
    sections = []
    all_eligible = {}
    for section in ("movement_column", "table_column", "daily_column"):
        articles = []
        for item in catalog:
            if item.get("section") != section:
                continue
            source = source_map.get(item.get("source_id"), {})
            if eligibility_errors(
                item,
                source,
                approval_map.get(item.get("id"), {}),
                today=today,
                ages=ages,
                preview=preview,
                internal_review=internal_review,
            ):
                continue
            articles.append(
                JournalKnowledgeArticle(
                    **{
                        key: item[key]
                        for key in ("id", "section", "title", "text", "revision", "content_sha256", "numeric_slot")
                    },
                    source=JournalSource(**{**source, "locator": item["locator"]}),
                    **{
                        key: item.get(key)
                        for key in ("topic", "allowed_claim_scope", "audience_min_age", "audience_max_age")
                    },
                )
            )
        # Same date + same approved catalog gives the same issue across refreshes/devices.
        all_eligible.update({article.id: article for article in articles})
        articles = reading_cycle(articles)
        if articles and not preview:
            articles = [articles[today.toordinal() % len(articles)]]
        sections.append(
            JournalReadingSection(section=section, status="ready" if articles else "empty", articles=articles)
        )
    related = {}
    for domain, ids in RELATED_READING_IDS.items():
        available = [all_eligible[key] for key in ids if key in all_eligible]
        if available:
            start = today.toordinal() % len(available)
            related[domain] = (available[start:] + available[:start])[:2]
    return JournalEditorialResponse(service_date=today, preview=preview, sections=sections, related_readings=related)


def load_catalog(directory: Path = CATALOG_DIR, *, internal_review: bool = False) -> tuple[list, list, list]:
    def read(name: str):
        return json.loads((directory / name).read_text(encoding="utf-8-sig"))

    # 배포자가 승인 파일을 아직 제공하지 않았다면 공개할 문장은 없다.
    approvals = read("approvals.json")["knowledge"] if (directory / "approvals.json").exists() else []
    if internal_review and (directory / "internal_approvals.json").exists():
        # 사용자에게 승인받은 내부 시연 범위만 읽는다. 공개 승인·출처 이용허락을 만들어내지 않는다.
        internal = read("internal_approvals.json")["knowledge"]
        approvals = list({item["id"]: item for item in internal + approvals}.values())
    return read("knowledge.json"), read("sources.json"), approvals


class JournalEditorialService:
    async def get_editorial(self, user: User) -> JournalEditorialResponse:
        today = service_today(user.id)
        try:
            internal = config.ENV in (Env.LOCAL, Env.DEV)
            return select_editorial(
                *load_catalog(internal_review=internal),
                today=today,
                ages=age_bounds(user, today),
                internal_review=internal,
            )
        except (OSError, ValueError, TypeError, KeyError):
            # No personal data or raw catalog text in logs; records still work.
            logger.warning("Journal catalog unavailable; serving empty reading sections")
            return select_editorial([], [], [], today=today, ages=None)
