package kr.tmtn.app.data

import android.content.Context
import android.content.SharedPreferences
import kr.tmtn.app.domain.ml.Sex
import kr.tmtn.app.domain.model.Axis
import kr.tmtn.app.domain.model.DailyRecord
import kr.tmtn.app.domain.model.MissionType
import kr.tmtn.app.domain.model.UserProfile
import org.json.JSONArray
import org.json.JSONObject

/**
 * MVP 로컬 저장소.
 *
 * 지금은 SharedPreferences 하나로 끝낸다. 서버가 붙으면 이 클래스 뒤로 API 를 넣고
 * 화면은 그대로 두면 된다.
 *
 * 주의: 카드 선택·완료의 최종 진실은 서버가 가진다(README 제품 원칙).
 * 지금은 서버가 없어 로컬이 임시로 그 역할을 하고 있을 뿐이다.
 * DailyRecord 는 나중에 서버 응답으로 덮어써야 한다.
 */
class TmtnStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("tmtn_secure", Context.MODE_PRIVATE)

    /* ---------------------------------------------------------- 로그인 */

    var isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_LOGGED_IN, false)
        set(v) = prefs.edit().putBoolean(KEY_LOGGED_IN, v).apply()

    fun logout() {
        prefs.edit().putBoolean(KEY_LOGGED_IN, false).apply()
    }

    /* ---------------------------------------------------------- 프로필 */

    fun loadProfile(): UserProfile {
        val raw = prefs.getString(KEY_PROFILE, null) ?: return UserProfile()
        return runCatching {
            val o = JSONObject(raw)
            UserProfile(
                nickname = o.optString("nickname"),
                birthYear = o.optInt("birthYear"),
                sex = o.optString("sex").takeIf { it.isNotEmpty() }?.let { runCatching { Sex.valueOf(it) }.getOrNull() },
                heightCm = o.optDouble("heightCm", 0.0),
                weightKg = o.optDouble("weightKg", 0.0),
                waistCm = if (o.has("waistCm") && !o.isNull("waistCm")) o.optDouble("waistCm") else null,
                aerobicMinutesPerWeek = o.optInt("aerobicMinutesPerWeek"),
                strengthDaysPerWeek = o.optInt("strengthDaysPerWeek"),
                goal = o.optString("goal"),
                preferredSlot = o.optString("preferredSlot", "언제나"),
            )
        }.getOrDefault(UserProfile())
    }

    fun saveProfile(p: UserProfile) {
        val o = JSONObject()
            .put("nickname", p.nickname)
            .put("birthYear", p.birthYear)
            .put("sex", p.sex?.name ?: "")
            .put("heightCm", p.heightCm)
            .put("weightKg", p.weightKg)
            .put("aerobicMinutesPerWeek", p.aerobicMinutesPerWeek)
            .put("strengthDaysPerWeek", p.strengthDaysPerWeek)
            .put("goal", p.goal)
            .put("preferredSlot", p.preferredSlot)
        if (p.waistCm != null) o.put("waistCm", p.waistCm) else o.put("waistCm", JSONObject.NULL)
        prefs.edit().putString(KEY_PROFILE, o.toString()).apply()
    }

    /* ------------------------------------------------- 오늘 고른 카드 */

    /** 하루에 한 번만 고를 수 있다. 확정하면 바꿀 수 없다. */
    fun pickedMissionId(date: String): String? = prefs.getString("picked_$date", null)

    fun savePick(date: String, missionId: String, targetNumber: Int, place: String) {
        prefs.edit()
            .putString("picked_$date", missionId)
            .putInt("picked_num_$date", targetNumber)
            .putString("picked_place_$date", place)
            .apply()
    }

    fun pickedNumber(date: String): Int = prefs.getInt("picked_num_$date", 0)
    fun pickedPlace(date: String): String = prefs.getString("picked_place_$date", "") ?: ""

    /* ---------------------------------------------------------- 기록 */

    fun records(): List<DailyRecord> {
        val raw = prefs.getString(KEY_RECORDS, null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                DailyRecord(
                    date = o.getString("date"),
                    missionId = o.getString("missionId"),
                    title = o.getString("title"),
                    axis = Axis.from(o.optString("axis")),
                    type = MissionType.from(o.optString("type")),
                    targetNumber = o.optInt("targetNumber"),
                    unit = o.optString("unit"),
                    achieved = o.optInt("achieved"),
                    completedAtLabel = o.optString("completedAtLabel"),
                    rewardName = o.optString("rewardName"),
                    rewardHint = o.optString("rewardHint"),
                    measuredByModel = o.optBoolean("measuredByModel"),
                    fromPlaceholderModel = o.optBoolean("fromPlaceholderModel"),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun addRecord(r: DailyRecord) {
        val list = records().filterNot { it.date == r.date && it.missionId == r.missionId } + r
        val a = JSONArray()
        list.sortedByDescending { it.date }.forEach {
            a.put(
                JSONObject()
                    .put("date", it.date).put("missionId", it.missionId).put("title", it.title)
                    .put("axis", it.axis.name).put("type", it.type.name)
                    .put("targetNumber", it.targetNumber).put("unit", it.unit)
                    .put("achieved", it.achieved).put("completedAtLabel", it.completedAtLabel)
                    .put("rewardName", it.rewardName).put("rewardHint", it.rewardHint)
                    .put("measuredByModel", it.measuredByModel)
                    .put("fromPlaceholderModel", it.fromPlaceholderModel),
            )
        }
        prefs.edit().putString(KEY_RECORDS, a.toString()).apply()
    }

    fun isDoneToday(date: String): Boolean = records().any { it.date == date }

    /** 데모를 처음부터 다시 보고 싶을 때. 마이 탭에서 부른다. */
    fun resetAll() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_LOGGED_IN = "logged_in"
        const val KEY_PROFILE = "profile"
        const val KEY_RECORDS = "records"
    }
}
