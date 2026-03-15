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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dsalmun.luxalarm.data.AlarmItem
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AlarmScreen(
    onSettingsClick: () -> Unit = {},
    alarmViewModel: AlarmViewModel = viewModel(factory = AlarmViewModelFactory()),
) {
    val context = LocalContext.current
    val alarms by alarmViewModel.alarms.collectAsState()
    var showTimePickerDialog by remember { mutableStateOf(false) }
    var alarmToEdit by remember { mutableStateOf<AlarmItem?>(null) }
    var alarmPendingDelete by remember { mutableStateOf<AlarmItem?>(null) }
    var expandedAlarmId by remember { mutableStateOf<Int?>(null) }
    var alarmIdForRingtonePicker by remember { mutableStateOf<Int?>(null) }

    val ringtonePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val alarmId = alarmIdForRingtonePicker
            alarmIdForRingtonePicker = null
            if (alarmId == null || it.resultCode != Activity.RESULT_OK)
                return@rememberLauncherForActivityResult

            val selectedUri: Uri? =
                it.data?.let { data ->
                    IntentCompat.getParcelableExtra(
                        data,
                        RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                        Uri::class.java,
                    )
                }
            val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val ringtoneUriToStore =
                selectedUri?.toString()?.takeUnless { selectedUri == defaultUri }
            alarmViewModel.setAlarmRingtone(alarmId, ringtoneUriToStore)
        }

    LaunchedEffect(key1 = Unit) {
        alarmViewModel.events.collectLatest { event ->
            when (event) {
                is AlarmViewModel.Event.ShowPermissionError -> {
                    Toast.makeText(
                            context,
                            context.getString(R.string.cannot_schedule_exact_alarms),
                            Toast.LENGTH_SHORT,
                        )
                        .show()
                }
                is AlarmViewModel.Event.ShowAlarmSetMessage -> {
                    showSetAlarmToast(context, event.hour, event.minute, event.repeatDays)
                }
            }
        }
    }

    val timePickerState =
        remember(alarmToEdit) {
            val calendar = Calendar.getInstance()
            val initialHour = alarmToEdit?.hour ?: calendar[Calendar.HOUR_OF_DAY]
            val initialMinute = alarmToEdit?.minute ?: calendar[Calendar.MINUTE]
            TimePickerState(
                initialHour = initialHour,
                initialMinute = initialMinute,
                is24Hour = true,
            )
        }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    alarmToEdit = null // Ensure we're in "add" mode
                    showTimePickerDialog = true
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.add_24px),
                    contentDescription = stringResource(R.string.add_alarm),
                )
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.alarm_screen_title)) },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            painter = painterResource(R.drawable.settings_24px),
                            contentDescription = stringResource(R.string.settings),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ),
            )
        },
    ) { innerPadding ->
        if (alarms.isEmpty()) {
            Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.no_alarms_set),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(alarms, key = { it.id }) { alarm ->
                    AlarmRow(
                        alarm = alarm,
                        ringtoneDisplayName =
                            remember(alarm.ringtoneUri) {
                                getRingtoneDisplayName(context, alarm.ringtoneUri)
                            },
                        expanded = expandedAlarmId == alarm.id,
                        onToggle = { isActive -> alarmViewModel.toggleAlarm(alarm.id, isActive) },
                        onClick = {
                            expandedAlarmId = if (expandedAlarmId == alarm.id) null else alarm.id
                        },
                        onTimeClick = {
                            alarmToEdit = alarm
                            showTimePickerDialog = true
                        },
                        onRepeatDaysChange = { newDays ->
                            alarmViewModel.setRepeatDays(alarm.id, newDays)
                        },
                        onRingtoneClick = {
                            if (alarmIdForRingtonePicker != null) return@AlarmRow
                            alarmIdForRingtonePicker = alarm.id
                            val defaultUri =
                                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                            val existingUri = alarm.ringtoneUri?.toUri() ?: defaultUri
                            val pickerIntent =
                                Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                    putExtra(
                                        RingtoneManager.EXTRA_RINGTONE_TYPE,
                                        RingtoneManager.TYPE_ALARM,
                                    )
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, defaultUri)
                                    putExtra(
                                        RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                        existingUri,
                                    )
                                    putExtra(
                                        RingtoneManager.EXTRA_RINGTONE_TITLE,
                                        context.getString(R.string.select_ringtone),
                                    )
                                }
                            ringtonePickerLauncher.launch(pickerIntent)
                        },
                        onDeleteClick = { alarmPendingDelete = alarm },
                    )
                }
            }
        }
    }

    if (showTimePickerDialog) {
        TimePickerDialog(
            onConfirm = {
                if (alarmToEdit != null) {
                    alarmViewModel.updateAlarmTime(
                        alarmToEdit!!.id,
                        timePickerState.hour,
                        timePickerState.minute,
                    )
                } else {
                    alarmViewModel.addAlarm(timePickerState.hour, timePickerState.minute)
                }
                showTimePickerDialog = false
                alarmToEdit = null
            },
            onDismiss = {
                showTimePickerDialog = false
                alarmToEdit = null
            },
            timePickerState = timePickerState,
        )
    }

    alarmPendingDelete?.let { alarm ->
        ConfirmDeleteAlarmDialog(
            alarm = alarm,
            onDismiss = { alarmPendingDelete = null },
            onConfirm = {
                if (expandedAlarmId == alarm.id) {
                    expandedAlarmId = null
                }
                alarmViewModel.deleteAlarm(alarm.id)
                alarmPendingDelete = null
            },
        )
    }
}

