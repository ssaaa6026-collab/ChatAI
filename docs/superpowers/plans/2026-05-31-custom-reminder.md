# Custom Reminder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add custom reminders with AI-generated voice notifications, support selecting specific days of the week, and prioritize custom reminders over fixed reminders when they conflict.

**Architecture:** Custom reminders stored in Room database, scheduled via AlarmManager with unique request codes. Extended ReminderReceiver/ReminderWorker handle custom reminders. AI generates natural reminder text via MimoTextApi, then TTS plays it directly from notification via a foreground service. Custom reminders override fixed reminders at the same time slot.

**Tech Stack:** Kotlin, Jetpack Compose, Room, AlarmManager, WorkManager, MimoTextApi, MimoTtsApi, MediaPlayer

---

### Task 1: Update CustomReminder Model

**Files:**
- Modify: `app/src/main/java/com/chat/ai/data/model/CustomReminder.kt`

- [ ] **Step 1: Add weekDays field to CustomReminder**

Replace the entire file with:

```kotlin
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
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/chat/ai/data/model/CustomReminder.kt
git commit -m "feat: add weekDays field to CustomReminder model"
```

---

### Task 2: Register CustomReminder in Database

**Files:**
- Modify: `app/src/main/java/com/chat/ai/data/db/AppDatabase.kt`

- [ ] **Step 1: Add CustomReminder to entities and bump version**

Replace the entire file with:

```kotlin
package com.chat.ai.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.chat.ai.data.model.*

@Database(
    entities = [Message::class, Persona::class, Summary::class, VoiceConfig::class, CustomReminder::class],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun personaDao(): PersonaDao
    abstract fun summaryDao(): SummaryDao
    abstract fun voiceConfigDao(): VoiceConfigDao
    abstract fun customReminderDao(): CustomReminderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chat_ai_database"
                ).fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/chat/ai/data/db/AppDatabase.kt
git commit -m "feat: register CustomReminder in AppDatabase (version 8)"
```

---

### Task 3: Create CustomReminderScheduler

**Files:**
- Create: `app/src/main/java/com/chat/ai/util/CustomReminderScheduler.kt`

- [ ] **Step 1: Write the scheduler**

```kotlin
package com.chat.ai.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.chat.ai.data.model.CustomReminder
import com.chat.ai.service.CustomReminderReceiver
import java.util.Calendar

object CustomReminderScheduler {
    private const val TAG = "CustomReminderScheduler"

    fun schedule(context: Context, reminder: CustomReminder) {
        if (!reminder.isEnabled) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, CustomReminderReceiver::class.java).apply {
            putExtra("custom_reminder_id", reminder.id)
            putExtra("custom_reminder_content", reminder.content)
            putExtra("custom_reminder_weekdays", reminder.weekDays)
        }

        val requestCode = (reminder.id + 100000).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, reminder.hour)
            set(Calendar.MINUTE, reminder.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )

        Log.d(TAG, "Scheduled custom reminder ${reminder.id} at ${reminder.hour}:${reminder.minute}, weekdays: ${reminder.weekDays}")
    }

    fun cancel(context: Context, reminderId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, CustomReminderReceiver::class.java)
        val requestCode = (reminderId + 100000).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Cancelled custom reminder $reminderId")
    }

    fun rescheduleAll(context: Context, reminders: List<CustomReminder>) {
        reminders.forEach { reminder ->
            cancel(context, reminder.id)
            if (reminder.isEnabled) {
                schedule(context, reminder)
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/chat/ai/util/CustomReminderScheduler.kt
git commit -m "feat: add CustomReminderScheduler for AlarmManager scheduling"
```

---

### Task 4: Add CustomReminderReceiver

**Files:**
- Create: `app/src/main/java/com/chat/ai/service/CustomReminderReceiver.kt`

- [ ] **Step 1: Write the receiver**

```kotlin
package com.chat.ai.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

class CustomReminderReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "CustomReminderReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val reminderId = intent?.getLongExtra("custom_reminder_id", -1) ?: return
        val content = intent.getStringExtra("custom_reminder_content") ?: return
        val weekDays = intent.getStringExtra("custom_reminder_weekdays") ?: return

        Log.d(TAG, "onReceive: id=$reminderId, content=$content, weekDays=$weekDays")

        val workRequest = OneTimeWorkRequestBuilder<CustomReminderWorker>()
            .setInputData(workDataOf(
                "custom_reminder_id" to reminderId,
                "custom_reminder_content" to content,
                "custom_reminder_weekdays" to weekDays
            ))
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/chat/ai/service/CustomReminderReceiver.kt
git commit -m "feat: add CustomReminderReceiver"
```

