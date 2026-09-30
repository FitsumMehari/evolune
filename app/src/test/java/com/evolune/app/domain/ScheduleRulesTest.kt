package com.evolune.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleRulesTest {
    private val zone = ZoneId.of("UTC")

    @Test fun dailyScheduleMovesToTomorrowAfterTargetHour() {
        val now = ZonedDateTime.of(2026, 9, 29, 20, 0, 0, 0, zone)
        val next = ScheduleRules.nextDaily(now, 8)
        assertEquals(30, next.dayOfMonth)
        assertEquals(8, next.hour)
        assertTrue(ScheduleRules.delayMillis(now, next) > 0)
    }

    @Test fun weeklyScheduleTargetsRequestedWeekday() {
        val now = ZonedDateTime.of(2026, 9, 29, 12, 0, 0, 0, zone)
        val next = ScheduleRules.nextWeekly(now, DayOfWeek.SUNDAY, 18)
        assertEquals(DayOfWeek.SUNDAY, next.dayOfWeek)
        assertEquals(18, next.hour)
        assertTrue(next.isAfter(now))
    }
}