@Composable
fun AlarmRow(
    alarm: AlarmItem,
    ringtoneDisplayName: String,
    expanded: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onTimeClick: () -> Unit,
    onRepeatDaysChange: (Set<Int>) -> Unit,
    onRingtoneClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text =
                        String.format(Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onTimeClick),
                )
                Column(horizontalAlignment = Alignment.End) {
                    Icon(
                        painter =
                            painterResource(
                                if (expanded) R.drawable.keyboard_arrow_up_24px
                                else R.drawable.keyboard_arrow_down_24px
                            ),
                        contentDescription =
                            if (expanded) stringResource(R.string.collapse)
                            else stringResource(R.string.expand),
                    )
                    Switch(checked = alarm.isActive, onCheckedChange = onToggle)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatRepeatDays(context, alarm.repeatDays, alarm.hour, alarm.minute),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))
                DaySelector(
                    selectedDays = alarm.repeatDays,
                    onDayClick = { day ->
                        val newDays = alarm.repeatDays.toMutableSet()
                        if (newDays.contains(day)) {
                            newDays.remove(day)
                        } else {
                            newDays.add(day)
                        }
                        onRepeatDaysChange(newDays)
                    },
                )
                Spacer(modifier = Modifier.height(22.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        painter = painterResource(R.drawable.notifications_active_24px),
                        contentDescription = stringResource(R.string.ringtone),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = ringtoneDisplayName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onRingtoneClick),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDeleteClick) {
                        Icon(
                            painter = painterResource(R.drawable.delete_24px),
                            contentDescription =
                                stringResource(R.string.delete_alarm_content_description),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DaySelector(selectedDays: Set<Int>, onDayClick: (Int) -> Unit) {
    val days =
        listOf(
            "Su" to Calendar.SUNDAY,
            "Mo" to Calendar.MONDAY,
            "Tu" to Calendar.TUESDAY,
            "We" to Calendar.WEDNESDAY,
            "Th" to Calendar.THURSDAY,
            "Fr" to Calendar.FRIDAY,
            "Sa" to Calendar.SATURDAY,
        )

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
        days.forEach { (label, day) ->
            val isSelected = selectedDays.contains(day)
            Box(
                modifier =
                    Modifier.size(40.dp)
                        .clip(CircleShape)
                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent
                        )
                        .clickable { onDayClick(day) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color =
                        if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

fun formatRepeatDays(appContext: Context, days: Set<Int>, hour: Int, minute: Int): String {
    if (days.isEmpty()) {
        val now = Calendar.getInstance()
        val alarmTime =
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        return if (alarmTime.after(now)) appContext.getString(R.string.today)
        else appContext.getString(R.string.tomorrow)
    }
    if (days.size == 7) return appContext.getString(R.string.every_day)

    val sortedDays = days.toSortedSet()
    val dayNames =
        sortedDays.map {
            when (it) {
                Calendar.SUNDAY -> "Sun"
                Calendar.MONDAY -> "Mon"
                Calendar.TUESDAY -> "Tue"
                Calendar.WEDNESDAY -> "Wed"
                Calendar.THURSDAY -> "Thu"
                Calendar.FRIDAY -> "Fri"
                Calendar.SATURDAY -> "Sat"
                else -> ""
            }
        }
    return dayNames.joinToString(", ")
}

private fun getRingtoneDisplayName(context: Context, ringtoneUri: String?): String {
    val uri =
        if (ringtoneUri.isNullOrBlank()) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        else ringtoneUri.toUri()
    return RingtoneManager.getRingtone(context, uri)?.getTitle(context)
        ?: context.getString(R.string.unknown_ringtone)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    timePickerState: TimePickerState,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_alarm_time)) },
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = timePickerState)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.set)) } },
    )
}

@Composable
private fun ConfirmDeleteAlarmDialog(
    alarm: AlarmItem,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_alarm_title)) },
        text = {
            Text(
                stringResource(
                    R.string.delete_alarm_message,
                    String.format(Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute),
                )
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        },
    )
}

private fun showSetAlarmToast(
    context: Context,
    hour: Int,
    minute: Int,
    repeatDays: Set<Int> = emptySet(),
) {
    val scheduledTimeMillis = calculateNextTrigger(hour, minute, repeatDays)

    val now = Calendar.getInstance()
    val diffMillis = scheduledTimeMillis - now.timeInMillis
    val totalMinutes = kotlin.math.ceil(diffMillis / (1000.0 * 60)).toInt()
    val days = totalMinutes / (60 * 24)
    val hours = (totalMinutes % (60 * 24)) / 60
    val minutes = totalMinutes % 60

    val timeParts = mutableListOf<String>()
    if (days > 0) {
        timeParts.add(
            "$days ${if (days == 1) context.getString(R.string.day_singular) else context.getString(R.string.day_plural)}"
        )
    }
    if (hours > 0) {
        timeParts.add(
            "$hours ${if (hours == 1) context.getString(R.string.hour_singular) else context.getString(R.string.hour_plural)}"
        )
    }
    if (minutes > 0) {
        timeParts.add(
            "$minutes ${if (minutes == 1) context.getString(R.string.minute_singular) else context.getString(R.string.minute_plural)}"
        )
    }
    if (timeParts.isEmpty()) {
        timeParts.add(context.getString(R.string.less_than_a_minute))
    }

    val toastMessage =
        context.getString(R.string.alarm_set_for_from_now, timeParts.joinToString(", "))
    Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
}
