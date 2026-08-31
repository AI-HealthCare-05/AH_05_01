package kr.tmtn.app.data

import android.content.Context
import android.content.SharedPreferences
import kr.tmtn.app.domain.ml.StrengthIntensity
import kr.tmtn.app.domain.ml.PregnancyStatus
import kr.tmtn.app.domain.ml.SexCode
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
        val rawJson = prefs.getString(KEY_PROFILE, null) ?: return UserProfile()
        return runCatching {
            val o = JSONObject(rawJson)
            UserProfile(
                name = o.optString("name"),
                nickname = o.optString("nickname"),
                birthYear = o.optInt("birthYear"),
                birthMonth = o.optInt("birthMonth"),
                sexCode = SexCode.fromCode(o.optIntOrNull("sexCode")),
                heightCm = o.optDouble("heightCm", 0.0),
                weightKg = o.optDouble("weightKg", 0.0),
                strengthDaysWeek = o.optIntOrNull("strengthDaysWeek"),
                strengthIntensity = runCatching {
                    StrengthIntensity.valueOf(o.optString("strengthIntensity"))
                }.getOrNull(),
                aerobicLightMinWeek = o.optIntOrNull("aerobicLightMinWeek"),
                aerobicModerateMinWeek = o.optIntOrNull("aerobicModerateMinWeek"),
                aerobicVigorousMinWeek = o.optIntOrNull("aerobicVigorousMinWeek"),
                pregnancyStatus = runCatching {
                    PregnancyStatus.valueOf(o.optString("pregnancyStatus"))
                }.getOrDefault(PregnancyStatus.UNKNOWN),
            )
        }.getOrDefault(UserProfile())
    }

    fun saveProfile(p: UserProfile) {
        // 모르는 값을 0 으로 바꾸지 않는다 (data_contract_v0_3 convert_unknown_to_zero: false)
        val o = JSONObject()
            .put("name", p.name)
            .put("nickname", p.nickname)
            .put("birthYear", p.birthYear)
            .put("birthMonth", p.birthMonth)
            .put("sexCode", p.sexCode?.knhanesCode ?: JSONObject.NULL)
            .put("heightCm", p.heightCm)
            .put("weightKg", p.weightKg)
            .put("strengthDaysWeek", p.strengthDaysWeek ?: JSONObject.NULL)
            .put("strengthIntensity", p.strengthIntensity?.name ?: JSONObject.NULL)
            .put("aerobicLightMinWeek", p.aerobicLightMinWeek ?: JSONObject.NULL)
            .put("aerobicModerateMinWeek", p.aerobicModerateMinWeek ?: JSONObject.NULL)
            .put("aerobicVigorousMinWeek", p.aerobicVigorousMinWeek ?: JSONObject.NULL)
            .put("pregnancyStatus", p.pregnancyStatus.name)
        prefs.edit().putString(KEY_PROFILE, o.toString()).apply()
    }

    /** null 과 0 을 구분해서 읽는다. 없는 값을 0 으로 만들지 않기 위해 필요하다. */
    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null

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

    /* ------------------------------------------------- 회고 · 적합도 */

    /**
     * 완료 뒤 남기는 한 줄과 "오늘 이거 어땠나" 답. (C20 · C08)
     *
     * 둘 다 **선택**이다. 안 쓰고 넘어가도 기록은 이미 남아 있다.
     * 적합도는 나중에 카드를 고를 때 난이도를 맞추는 신호로 쓴다.
     */
    fun reflectionOn(date: String): String = prefs.getString("note_$date", "") ?: ""

    fun fitOn(date: String): String = prefs.getString("fit_$date", "") ?: ""

    fun saveReflection(date: String, note: String, fit: String) {
        prefs.edit()
            .putString("note_$date", note)
            .putString("fit_$date", fit)
            .apply()
    }

    /* -------------------------------------------------- 미션 타이머 (C02~C04) */

    /**
     * 진행 중인 타이머. **초를 세지 않고 "언제 시작했는지" 를 적어 둔다.**
     *
     * 1초마다 세는 방식은 화면을 벗어나거나 폰이 잠들면 멈춘다.
     * 20분짜리 미션을 하려고 20분 내내 앱을 켜 두고 있으라고 할 수는 없다.
     * 시작 시각만 적어 두면 돌아와서 빼기만 하면 되고, 앱이 죽었다 살아나도 값이 남는다.
     *
     * [startedAt] 이 0 이면 지금은 멈춰 있다는 뜻이다.
     * [accumulated] 는 멈추기 전까지 쌓인 초.
     */
    data class TimerState(val startedAt: Long, val accumulated: Int) {
        val isRunning: Boolean get() = startedAt > 0L
    }

    fun timerStateOf(key: String): TimerState = TimerState(
        startedAt = prefs.getLong("run_started_$key", 0L),
        accumulated = prefs.getInt("run_acc_$key", 0),
    )

    fun saveTimerState(key: String, state: TimerState) {
        prefs.edit()
            .putLong("run_started_$key", state.startedAt)
            .putInt("run_acc_$key", state.accumulated)
            .apply()
    }

    /** 미션을 끝냈거나 접었을 때. 남겨 두면 다음 미션이 이어서 세는 것처럼 보인다. */
    fun clearTimer(key: String) {
        prefs.edit()
            .remove("run_started_$key")
            .remove("run_acc_$key")
            .apply()
    }

    /* ------------------------------------------------------ 대체 미션 */

    /**
     * 미션 바꾸기는 **하루에 한 번**이다.
     * 마음에 드는 카드가 나올 때까지 돌리면 "고른다" 는 뜻이 없어진다.
     *
     * TODO(서버): 쉼과 마찬가지로 서버가 세야 기기를 바꿔도 맞는다.
     */
    fun swappedOn(date: String): Boolean = prefs.getBoolean("swap_$date", false)

    fun markSwapped(date: String) {
        prefs.edit().putBoolean("swap_$date", true).apply()
    }

    /* ---------------------------------------------------------- 쉼 */

    /**
     * 쉼은 **사용자가 직접 누른 날에만** 기록된다. 저절로 쉼이 되는 날은 없다.
     * 아무것도 안 하고 지나간 날은 쉼이 아니라 미완료다.
     *
     * 한 주에 [REST_PER_WEEK] 번까지. 주는 월요일에 시작한다.
     *
     * TODO(서버): 이 카운트는 **서버가 세야** 기기를 바꾸거나 앱을 지웠다
     *  깔아도 맞는다. 지금은 서버가 없어 이 기기 안에서만 센다.
     */
    fun isRest(date: String): Boolean = prefs.getBoolean("rest_$date", false)

    fun markRest(date: String) {
        prefs.edit().putBoolean("rest_$date", true).apply()
    }

    fun clearRest(date: String) {
        prefs.edit().remove("rest_$date").apply()
    }

    /** 그 주에 이미 쉰 날들 (월요일 시작 7일 중) */
    fun restDaysInWeek(weekDates: List<String>): List<String> = weekDates.filter { isRest(it) }

    /** 데모를 처음부터 다시 보고 싶을 때. 내 정보 탭에서 부른다. */
    fun resetAll() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_LOGGED_IN = "logged_in"
        const val KEY_PROFILE = "profile"
        const val KEY_RECORDS = "records"
    }
}
