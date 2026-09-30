package com.evolune.app.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.ZonedDateTime

object ScheduleRules {
    fun nextDaily(now: ZonedDateTime, hour: Int): ZonedDateTime {
        var next = now.withHour(hour.coerceIn(0, 23)).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return next
    }

    fun nextWeekly(now: ZonedDateTime, day: DayOfWeek = DayOfWeek.SUNDAY, hour: Int = 18): ZonedDateTime {
        var next = now.with(day).withHour(hour.coerceIn(0, 23)).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusWeeks(1)
        return next
    }

    fun delayMillis(now: ZonedDateTime, target: ZonedDateTime): Long = Duration.between(now, target).toMillis().coerceAtLeast(0)
}
