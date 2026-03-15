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

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.MediumTest
import com.dsalmun.luxalarm.data.AlarmDatabase
import com.dsalmun.luxalarm.data.AlarmItem
import com.dsalmun.luxalarm.data.AlarmRepository
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

@MediumTest
class AlarmRepositoryRollbackTest {
    private lateinit var context: Context
    private lateinit var database: AlarmDatabase
    private lateinit var repository: FailingScheduleAlarmRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database =
            Room.inMemoryDatabaseBuilder(context, AlarmDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository = FailingScheduleAlarmRepository(database, context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun addAlarm_whenSchedulingFails_rollsBackInsertedAlarm() {
        val result = runBlocking { repository.addAlarm(hour = 7, minute = 30) }

        assertFalse(result)
        val alarms = runBlocking { database.alarmDao().getAllAlarms().first() }
        assertTrue(alarms.isEmpty())
    }

    @Test
    fun toggleAlarm_whenSchedulingFails_restoresOriginalActiveState() {
        val alarmId =
            runBlocking {
                    database.alarmDao().insert(AlarmItem(hour = 7, minute = 30, isActive = true))
                }
                .toInt()

        val result = runBlocking { repository.toggleAlarm(alarmId, isActive = false) }

        assertFalse(result)
        val reloaded = runBlocking { database.alarmDao().getAlarmById(alarmId) }
        assertTrue(reloaded!!.isActive)
    }

    @Test
    fun updateAlarmTime_whenSchedulingFails_restoresOriginalTimeAndActiveState() {
        val alarmId =
            runBlocking {
                    database
                        .alarmDao()
                        .insert(
                            AlarmItem(
                                hour = 6,
                                minute = 45,
                                isActive = false,
                                repeatDays = emptySet(),
                            )
                        )
                }
                .toInt()

        val result = runBlocking { repository.updateAlarmTime(alarmId, hour = 8, minute = 15) }

        assertFalse(result)
        val reloaded = runBlocking { database.alarmDao().getAlarmById(alarmId) }
        assertEquals(6, reloaded!!.hour)
        assertEquals(45, reloaded.minute)
        assertFalse(reloaded.isActive)
    }
}

private class FailingScheduleAlarmRepository(database: AlarmDatabase, context: Context) :
    AlarmRepository(database.alarmDao(), context) {
    override suspend fun scheduleNextAlarm(): Boolean = false
}
