package kr.tmtn.app.data

import kr.tmtn.app.domain.model.Axis
import kr.tmtn.app.domain.model.Mission
import kr.tmtn.app.domain.model.MissionCard
import kr.tmtn.app.domain.model.UserProfile
import kotlin.random.Random

/**
 * 하루 세 장을 뽑는 규칙.
 *
 *  - 같은 날에는 몇 번을 다시 열어도 같은 세 장이 나온다 (날짜로 씨앗을 고정).
 *  - 세 장의 축(木火土金水)이 겹치지 않게 뽑는다.
 *  - 안전 태그가 붙은 카드는 시니어 안전 카드가 아니면 빼지 않되,
 *    HIGH_IMPACT / BALANCE 는 한 세트에 한 장까지만 넣는다.
 *
 * 서버가 생기면 이 로직은 서버로 옮긴다 (카드 세트의 최종 진실은 서버).
 */
object DailyCardDraw {

    fun draw(catalog: MissionCatalog, profile: UserProfile, date: String, count: Int = 3): List<MissionCard> {
        val seed = (date + "|" + profile.birthYear + "|" + profile.displayName).hashCode().toLong()
        val rnd = Random(seed)

        val axes = Axis.displayOrder.shuffled(rnd).take(count)
        val picked = ArrayList<MissionCard>(count)
        var riskUsed = false

        for (axis in axes) {
            // 시간대는 온보딩에서 묻지 않는다(피그마 A09·A10 의 기상·취침 시각이 들어오면 그때 맞춘다).
            // 지금은 모든 카드를 후보로 둔다.
            val pool = catalog.byAxis(axis)

            val safePool = pool.filter { it.safetyTag.isEmpty() }
            val chosenPool = if (riskUsed && safePool.isNotEmpty()) safePool else pool
            val mission = chosenPool[rnd.nextInt(chosenPool.size)]
            if (mission.safetyTag.isNotEmpty()) riskUsed = true
            picked += mission.toCard(rnd)
        }
        return picked
    }

    private fun Mission.toCard(rnd: Random): MissionCard {
        val steps = ((numMax - numMin) / numStep).coerceAtLeast(0)
        val target = numMin + rnd.nextInt(steps + 1) * numStep
        val place = if (places.isEmpty()) "지금 있는 곳" else places[rnd.nextInt(places.size)]
        return MissionCard(mission = this, targetNumber = target, place = place)
    }

    /** 확정된 카드를 다시 만든다 (앱을 껐다 켜도 같은 카드가 나와야 한다). */
    fun rebuild(catalog: MissionCatalog, missionId: String, targetNumber: Int, place: String): MissionCard? {
        val m = catalog.byId[missionId] ?: return null
        return MissionCard(m, targetNumber, place)
    }

    /**
     * 대체 미션 한 장. (B11)
     *
     * **같은 축을 지킨다** — 오늘 쌓기로 한 재료는 그대로 두고 행동만 바꾼다.
     * 축까지 바뀌면 "물길이 필요한 날" 이라는 그날의 성격이 없어지고,
     * 마음에 드는 카드가 나올 때까지 돌리는 뽑기가 된다.
     *
     * 안전 태그가 붙은 미션은 되도록 내주지 않는다 —
     * 지금 하기 어렵다고 한 사람에게 더 센 것을 권할 이유가 없다.
     */
    fun alternative(catalog: MissionCatalog, current: MissionCard, date: String): MissionCard? {
        val rnd = Random("$date|swap|${current.mission.id}".hashCode().toLong())
        val pool = catalog.byAxis(current.mission.axis)
            .filter { it.id != current.mission.id }
        val safe = pool.filter { it.safetyTag.isEmpty() }
        val chosen = if (safe.isNotEmpty()) safe else pool
        if (chosen.isEmpty()) return null
        return chosen[rnd.nextInt(chosen.size)].toCard(rnd)
    }
}
