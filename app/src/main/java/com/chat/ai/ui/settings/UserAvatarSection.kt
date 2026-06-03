package com.chat.ai.ui.settings

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chat.ai.util.PrefsManager
import java.io.File

@Composable
fun UserAvatarSection() {
    val context = LocalContext.current
    var userAvatarPath by remember { mutableStateOf(PrefsManager.getUserAvatar(context)) }
    val userAvatarLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { stream ->
                val bytes = stream.readBytes()
                val avatarFile = File(context.filesDir, "user_avatar.jpg")
                avatarFile.writeBytes(bytes)
                userAvatarPath = avatarFile.absolutePath
                PrefsManager.setUserAvatar(context, avatarFile.absolutePath)
            }
        }
    }

    Text("用户头像", style = MaterialTheme.typography.titleMedium)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (userAvatarPath.isNotBlank() && File(userAvatarPath).exists()) {
            val bitmap = BitmapFactory.decodeFile(userAvatarPath)
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "用户头像",
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .clickable { userAvatarLauncher.launch("image/*") },
                    contentScale = ContentScale.Crop
                )
            }
        } else {
            OutlinedButton(
                onClick = { userAvatarLauncher.launch("image/*") }
            ) {
                Text("选择头像")
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text("点击头像可更换")
    }
}
