package com.chat.ai.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_reminders")
data class CustomReminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,          // 提醒小时 (0-23)
    val minute: Int,        // 提醒分钟 (0-59)
    val content: String,    // 提醒内容
    val weekDays: String,   // 逗号分隔的星期几，如 "1,2,3,4,5" (1=周一, 7=周日)
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun getWeekDayList(): List<Int> {
        if (weekDays.isBlank()) return emptyList()
        return weekDays.split(",").mapNotNull { it.trim().toIntOrNull() }
    }

    fun isToday(): Boolean {
        val today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
        // Calendar: 1=Sunday, 2=Monday...7=Saturday
        // Our format: 1=Monday...7=Sunday
        val todayInOurFormat = if (today == 1) 7 else today - 1
        return getWeekDayList().contains(todayInOurFormat)
    }
}
