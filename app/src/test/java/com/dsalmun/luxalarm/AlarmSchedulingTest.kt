/*
 * This file is part of Lux Alarm, authored by Daniel Salmun.
 *
 * Lux Alarm is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Lux Alarm is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Lux Alarm.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.dsalmun.luxalarm

import java.util.Calendar
import java.util.TimeZone
import kotlin.test.assertEquals
import org.junit.Test

class AlarmSchedulingTest {
    private fun utcCalendar(
        year: Int,
        month: Int,
        dayOfMonth: Int,
        hour: Int,
        minute: Int,
    ): Calendar =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, dayOfMonth)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    @Test
    fun oneShotAlarmLaterToday_returnsToday() {
        val now = utcCalendar(2026, Calendar.MARCH, 15, 6, 0)

        val result = calculateNextTrigger(7, 30, emptySet()) { now.clone() as Calendar }

        val expected = utcCalendar(2026, Calendar.MARCH, 15, 7, 30).timeInMillis
        assertEquals(expected, result)
    }

    @Test
    fun oneShotAlarmAtSameMinute_rollsToTomorrow() {
        val now = utcCalendar(2026, Calendar.MARCH, 15, 7, 30)

        val result = calculateNextTrigger(7, 30, emptySet()) { now.clone() as Calendar }

        val expected = utcCalendar(2026, Calendar.MARCH, 16, 7, 30).timeInMillis
        assertEquals(expected, result)
    }

    @Test
    fun repeatingAlarmLaterSameDay_returnsToday() {
        val now = utcCalendar(2026, Calendar.MARCH, 15, 6, 0)

        val result = calculateNextTrigger(7, 30, setOf(Calendar.SUNDAY)) { now.clone() as Calendar }

        val expected = utcCalendar(2026, Calendar.MARCH, 15, 7, 30).timeInMillis
        assertEquals(expected, result)
    }

    @Test
    fun repeatingAlarmSkipsToNextSelectedDay() {
        val now = utcCalendar(2026, Calendar.MARCH, 15, 8, 0)

        val result =
            calculateNextTrigger(7, 30, setOf(Calendar.MONDAY, Calendar.WEDNESDAY)) {
                now.clone() as Calendar
            }

        val expected = utcCalendar(2026, Calendar.MARCH, 16, 7, 30).timeInMillis
        assertEquals(expected, result)
    }

    @Test
    fun repeatingAlarmWrapsToNextWeekWhenTodayHasPassed() {
        val now = utcCalendar(2026, Calendar.MARCH, 15, 8, 0)

        val result = calculateNextTrigger(7, 30, setOf(Calendar.SUNDAY)) { now.clone() as Calendar }

        val expected = utcCalendar(2026, Calendar.MARCH, 22, 7, 30).timeInMillis
        assertEquals(expected, result)
    }

    @Test
    fun repeatingAlarmReturnsNearestUpcomingDayNotSmallestDayConstant() {
        val now = utcCalendar(2026, Calendar.MARCH, 19, 9, 0)

        val result =
            calculateNextTrigger(7, 30, setOf(Calendar.SATURDAY, Calendar.MONDAY)) {
                now.clone() as Calendar
            }

        val expected = utcCalendar(2026, Calendar.MARCH, 21, 7, 30).timeInMillis
        assertEquals(expected, result)
    }
}
