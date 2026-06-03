package com.chat.ai.util

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

private enum class PermissionStep {
    NOTIFICATION, ALARM, BATTERY, DONE
}

@Composable
fun PermissionsBootstrap() {
    val context = LocalContext.current
    var step by remember { mutableStateOf(determineFirstStep(context)) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        PrefsManager.setNotificationPermissionAsked(context, true)
        step = PermissionStep.ALARM
    }

    when (step) {
        PermissionStep.NOTIFICATION -> {
            AlertDialog(
                onDismissRequest = {
                    PrefsManager.setNotificationPermissionAsked(context, true)
                    step = PermissionStep.ALARM
                },
                title = { Text("通知权限") },
                text = { Text("允许通知后，AI 主动消息和定时提醒才能正常推送。") },
                confirmButton = {
                    TextButton(onClick = {
                        PrefsManager.setNotificationPermissionAsked(context, true)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            step = PermissionStep.ALARM
                        }
                    }) { Text("允许") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        PrefsManager.setNotificationPermissionAsked(context, true)
                        step = PermissionStep.ALARM
                    }) { Text("暂不") }
                }
            )
        }

        PermissionStep.ALARM -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                if (!alarmManager.canScheduleExactAlarms() && !PrefsManager.isAlarmPermissionAsked(context)) {
                    AlertDialog(
                        onDismissRequest = {
                            PrefsManager.setAlarmPermissionAsked(context, true)
                            step = PermissionStep.BATTERY
                        },
                        title = { Text("精确闹钟权限") },
                        text = { Text("允许精确闹钟后，定时提醒和主动消息才能准时触发。") },
                        confirmButton = {
                            TextButton(onClick = {
                                PrefsManager.setAlarmPermissionAsked(context, true)
                                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                                step = PermissionStep.BATTERY
                            }) { Text("去设置") }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                PrefsManager.setAlarmPermissionAsked(context, true)
                                step = PermissionStep.BATTERY
                            }) { Text("暂不") }
                        }
                    )
                } else {
                    step = PermissionStep.BATTERY
                }
            } else {
                step = PermissionStep.BATTERY
            }
        }

        PermissionStep.BATTERY -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                if (!powerManager.isIgnoringBatteryOptimizations(context.packageName) && !PrefsManager.isBatteryOptimizationAsked(context)) {
                    AlertDialog(
                        onDismissRequest = {
                            PrefsManager.setBatteryOptimizationAsked(context, true)
                            step = PermissionStep.DONE
                        },
                        title = { Text("电池优化豁免") },
                        text = { Text("关闭电池优化后，AI 后台服务才能持续运行，避免被系统杀死。") },
                        confirmButton = {
                            TextButton(onClick = {
                                PrefsManager.setBatteryOptimizationAsked(context, true)
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                                step = PermissionStep.DONE
                            }) { Text("去设置") }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                PrefsManager.setBatteryOptimizationAsked(context, true)
                                step = PermissionStep.DONE
                            }) { Text("暂不") }
                        }
                    )
                } else {
                    step = PermissionStep.DONE
                }
            } else {
                step = PermissionStep.DONE
            }
        }

        PermissionStep.DONE -> { /* 所有权限已处理 */ }
    }
}

private fun determineFirstStep(context: Context): PermissionStep {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            && !PrefsManager.isNotificationPermissionAsked(context)) {
            return PermissionStep.NOTIFICATION
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!alarmManager.canScheduleExactAlarms() && !PrefsManager.isAlarmPermissionAsked(context)) {
            return PermissionStep.ALARM
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(context.packageName) && !PrefsManager.isBatteryOptimizationAsked(context)) {
            return PermissionStep.BATTERY
        }
    }
    return PermissionStep.DONE
}
