# ChatAI

基于 MiMo API 的 Android 聊天应用，支持流式对话、实体记忆、语音对话、屏幕共享、图片分析、悬浮气泡等功能。

## 功能

### 核心对话
- **流式回复** — AI 回复逐 token 实时显示，带闪烁光标打字效果
- **Markdown 渲染** — AI 回复支持加粗、斜体、代码格式
- **回复长度控制** — 简短/正常/详细三档，应用到文字聊天、图片分析、屏幕共享
- **上下文记忆** — 最近 50 条可见消息 + 摘要 + 实体记忆，三层记忆架构

### 记忆系统
- **实体记忆** — AI 自动从对话中提取关键事实，带重要性评分（1-10）
- **三种类型** — 事实与偏好、经历与事件、洞察与总结
- **记忆反思** — 每 20 条消息自动从已有记忆中提炼更高层次的洞察
- **去重机制** — 提取时参考已有记忆，不重复存储
- **查看记忆** — 设置页可查看所有记忆，按类型分组

### 语音 & 多媒体
- **语音合成 (TTS)** — 支持内置音色、音色克隆、AI 设计音色
- **TTS 优先级** — 用户聊天 > 屏幕共享 > 提醒，高优先级打断低优先级
- **语音输入 (ASR)** — 语音转文字
- **图片分析** — 发送图片或拍照让 AI 描述，支持直接调用相机
- **屏幕共享** — 实时捕获屏幕，AI 分析并语音播报

### 人设系统
- **自定义人设** — 名字、性别、性格、说话风格、背景故事、关系
- **反幻觉规则** — AI 始终保持角色身份，不暴露 AI 身份
- **口语化表达** — 自然的语气词、省略句，像朋友聊天
- **时间感知** — 根据时间段自动调整问候语气

### 消息 & 提醒
- **主动消息** — AI 定时主动发消息（问候、关心、分享想法）
- **定时提醒** — 早餐/午餐/晚餐/睡觉提醒，AI 生成自然语言
- **自定义提醒** — 自定义时间和内容，支持选择星期几，AI 语音提醒

### UI & 交互
- **消息气泡** — 圆角设计（20dp），AI 名字显示在头像右侧
- **页面切换动画** — 滑入 + 淡入效果
- **滚动动画** — 新消息平滑滚动到底部
- **Pull-to-Refresh** — 下拉加载更早的消息
- **TopAppBar** — AI 头像 + 名字 + 状态副标题
- **悬浮气泡** — 全局悬浮球，点击展开迷你聊天窗口

### 数据管理
- **查看记忆** — 在设置中查看 AI 的所有记忆
- **清除数据** — 一键清除聊天记录、摘要、记忆
- **加密存储** — API Key 使用 EncryptedSharedPreferences 加密保存

## API 兼容协议

### 聊天 & 识图 API（共用一个 API Key）
- **端点**: `https://token-plan-cn.xiaomimimo.com/anthropic/v1/messages`
- **认证**: `x-api-key` Header
- **模型**: `mimo-v2.5-pro`（聊天）、`mimo-v2.5`（识图）
- **协议**: Anthropic Messages API（支持 SSE 流式响应）
- **功能**: 流式文本对话、图片分析、屏幕共享识别

### TTS API（独立 API Key）
- **端点**: `https://api.xiaomimimo.com/v1/chat/completions`
- **认证**: `api-key` Header（与聊天 API 不同）
- **模型**: `mimo-v2.5-tts`（内置音色）、`mimo-v2.5-tts-voiceclone`（克隆）
- **协议**: OpenAI Chat Completions API

## 技术栈

- Kotlin + Jetpack Compose + Material 3
- Room 数据库（6 个实体：Message、Persona、Summary、VoiceConfig、CustomReminder、Memory）
- OkHttp 网络请求 + SSE 流式响应
- AlarmManager + WorkManager 定时任务
- MediaProjection 屏幕捕获
- CameraX 相机拍照
- ML Kit 中文文字识别
- EncryptedSharedPreferences 加密存储

## 构建

```bash
# Debug 版本
./gradlew assembleDebug

# Release 版本（需要先配置 keystore）
./gradlew assembleRelease
```

## 权限

| 权限 | 用途 |
|------|------|
| `INTERNET` | 网络请求 |
| `RECORD_AUDIO` | 语音输入 |
| `CAMERA` | 拍照 |
| `POST_NOTIFICATIONS` | 通知 |
| `SCHEDULE_EXACT_ALARM` | 精确闹钟 |
| `FOREGROUND_SERVICE` | 前台服务（主动消息、屏幕共享、悬浮气泡） |
| `SYSTEM_ALERT_WINDOW` | 悬浮气泡 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 电池优化豁免 |
