package com.chat.ai.util

object PromptTemplates {
    const val ACTION_RULE =
        "动作和神态描述必须用括号括起来，例如：（微笑）（点头）（思考）"

    const val SHORT_RULE =
        "你必须用非常简短的一两句话回复，不要展开。"

    fun lengthRule(level: String): String = when (level) {
        "short" -> "【回复长度要求】你必须用1-2句话回复，不超过50字，不要展开说明。"
        "long" -> "【回复长度要求】请详细回复，至少100字以上，分段展开说明。"
        else -> "【回复长度要求】正常回复，3-5句话。"
    }

    fun currentTime(): String = "【当前时间】现在是 ${TimeFormatter.nowZh()}"

    fun compose(systemPrompt: String, vararg parts: String): String = buildString {
        append(systemPrompt)
        for (part in parts) {
            if (part.isBlank()) continue
            append("\n\n")
            append(part)
        }
    }
}