---

### Task 5: Add CustomReminderWorker with AI Voice

**Files:**
- Create: `app/src/main/java/com/chat/ai/service/CustomReminderWorker.kt`

- [ ] **Step 1: Write the worker**

```kotlin
package com.chat.ai.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chat.ai.ChatApplication
import com.chat.ai.MainActivity
import com.chat.ai.data.api.MimoTextApi
import com.chat.ai.data.repository.PersonaRepository
import com.chat.ai.util.ContextManager
import com.chat.ai.util.PrefsManager
import java.util.Calendar

class CustomReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "CustomReminderWorker"
        const val CHANNEL_ID = "custom_reminder_channel"
    }

    override suspend fun doWork(): Result {
        val reminderId = inputData.getLong("custom_reminder_id", -1)
        val content = inputData.getString("custom_reminder_content") ?: return Result.failure()
        val weekDays = inputData.getString("custom_reminder_weekdays") ?: return Result.failure()

        Log.d(TAG, "doWork: id=$reminderId, content=$content, weekDays=$weekDays")

        // Check if today matches the weekday schedule
        val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        val todayInOurFormat = if (today == 1) 7 else today - 1
        val activeDays = weekDays.split(",").mapNotNull { it.trim().toIntOrNull() }

        if (activeDays.isNotEmpty() && !activeDays.contains(todayInOurFormat)) {
            Log.d(TAG, "Today ($todayInOurFormat) not in active days ($activeDays), skipping")
            return Result.success()
        }

        val app = applicationContext as ChatApplication
        val db = app.database
        val apiKey = PrefsManager.getApiKey(applicationContext)

        if (apiKey.isBlank()) {
            Log.e(TAG, "API key is blank")
            showNotification(content, "AI", reminderId)
            return Result.success()
        }

        val textApi = MimoTextApi(apiKey)
        val personaRepository = PersonaRepository(db.personaDao(), db.voiceConfigDao())
        val personaName = personaRepository.getActivePersona()?.name ?: "AI"
        val systemPrompt = personaRepository.getSystemPrompt()

        val currentTime = java.text.SimpleDateFormat("yyyy年MM月dd日 HH:mm", java.util.Locale.CHINA).format(java.util.Date())
        val prompt = "$systemPrompt\n\n现在是 $currentTime，用户设置了一个提醒：「$content」。请用一两句话自然地提醒用户，像朋友一样关心地提醒。动作和神态描述用括号括起来。\n\n【重要】回复必须简短，只用一两句话。"

        val result = textApi.sendMessage(prompt, emptyList())

        val message = result.getOrNull() ?: content
        Log.d(TAG, "AI generated message: $message")

        // Save to SharedPreferences for notification playback
        val prefs = applicationContext.getSharedPreferences("custom_reminder_voice", Context.MODE_PRIVATE)
        prefs.edit().putString("msg_$reminderId", message).apply()

        showVoiceNotification(message, personaName, reminderId)
        return Result.success()
    }

    private fun showVoiceNotification(message: String, personaName: String, reminderId: Long) {
        createNotificationChannel()
        val notificationId = (3000 + reminderId).toInt()

        // Play voice action
        val playIntent = Intent(applicationContext, CustomReminderVoiceReceiver::class.java).apply {
            putExtra("reminder_message", message)
            putExtra("notification_id", notificationId)
        }
        val playPendingIntent = PendingIntent.getBroadcast(
            applicationContext, notificationId + 50000, playIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Open app action
        val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val openPendingIntent = PendingIntent.getActivity(
            applicationContext, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(personaName)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_media_play, "播放语音", playPendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }

    private fun showNotification(message: String, personaName: String, reminderId: Long) {
        createNotificationChannel()
        val notificationId = (3000 + reminderId).toInt()

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(personaName)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "自定义提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "自定义时间提醒"
            }
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/chat/ai/service/CustomReminderWorker.kt
git commit -m "feat: add CustomReminderWorker with AI voice generation"
```

---

### Task 6: Add CustomReminderVoiceReceiver

**Files:**
- Create: `app/src/main/java/com/chat/ai/service/CustomReminderVoiceReceiver.kt`

- [ ] **Step 1: Write the receiver**

```kotlin
package com.chat.ai.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class CustomReminderVoiceReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "CustomReminderVoiceReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val message = intent?.getStringExtra("reminder_message") ?: return
        Log.d(TAG, "Playing voice for: $message")

        val tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.CHINESE
                tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "custom_reminder")
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/chat/ai/service/CustomReminderVoiceReceiver.kt
git commit -m "feat: add CustomReminderVoiceReceiver for notification voice playback"
```

