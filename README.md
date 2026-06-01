# ChatAI

基于 MiMo API 的 Android 聊天应用，支持语音对话、屏幕共享、图片分析、主动消息、自定义提醒等功能。

## 功能

- **AI 聊天** — 基于人设的自然对话，支持上下文记忆和对话摘要
- **语音合成 (TTS)** — 支持内置音色、音色克隆、AI 设计音色
- **图片分析** — 发送图片让 AI 基于人设和上下文描述
- **屏幕共享** — 实时捕获屏幕，AI 分析并语音播报
- **主动消息** — AI 定时主动发消息（问候、关心、分享想法）
- **定时提醒** — 早餐/午餐/晚餐/睡觉提醒，AI 生成自然语言
- **自定义提醒** — 自定义时间和内容，支持选择星期几，AI 语音提醒
- **人物设定** — 自定义 AI 名字、性格、说话风格、背景故事

## API 兼容协议

本应用使用 MiMo API，兼容以下协议格式：

### 聊天 & 识图 API（共用一个 API Key）
- **端点**: `https://token-plan-cn.xiaomimimo.com/anthropic/v1/messages`
- **认证**: `x-api-key` Header
- **模型**: `mimo-v2.5`
- **协议**: Anthropic Messages API
- **功能**: 文本对话、图片分析、屏幕共享识别
- **说明**: 聊天和识图使用同一个 API Key，对应应用内「聊天 API Key」输入框

### TTS API（独立 API Key）
- **端点**: `https://api.xiaomimimo.com/v1/chat/completions`
- **认证**: `api-key` Header（与聊天 API 不同）
- **模型**: `mimo-v2.5-tts`（内置音色）、`mimo-v2.5-tts-voiceclone`（克隆）
- **协议**: OpenAI Chat Completions API
- **说明**: 对应应用内「TTS API Key」输入框

## 模型需要的功能

| 功能 | 要求 |
|------|------|
| 文本对话 | 支持 system prompt、多轮对话、上下文消息 |
| 回复控制 | temperature 参数（0.7） |
| 语音合成 | 支持内置音色选择、音色克隆（base64 音频输入）、AI 设计音色 |
| 图片理解 | 支持 base64 图片输入、结合文本 prompt 分析 |
| 中文能力 | 流利的中文对话和理解 |

## 技术栈

- Kotlin + Jetpack Compose
- Room 数据库
- OkHttp 网络请求
- AlarmManager + WorkManager 定时任务
- MediaProjection 屏幕捕获
- TextToSpeech 系统语音

## 构建

```bash
# Debug 版本
./gradlew assembleDebug

# Release 版本（需要先生成 keystore）
./gradlew assembleRelease
```

## 权限

- `INTERNET` — 网络请求
- `RECORD_AUDIO` — 语音输入
- `POST_NOTIFICATIONS` — 通知
- `SCHEDULE_EXACT_ALARM` — 精确闹钟
- `FOREGROUND_SERVICE` — 前台服务（主动消息、屏幕共享）
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — 电池优化豁免
