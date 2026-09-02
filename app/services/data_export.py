import csv
import io

from app.models.cards import DailyCardSet
from app.models.health import ExerciseHabitSnapshot, HealthInputSnapshot
from app.models.records import DailyRecordNote
from app.models.users import User


class DataExportService:
    """F14: 내 데이터 내보내기.

    ⚠️ Figma는 "요청 -> 파일 준비되면 알림 -> 7일짜리 다운로드 링크"인 비동기 작업
    (S3 업로드, 알림 발송, 만료 링크 발급)까지 그리는데, 그건 별도 파일 저장소·큐가
    필요한 인프라 작업이라 이번 스코프 밖. 지금은 요청 즉시 CSV를 만들어서 그 자리에서
    바로 내려주는 동기 버전으로 우선 구현함. 나중에 데이터가 많아지면 비동기로 바꿀 것.
    """

    async def build_csv(self, user: User) -> str:
        buffer = io.StringIO()
        writer = csv.writer(buffer)

        writer.writerow(["구분", "날짜", "제목", "완료여부", "걸린시간(초)", "메모"])

        card_sets = await DailyCardSet.filter(user=user).prefetch_related("selection__challenge")
        for card_set in card_sets:
            selection = getattr(card_set, "selection", None)
            challenge = getattr(selection, "challenge", None) if selection else None
            if challenge is None:
                continue
            title = (challenge.mission_snapshot or {}).get("title", "")
            done = "완료" if challenge.state == "COMPLETED" else "미완료"
            writer.writerow(
                ["행동기록", str(card_set.service_date), title, done, challenge.accumulated_duration_seconds, ""]
            )

        notes = await DailyRecordNote.filter(user=user)
        for note in notes:
            writer.writerow(["메모", str(note.service_date), "", "쉼" if note.is_rest_day else "", "", note.memo or ""])

        health_snapshots = await HealthInputSnapshot.filter(user=user).order_by("-measured_at")
        for snap in health_snapshots:
            writer.writerow(["입력값(몸정보)", str(snap.measured_at), str(snap.input_values), "", "", ""])

        exercise_snapshots = await ExerciseHabitSnapshot.filter(user=user).order_by("-recorded_at")
        for snap in exercise_snapshots:
            summary = (
                f"근력주{snap.strength_weekly_count}회 "
                f"유산소저{snap.aerobic_low_minutes}분/중{snap.aerobic_moderate_minutes}분/고{snap.aerobic_high_minutes}분"
            )
            writer.writerow(["입력값(운동정보)", str(snap.recorded_at), summary, "", "", ""])

        writer.writerow(["계정", str(user.created_at), user.email, "", "", ""])

        return buffer.getvalue()
