package kr.tmtn.app.data

import android.content.Context
import kr.tmtn.app.domain.model.Axis
import kr.tmtn.app.domain.model.Mission
import kr.tmtn.app.domain.model.MissionType
import org.json.JSONArray
import org.json.JSONObject

/**
 * assets/missions.json (카드 200장) 을 읽는다.
 *
 * 원본은 팀이 검수한 CSV(`TMTN_오늘의운세_200카드`)다.
 * 문구를 앱에서 고치지 않는다 — CSV 를 고치고 다시 JSON 을 만든다.
 * (변환 스크립트: docs/TMTN_ANDROID_GUIDE.md 참고)
 *
 * 외부 JSON 라이브러리를 쓰지 않고 안드로이드 내장 org.json 만 쓴다. 의존성을 늘리지 않기 위해서다.
 */
class MissionCatalog private constructor(val missions: List<Mission>) {

    val byId: Map<String, Mission> = missions.associateBy { it.id }

    fun byAxis(axis: Axis): List<Mission> = missions.filter { it.axis == axis }

    companion object {
        @Volatile
        private var instance: MissionCatalog? = null

        fun load(context: Context): MissionCatalog =
            instance ?: synchronized(this) {
                instance ?: parse(
                    context.applicationContext.assets.open("missions.json")
                        .bufferedReader().use { it.readText() },
                ).also { instance = it }
            }

        private fun parse(raw: String): MissionCatalog {
            val root = JSONObject(raw)
            val arr: JSONArray = root.getJSONArray("missions")
            val list = ArrayList<Mission>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list += Mission(
                    id = o.getString("id"),
                    axis = Axis.from(o.getString("axis")),
                    area = o.getString("area"),
                    action = o.getString("action"),
                    type = MissionType.from(o.getString("type")),
                    modelBacked = o.optBoolean("modelBacked", false),
                    measureKeys = o.strings("measureKeys"),
                    startCondition = o.optString("startCondition"),
                    completeRule = o.optString("completeRule"),
                    places = o.strings("places"),
                    numMin = o.optInt("numMin", 1),
                    numMax = o.optInt("numMax", 1),
                    numStep = o.optInt("numStep", 1).coerceAtLeast(1),
                    unit = o.optString("unit"),
                    timeSlots = o.strings("timeSlots"),
                    fortune = o.optString("fortune"),
                    lineTemplate = o.optString("lineTemplate"),
                    safetyNote = o.optString("safetyNote"),
                    safetyTag = o.optString("safetyTag"),
                    seniorSafe = o.optBoolean("seniorSafe", true),
                    rewardMaterial = o.optString("rewardMaterial"),
                    rewardName = o.optString("rewardName"),
                    rewardHint = o.optString("rewardHint"),
                )
            }
            return MissionCatalog(list)
        }

        private fun JSONObject.strings(key: String): List<String> {
            val a = optJSONArray(key) ?: return emptyList()
            return (0 until a.length()).map { a.getString(it) }
        }
    }
}
