package com.tmtn.app.ui.cardhome

import com.tmtn.app.network.model.ExerciseMissionOption
import com.tmtn.app.ui.common.isSensorMissionExecType
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class DailyExerciseSelectionTest {
    private val date = LocalDate.of(2026, 9, 19)
    private val options = (1..24).map { index ->
        ExerciseMissionOption(UUID(0, index.toLong()), "운동 $index", "안내",
            if (index <= 8) "SENSOR_WALKING_DURATION" else "CHECK", 3, "분", "WOOD", "나뭇가지", false)
    }

    @Test fun fiveUniqueOptionsIncludeTwoSensorsFirst() {
        val selected = selectDailyExercises(options, date, "a")
        assertEquals(5, selected.size)
        assertEquals(5, selected.distinctBy { it.catalog_entry_id }.size)
        assertTrue(selected.take(2).all { isSensorMissionExecType(it.exec_type) })
        assertTrue(selected.drop(2).none { isSensorMissionExecType(it.exec_type) })
    }

    @Test fun reorderedCatalogAndCompletedOptionKeepTheirPlaces() {
        val selected = selectDailyExercises(options, date, "a")
        val id = selected.first().catalog_entry_id
        val refreshed = options.reversed().map { it.copy(already_completed_today = it.catalog_entry_id == id) }
        val actual = selectDailyExercises(refreshed, date, "a")
        assertEquals(selected.map { it.catalog_entry_id }, actual.map { it.catalog_entry_id })
        assertTrue(actual.first().already_completed_today)
    }

    @Test fun persistedIdsSurviveNewCatalogItems() {
        val selected = selectDailyExercises(options, date, "a")
        val extra = options.first().copy(catalog_entry_id = UUID(0, 99))
        val actual = selectDailyExercises(options + extra, date, "a", selected.map { it.catalog_entry_id.toString() })
        assertEquals(selected, actual)
    }

    @Test fun nextDayAndOtherAccountReceiveDifferentSelections() {
        val first = selectDailyExercises(options, date, "a")
        assertNotEquals(first, selectDailyExercises(options, date.plusDays(1), "a"))
        assertNotEquals(first, selectDailyExercises(options, date, "b"))
    }

    @Test fun removedOptionIsReplacedWithoutDroppingSurvivors() {
        val selected = selectDailyExercises(options, date, "a")
        val actual = selectDailyExercises(options - selected.first(), date, "a", selected.map { it.catalog_entry_id.toString() })
        assertEquals(5, actual.size)
        assertTrue(actual.containsAll(selected.drop(1)))
    }

    @Test fun smallEmptyAndSingleTypeCatalogsAreValid() {
        assertTrue(selectDailyExercises(emptyList(), date, "a").isEmpty())
        assertEquals(2, selectDailyExercises(options.take(2), date, "a").size)
        assertEquals(5, selectDailyExercises(options.take(8), date, "a").size)
        assertEquals(5, selectDailyExercises(options.drop(8), date, "a").size)
        assertEquals(2, selectDailyExercises(options.take(2) + options.take(2), date, "a").size)
    }

    @Test fun hardwareCountersAreSensorsButTimersAreNot() {
        listOf("SENSOR_STEPS", "SENSOR_STEPS_IN_PLACE", "SENSOR_FLOORS_CLIMBED",
            "SENSOR_WALKING_DURATION", "SENSOR_RUNNING_DURATION", "SENSOR_RUNNING_DISTANCE")
            .forEach { assertTrue(isSensorMissionExecType(it)) }
        listOf(null, "CHECK", "TIMER", "UNKNOWN").forEach { assertFalse(isSensorMissionExecType(it)) }
    }
}
