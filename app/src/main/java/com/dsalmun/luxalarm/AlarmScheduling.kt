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

fun calculateNextTrigger(
    hour: Int,
    minute: Int,
    repeatDays: Set<Int>,
    nowProvider: () -> Calendar = { Calendar.getInstance() },
): Long {
    val now = nowProvider()
    val alarmTime =
        nowProvider().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    if (repeatDays.isEmpty()) {
        if (!alarmTime.after(now)) {
            alarmTime.add(Calendar.DAY_OF_MONTH, 1)
        }
        return alarmTime.timeInMillis
    }

    for (i in 0 until 7) {
        val potentialNextDay = nowProvider().apply { add(Calendar.DAY_OF_MONTH, i) }
        val dayOfWeek = potentialNextDay[Calendar.DAY_OF_WEEK]

        if (dayOfWeek in repeatDays) {
            val triggerTime =
                nowProvider().apply {
                    time = potentialNextDay.time
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
            if (triggerTime.after(now)) {
                return triggerTime.timeInMillis
            }
        }
    }

    val firstDayOfWeek = repeatDays.minOrNull()!!

    val nextWeekAlarm =
        nowProvider().apply {
            add(Calendar.WEEK_OF_YEAR, 1)
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    return nextWeekAlarm.timeInMillis
}