---

### Task 7: Update AndroidManifest

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add new receivers**

Add these two receiver declarations inside the `<application>` tag, after the existing `ReminderReceiver`:

```xml
<receiver
    android:name=".service.CustomReminderReceiver"
    android:exported="true" />

<receiver
    android:name=".service.CustomReminderVoiceReceiver"
    android:exported="true" />
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "feat: register CustomReminderReceiver and VoiceReceiver in manifest"
```

---

### Task 8: Add CustomReminderScreen and ViewModel

**Files:**
- Create: `app/src/main/java/com/chat/ai/ui/reminder/CustomReminderViewModel.kt`
- Create: `app/src/main/java/com/chat/ai/ui/reminder/CustomReminderScreen.kt`

- [ ] **Step 1: Write the ViewModel**

```kotlin
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
```

- [ ] **Step 2: Write the CustomReminderScreen**

```kotlin
package com.chat.ai.ui.reminder

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chat.ai.data.model.CustomReminder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomReminderScreen(
    onNavigateBack: () -> Unit,
    viewModel: CustomReminderViewModel = viewModel()
) {
    val reminders by viewModel.reminders.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("自定义提醒") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Text("←") }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "添加提醒")
                    }
                }
            )
        }
    ) { padding ->
        if (reminders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("还没有自定义提醒，点击右上角 + 添加",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(reminders) { reminder ->
                    ReminderCard(
                        reminder = reminder,
                        onToggle = { viewModel.toggleEnabled(reminder) },
                        onDelete = { viewModel.deleteReminder(reminder) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddReminderDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { reminder ->
                viewModel.addReminder(reminder)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun ReminderCard(
    reminder: CustomReminder,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val dayNames = listOf("一", "二", "三", "四", "五", "六", "日")
    val activeDays = reminder.getWeekDayList()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (reminder.isEnabled)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = String.format("%02d:%02d", reminder.hour, reminder.minute),
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = reminder.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    dayNames.forEachIndexed { index, name ->
                        val dayNum = index + 1
                        val isActive = activeDays.contains(dayNum)
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (isActive)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isActive)
                                        MaterialTheme.colorScheme.onPrimary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Switch(
                    checked = reminder.isEnabled,
                    onCheckedChange = { onToggle() }
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReminderDialog(
    onDismiss: () -> Unit,
    onConfirm: (CustomReminder) -> Unit
) {
    var hour by remember { mutableStateOf(8) }
    var minute by remember { mutableStateOf(0) }
    var content by remember { mutableStateOf("") }
    var selectedDays by remember { mutableStateOf(setOf(1, 2, 3, 4, 5, 6, 7)) }
    val dayNames = listOf("一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "日" to 7)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加提醒") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("提醒内容") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("时间：")
                    var hourExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = hourExpanded,
                        onExpandedChange = { hourExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = String.format("%02d", hour),
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.width(70.dp).menuAnchor(),
                            textStyle = MaterialTheme.typography.bodyLarge
                        )
                        ExposedDropdownMenu(
                            expanded = hourExpanded,
                            onDismissRequest = { hourExpanded = false }
                        ) {
                            (0..23).forEach { h ->
                                DropdownMenuItem(
                                    text = { Text(String.format("%02d", h)) },
                                    onClick = { hour = h; hourExpanded = false }
                                )
                            }
                        }
                    }
                    Text(":")
                    var minuteExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = minuteExpanded,
                        onExpandedChange = { minuteExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = String.format("%02d", minute),
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.width(70.dp).menuAnchor(),
                            textStyle = MaterialTheme.typography.bodyLarge
                        )
                        ExposedDropdownMenu(
                            expanded = minuteExpanded,
                            onDismissRequest = { minuteExpanded = false }
                        ) {
                            (0..59 step 5).forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(String.format("%02d", m)) },
                                    onClick = { minute = m; minuteExpanded = false }
                                )
                            }
                        }
                    }
                }

                Text("重复：", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    dayNames.forEach { (name, dayNum) ->
                        FilterChip(
                            selected = selectedDays.contains(dayNum),
                            onClick = {
                                selectedDays = if (selectedDays.contains(dayNum)) {
                                    selectedDays - dayNum
                                } else {
                                    selectedDays + dayNum
                                }
                            },
                            label = { Text(name, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (content.isNotBlank() && selectedDays.isNotEmpty()) {
                        onConfirm(
                            CustomReminder(
                                hour = hour,
                                minute = minute,
                                content = content,
                                weekDays = selectedDays.sorted().joinToString(",")
                            )
                        )
                    }
                },
                enabled = content.isNotBlank() && selectedDays.isNotEmpty()
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/chat/ai/ui/reminder/
git commit -m "feat: add CustomReminderScreen with day-of-week selection"
```

