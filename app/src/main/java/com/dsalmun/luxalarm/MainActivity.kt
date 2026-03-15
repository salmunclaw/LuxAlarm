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

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.dsalmun.luxalarm.ui.theme.LuxAlarmTheme

class MainActivity : ComponentActivity() {
    private var showNotificationPermissionDeniedDialog by mutableStateOf(false)
    private var showExactAlarmPermissionDeniedDialog by mutableStateOf(false)

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean
            ->
            if (!isGranted) {
                showNotificationPermissionDeniedDialog = true
            }
        }

    private val requestExactAlarmPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (!AppContainer.repository.canScheduleExactAlarms()) {
                showExactAlarmPermissionDeniedDialog = true
            }
        }

    override fun onResume() {
        super.onResume()
        if (AppContainer.repository.isAlarmRinging()) {
            if (AlarmService.isRunning) {
                val intent =
                    Intent(this, AlarmActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                startActivity(intent)
            } else {
                AppContainer.repository.clearRingingAlarm()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestRequiredPermissions()

        enableEdgeToEdge()
        setContent {
            LuxAlarmTheme {
                var showSettings by remember { mutableStateOf(false) }

                BackHandler(enabled = showSettings) { showSettings = false }
                if (showSettings) {
                    SettingsScreen(onBackClick = { showSettings = false })
                } else {
                    AlarmScreen(onSettingsClick = { showSettings = true })
                }

                PermissionDialogs(
                    showNotificationPermissionDeniedDialog = showNotificationPermissionDeniedDialog,
                    onDismissNotificationPermissionDeniedDialog = {
                        showNotificationPermissionDeniedDialog = false
                    },
                    onOpenAppNotificationSettings = {
                        showNotificationPermissionDeniedDialog = false
                        openAppNotificationSettings()
                    },
                    showExactAlarmPermissionDeniedDialog = showExactAlarmPermissionDeniedDialog,
                    onDismissExactAlarmPermissionDeniedDialog = {
                        showExactAlarmPermissionDeniedDialog = false
                    },
                    onOpenExactAlarmSettings = {
                        showExactAlarmPermissionDeniedDialog = false
                        requestExactAlarmPermission()
                    },
                )
            }
        }
    }

    private fun requestRequiredPermissions() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (!AppContainer.repository.canScheduleExactAlarms()) {
            requestExactAlarmPermission()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = getSystemService(NotificationManager::class.java)
            if (!nm.canUseFullScreenIntent()) {
                val intent =
                    Intent(
                        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        "package:$packageName".toUri(),
                    )
                startActivity(intent)
            }
        }
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent =
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = "package:$packageName".toUri()
                }
            requestExactAlarmPermissionLauncher.launch(intent)
        }
    }

    private fun openAppNotificationSettings() {
        val intent =
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
        startActivity(intent)
    }
}

@androidx.compose.runtime.Composable
private fun PermissionDialogs(
    showNotificationPermissionDeniedDialog: Boolean,
    onDismissNotificationPermissionDeniedDialog: () -> Unit,
    onOpenAppNotificationSettings: () -> Unit,
    showExactAlarmPermissionDeniedDialog: Boolean,
    onDismissExactAlarmPermissionDeniedDialog: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
) {
    if (showNotificationPermissionDeniedDialog) {
        AlertDialog(
            onDismissRequest = onDismissNotificationPermissionDeniedDialog,
            title = {
                Text(
                    text =
                        androidx.compose.ui.res.stringResource(
                            R.string.notification_permission_denied_title
                        )
                )
            },
            text = {
                Text(
                    text =
                        androidx.compose.ui.res.stringResource(
                            R.string.notification_permission_denied_message
                        )
                )
            },
            dismissButton = {
                TextButton(onClick = onDismissNotificationPermissionDeniedDialog) {
                    Text(
                        text =
                            androidx.compose.ui.res.stringResource(
                                R.string.permission_dialog_not_now
                            )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onOpenAppNotificationSettings) {
                    Text(
                        text =
                            androidx.compose.ui.res.stringResource(
                                R.string.permission_dialog_open_settings
                            )
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }

    if (showExactAlarmPermissionDeniedDialog) {
        AlertDialog(
            onDismissRequest = onDismissExactAlarmPermissionDeniedDialog,
            title = {
                Text(
                    text =
                        androidx.compose.ui.res.stringResource(
                            R.string.exact_alarm_permission_denied_title
                        )
                )
            },
            text = {
                Text(
                    text =
                        androidx.compose.ui.res.stringResource(
                            R.string.exact_alarm_permission_denied_message
                        )
                )
            },
            dismissButton = {
                TextButton(onClick = onDismissExactAlarmPermissionDeniedDialog) {
                    Text(
                        text =
                            androidx.compose.ui.res.stringResource(
                                R.string.permission_dialog_not_now
                            )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onOpenExactAlarmSettings) {
                    Text(
                        text =
                            androidx.compose.ui.res.stringResource(
                                R.string.permission_dialog_open_settings
                            )
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}
