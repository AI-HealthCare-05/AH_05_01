"""초기 습관 점수 산식(저강도 20·중고강도 50·근력 30).

⚠️ 2026-09-16 이식 - 강호님(모델) "초기습관_산식과_모델표시_확정_v1" 전달본
(initial_habit.py) 원본 그대로 복사함. calculate() 로직은 한 글자도 안 고침.
버전: initial-habit-20-50-30-v1-20260916"""
import math

VERSION = 'initial-habit-20-50-30-v1-20260916'
DEFINITION = 'leisure_bouts10_strength_days_v1'


def calculate(*, low_minutes, moderate_minutes, vigorous_minutes,
              strength_days, strength_intensity, input_revision,
              input_definition):
    if not isinstance(input_revision, str) or not input_revision.strip():
        raise ValueError('INPUT_REVISION_REQUIRED')
    if input_definition != DEFINITION:
        raise ValueError('INPUT_DEFINITION_UNCONFIRMED')
    fields = dict(low_minutes=low_minutes, moderate_minutes=moderate_minutes,
                  vigorous_minutes=vigorous_minutes, strength_days=strength_days)
    missing = [k for k, v in fields.items() if v is None]
    for k, v in fields.items():
        if v is None:
            continue
        if type(v) not in (int, float) or not math.isfinite(v) or v < 0:
            raise ValueError('INVALID_' + k.upper())
        if k == 'strength_days':
            if v != int(v) or v > 7:
                raise ValueError('INVALID_STRENGTH_DAYS')
        elif v > 10080:
            raise ValueError('INVALID_WEEKLY_MINUTES')
    minutes = [low_minutes, moderate_minutes, vigorous_minutes]
    if all(v is not None for v in minutes) and sum(minutes) > 10080:
        raise ValueError('INVALID_WEEKLY_TOTAL')
    if strength_intensity not in (None, 'none', 'light', 'moderate', 'hard'):
        raise ValueError('INVALID_STRENGTH_INTENSITY')
    if strength_days == 0 and strength_intensity not in (None, 'none'):
        raise ValueError('INCONSISTENT_STRENGTH_INPUT')
    if strength_days and strength_intensity in (None, 'none'):
        missing.append('strength_intensity')
    contributions = None if missing else dict(
        low=20 * min(low_minutes / 150, 1),
        moderate_vigorous=50 * min((moderate_minutes + 2 * vigorous_minutes) / 150, 1),
        strength=30 * min(strength_days / 2, 1))
    return dict(formula_version=VERSION, input_revision=input_revision,
                input_definition=input_definition, policy_status='approved',
                score=None if missing else sum(contributions.values()),
                contributions=contributions, missing_inputs=missing,
                value_type='product_activity_points')