---

### Task 9: Add Navigation and Settings Entry

**Files:**
- Modify: `app/src/main/java/com/chat/ai/ui/navigation/NavGraph.kt`
- Modify: `app/src/main/java/com/chat/ai/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Update NavGraph to add custom_reminders route**

In `NavGraph.kt`, add the import and composable route:

Add import:
```kotlin
import com.chat.ai.ui.reminder.CustomReminderScreen
```

Add inside `NavHost` block, after the `"screen_share"` composable:
```kotlin
composable("custom_reminders") {
    CustomReminderScreen(onNavigateBack = { navController.popBackStack() })
}
```

Update `SettingsScreen` call to pass the new navigation callback:
```kotlin
composable("settings") {
    SettingsScreen(
        onNavigateBack = { navController.popBackStack() },
        onNavigateToVoice = { navController.navigate("voice_settings") },
        onNavigateToScreenShare = { navController.navigate("screen_share") },
        onNavigateToCustomReminders = { navController.navigate("custom_reminders") }
    )
}
```

- [ ] **Step 2: Update SettingsScreen to add entry point**

In `SettingsScreen.kt`, update the function signature to add the new callback:

```kotlin
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToVoice: () -> Unit,
    onNavigateToScreenShare: () -> Unit,
    onNavigateToCustomReminders: () -> Unit = {}
) {
```

Add after the "定时提醒" section (after the reminder times text and before "数据管理"):

```kotlin
Spacer(modifier = Modifier.height(8.dp))
OutlinedButton(
    onClick = onNavigateToCustomReminders,
    modifier = Modifier.fillMaxWidth()
) {
    Text("自定义提醒管理")
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/chat/ai/ui/navigation/NavGraph.kt app/src/main/java/com/chat/ai/ui/settings/SettingsScreen.kt
git commit -m "feat: add custom reminders navigation and settings entry"
```

---

### Task 10: Add Conflict Resolution

**Files:**
- Modify: `app/src/main/java/com/chat/ai/service/ReminderWorker.kt`

- [ ] **Step 1: Add conflict check to ReminderWorker**

In `doWork()`, before generating the message, add a check for conflicting custom reminders. Replace the `doWork` method:

```kotlin
override suspend fun doWork(): Result {
    Log.d(TAG, "doWork called")

    val reminderType = inputData.getString(KEY_REMINDER_TYPE) ?: return Result.failure()
    Log.d(TAG, "Reminder type: $reminderType")

    // Check if a custom reminder conflicts at this time
    val now = java.util.Calendar.getInstance()
    val currentHour = now.get(java.util.Calendar.HOUR_OF_DAY)
    val currentMinute = now.get(java.util.Calendar.MINUTE)

    val app = applicationContext as ChatApplication
    val customReminder = app.database.customReminderDao().getByTime(currentHour, currentMinute)
    if (customReminder != null && customReminder.isEnabled && customReminder.isToday()) {
        Log.d(TAG, "Custom reminder ${customReminder.id} conflicts with $reminderType at $currentHour:$currentMinute, skipping fixed reminder")
        return Result.success()
    }

    val personaRepository = PersonaRepository(app.database.personaDao(), app.database.voiceConfigDao())
    val personaName = personaRepository.getActivePersona()?.name ?: "AI"

    val message = when (reminderType) {
        "breakfast" -> getBreakfastMessage(personaName)
        "lunch" -> getLunchMessage(personaName)
        "dinner" -> getDinnerMessage(personaName)
        "sleep" -> getSleepMessage(personaName)
        else -> return Result.failure()
    }

    showNotification(message, personaName)
    return Result.success()
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/chat/ai/service/ReminderWorker.kt
git commit -m "feat: skip fixed reminders when custom reminder conflicts"
```

---

### Task 11: Verify and Test

- [ ] **Step 1: Build the project**

Run: `cd "E:/vis project/Project14/android_chat_app" && ./gradlew assembleDebug`

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Manual testing checklist**

1. Open app → Settings → 自定义提醒管理
2. Add a custom reminder for 1 minute from now, select all days, enter content "测试提醒"
3. Wait for the notification to appear
4. Verify notification shows AI-generated text
5. Tap "播放语音" action button on notification
6. Verify voice plays
7. Add another custom reminder at the same time as breakfast (7:30)
8. Verify breakfast reminder is skipped when custom one fires
9. Toggle custom reminder off, verify it stops firing
10. Delete custom reminder, verify it's removed
