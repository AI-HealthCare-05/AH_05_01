"""미션 실천 점수 산식.

⚠️ 2026-09-15 이식 - 문홍주 팀장님(모델) 전달본(practice.py) 원본 그대로 복사함.
daily_units/trajectory/policy 로직은 한 글자도 안 고침 - 요청서에 "수학식을 새로
근사하거나 포인트를 최종 지수에 직접 더하지 말아 주세요"라고 명시돼 있어서, 산식은
검증된 원본을 그대로 쓰고 호출부(practice_score_service.py)만 새로 작성함.
버전: practice-preserved-cumulative-v1-20260915"""
import math

VERSION = 'practice-preserved-cumulative-v1-20260915'


def policy():
    return dict(version=VERSION, decision='user_selected_2026-09-15',
                deploymentStatus='local_only', cumulativeWeight=.8,
                maintenanceWeight=.2, cumulativeScale=60, maintenanceBuildScale=10,
                confirmedRestGraceDays=7, dailyMaintenanceRetention=.98,
                unknownDays='preserve_score_request_update_after_7_days',
                maxDailyUnits=1.5, additionalMinutesPerUnit=20,
                standaloneBaseMinutes=5, missionBaseUnits=1,
                clinicalMeaning='product_practice_points_only')


def daily_units(events):  # noqa: C901 - 원본 산식 그대로, 복잡도 이유로 리팩토링 금지
    """One day's eligible exercise records, already authorized by mission catalog.

    Caller must establish actual completion, unique real sessions and no overlap.
    sessionId is the real activity, not a mission or log ID. This function cannot
    detect a user assigning two IDs to one activity. No nonexercise cards allowed.
    """
    seen = {}
    for e in events:
        if set(e) != {'sessionId', 'kind', 'minutes'}:
            raise ValueError('INVALID_EVENT_FIELDS')
        if not isinstance(e['sessionId'], str) or not e['sessionId'].strip():
            raise ValueError('INVALID_SESSION_ID')
        if e['kind'] not in ('mission', 'additional'):
            raise ValueError('INVALID_KIND')
        m = e['minutes']
        if m is not None and (type(m) not in (float, int) or not math.isfinite(m) or not 0 < m <= 1440):
            raise ValueError('INVALID_MINUTES')
        if e['kind'] == 'additional' and m is None:
            raise ValueError('ADDITIONAL_DURATION_REQUIRED')
        if e['sessionId'] in seen and seen[e['sessionId']] != e:
            raise ValueError('CONFLICTING_SESSION')
        seen[e['sessionId']] = dict(e)
    records = list(seen.values())
    if not records:
        return 0.0
    if sum(e['minutes'] or 0 for e in records) > 1440:
        raise ValueError('IMPOSSIBLE_DAILY_DURATION')
    # A first eligible mission earns one unit even if no duration was captured.
    # Standalone additional practice earns a base unit over five minutes, then
    # a bonus. Aggregation prevents rewards from splitting a session into logs.
    missions = [e for e in records if e['kind'] == 'mission']
    if not missions:
        total = sum(e['minutes'] for e in records)
        return min(total / 5, 1) + min(max(total - 5, 0) / 20, .5)
    base = min(missions, key=lambda e: (e['minutes'] or 0, e['sessionId']))
    extra_minutes = sum(e['minutes'] or 0 for e in records if e['sessionId'] != base['sessionId'])
    return 1.0 + min(extra_minutes / 20.0, 0.5)


def trajectory(days):
    """Ordered daily records: active, confirmed_rest, or unknown.

    Unknown days freeze score and break a confirmed-rest run. A stale flag is
    returned, not evidence of inactivity. Recompute from history on corrections.
    """
    weight = .2
    units = momentum = 0.0
    rest_run = unknown_run = 0
    rows = []
    for day, record in enumerate([{'status': 'origin', 'events': []}] + list(days)):
        if set(record) != {'status', 'events'} or not isinstance(record['events'], list):
            raise ValueError('INVALID_DAY')
        status = record['status']
        if status not in ('active', 'confirmed_rest', 'unknown') and day != 0:
            raise ValueError('INVALID_DAY_STATUS')
        earned = daily_units(record['events'])
        if (status == 'active') != (earned > 0):
            if day != 0:
                raise ValueError('DAY_EVENT_MISMATCH')
        if earned:
            units += earned
            momentum = 100 - (100 - momentum) * math.exp(-earned / 10)
            rest_run = unknown_run = 0
        elif status == 'confirmed_rest':
            rest_run += 1
            unknown_run = 0
            if rest_run > 7:
                momentum *= 0.98
        elif status == 'unknown':
            unknown_run += 1
            rest_run = 0
        cumulative = 100 * (-math.expm1(-units / 60))
        p = (1 - weight) * cumulative + weight * momentum
        rows.append(dict(day=day, status=status, dailyUnits=earned, cumulativeUnits=units,
                         cumulativeScore=cumulative, maintenanceScore=momentum, practiceScore=p,
                         confirmedRestRun=rest_run, unknownRun=unknown_run,
                         freshness='needs_activity_update' if unknown_run >= 7 else 'current_or_recent',
                         version=VERSION, policyStatus='user_selected_local_only'))
    return rows
