package com.chat.ai.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeFormatter {
    private val zhFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.CHINA)
    }

    fun nowZh(): String = zhFormat.get()!!.format(Date())
}
