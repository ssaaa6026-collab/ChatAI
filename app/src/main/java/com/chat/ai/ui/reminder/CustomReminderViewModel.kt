package com.chat.ai.ui.reminder

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chat.ai.ChatApplication
import com.chat.ai.data.model.CustomReminder
import com.chat.ai.util.CustomReminderScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CustomReminderViewModel(application: Application) : AndroidViewModel(application) {
    private val db = (application as ChatApplication).database
    private val dao = db.customReminderDao()

    val reminders: StateFlow<List<CustomReminder>> = dao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addReminder(reminder: CustomReminder) {
        viewModelScope.launch {
            dao.insert(reminder)
            val saved = dao.getByTime(reminder.hour, reminder.minute)
            saved?.let {
                CustomReminderScheduler.schedule(getApplication(), it)
            }
        }
    }

    fun updateReminder(reminder: CustomReminder) {
        viewModelScope.launch {
            dao.update(reminder)
            CustomReminderScheduler.cancel(getApplication(), reminder.id)
            if (reminder.isEnabled) {
                CustomReminderScheduler.schedule(getApplication(), reminder)
            }
        }
    }

    fun deleteReminder(reminder: CustomReminder) {
        viewModelScope.launch {
            CustomReminderScheduler.cancel(getApplication(), reminder.id)
            dao.delete(reminder)
        }
    }

    fun toggleEnabled(reminder: CustomReminder) {
        val updated = reminder.copy(isEnabled = !reminder.isEnabled)
        updateReminder(updated)
    }
}
