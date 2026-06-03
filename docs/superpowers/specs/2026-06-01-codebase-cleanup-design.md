# 代码质量优化 — 设计文档

> 状态：待实施
> 日期：2026-06-01
> 范围：`android_chat_app/app/src/main/java/com/chat/ai/`

## 背景

`ChatAI` 项目在功能持续叠加后，代码出现了明显的"屎山"特征：手搓 DI 在多处复读、Notification/Prompt 模板复制粘贴、`SettingsScreen` 单文件 324 行、调试日志满天飞。本次重构**只做整理，不改功能**，目标是降低后续维护成本和回归风险。

## 非目标

- 不改任何对外功能与 UI 行为
- 不替换 OkHttp / Gson / Compose / Room 等基础库
- 不引入 Hilt/Koin（按用户决定使用 ServiceLocator）
- 不重构 `TtsManager` 内部实现（仅做实例共享）

## 现状问题清单（保留供 review）

### A. 严重重复
1. `ChatViewModel`、`ProactiveWorker`、`ReminderWorker`、`CustomReminderWorker` 各自手搓 DI（new `MimoTextApi/ContextManager/ChatRepository/PersonaRepository`）
2. `ProactiveWorker` 与 `CustomReminderWorker` 的 `createNotificationChannel` + `showNotification` + `showVoiceNotification` 几乎复制粘贴
3. Prompt 片段（"动作神态用括号""【当前时间】..."）散布在 4 个文件
4. `SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.CHINA)` 在 5 处复读
5. `OkHttpClient` 三个 API 类各自 new 一份

### B. 大文件 / 职责乱
6. `SettingsScreen` 324 行，6 个独立 section 杂糅在一起
7. `ChatViewModel.sendMessage` vs `sendMessageWithImage` 大段重复，且**直接调 `db.messageDao().insert(...)` 绕过 Repository**
8. `ChatRepository.sendMessage` 自己拼 prompt 模板，本应是上层职责

### C. 调试残留 / 安全
9. `ProactiveScheduler` `=== START ===` 类调试 Log 满屏
10. `MimoVisionApi.Log.d("Request: $requestBody")` 把 base64 图片打到 logcat
11. `MimoTtsApi` 同样 log 含 base64 音频的 request body
12. `PrefsManager` 每次访问都重建 `EncryptedSharedPreferences`（昂贵）
13. `ChatViewModel.apiKey` init 时一次性读取，用户改 key 后必须重启
14. `TtsManager` 每个 ViewModel 各自 new（之前 memory 写过"共享"，但代码里没共享）
15. `ContextManager.generateSummary` 每次 ChatVM init 触发，无节流，每次开 app 烧 token
16. `MainActivity.onCreate` 连续 3 次 `startActivity`（通知权限 + 精确闹钟 + 电池优化），用户体验灾难

## 设计

### 架构概览

```
ChatApplication
└── ServiceLocator               <- 集中持有单例
    ├── okHttpClient: OkHttpClient
    ├── textApi: MimoTextApi      (按 apiKey 重建)
    ├── visionApi: MimoVisionApi  (按 apiKey 重建)
    ├── ttsApi: MimoTtsApi        (按 ttsApiKey 重建)
    ├── personaRepository
    ├── contextManager
    ├── chatRepository
    └── ttsManager                (App 级单例)

util/
├── PromptTemplates    <- 集中所有提示词片段
├── TimeFormatter      <- nowZh()
└── PrefsManager       <- 缓存 prefs 实例

ui/common/
└── NotificationHelper <- 统一 channel/builder
```

### Phase A — 基础设施（消重）

**A1. `util/ServiceLocator.kt`**
- 接收 `ChatApplication`，懒加载所有共享实例
- API 类按 key 缓存：`fun textApi(): MimoTextApi`，内部检查 key 是否变了，变了则重建（解决问题 #13）
- `okHttpClient` 单例，3 个 API 共享（解决问题 #5）

**A2. `ui/common/NotificationHelper.kt`**
- `ensureChannel(channelId, name, description)` 幂等创建
- `buildBasic(context, channelId, title, text, contentIntent, autoCancel = true)` 返回 `Notification`
- `buildVoice(context, channelId, title, text, contentIntent, playIntent? = null)`
- 解决问题 #2

**A3. `util/PromptTemplates.kt`**
- `const val ACTION_RULE = "动作和神态描述必须用括号括起来，例如：（微笑）（点头）"`
- `fun lengthRule(level: String): String` 返回简短/正常/详细对应文案
- `fun timeSuffix(): String` 返回 `"\n\n【当前时间】现在是 ${TimeFormatter.nowZh()}"`
- `fun compose(systemPrompt: String, vararg parts: String): String`
- 解决问题 #3

**A4. `util/TimeFormatter.kt`**
- 单例 `SimpleDateFormat`（thread-local 或 synchronized）
- `fun nowZh(): String`
- 解决问题 #4

**A5. `PrefsManager` 改为缓存**
- 单 `EncryptedSharedPreferences` 实例，懒加载，按 `appContext` 持有
- 解决问题 #12

### Phase B — 拆大文件 / 理职责

**B1. 拆分 `SettingsScreen`**
- `SettingsScreen.kt` 仅保留 `Scaffold` + 调用各 section
- 新增同目录下：
  - `ApiConfigSection.kt`
  - `UserAvatarSection.kt`
  - `ResponseLengthSection.kt`
  - `ProactiveSection.kt`
  - `ReminderSection.kt`
  - `DataManagementSection.kt`
- 解决问题 #6

**B2. `ChatViewModel` 收敛**
- 提取私有 `suspend fun sendInternal(content, imageBytes? = null)`
- 移除直接 `db.messageDao().insert(...)`，全部走 Repository
- 让 `ChatRepository` 新增 `sendImageMessage(content, imageBytes, systemPrompt, ...)` 方法封装 vision 调用
- ServiceLocator 注入：`textApi/visionApi/ttsManager` 都从 SL 拿
- 解决问题 #7、#13、#14

**B3. `ChatRepository` 解耦提示词**
- 接收"已组装好的 system prompt"，不再自己拼"动作神态规则"模板
- 调用方（ViewModel/Worker）通过 `PromptTemplates.compose(...)` 拼好再传入
- 解决问题 #8

**B4. Worker 收敛**
- `ProactiveWorker`、`ReminderWorker`、`CustomReminderWorker` 全部从 `ServiceLocator` 取依赖，不再 new
- 通知发送统一通过 `NotificationHelper`
- 解决问题 #1、#2

### Phase C — 日志整顿

- 删除 `=== START ===` `=== END ===` 类调试日志（`ProactiveScheduler` 等）
- API 类不 log request body（`MimoVisionApi`、`MimoTtsApi`）
- 保留 `Log.e(...)` 错误日志和有诊断价值的 `Log.d`（如 Worker 入口"doWork called"，response code 等）
- 解决问题 #9、#10、#11

### Phase D — 隐患修复

**D1. `ContextManager.generateSummary` 节流**
- 在 `Summary` 表新增 `messageCountAtSnapshot: Int` 字段（轻量 schema 升级，db version 8→9，**仍用 `fallbackToDestructiveMigration`** 保持现状不引入新风险）
- 摘要时记录当时消息总数；下次只在新增消息 ≥ N 时才重新摘要
- 解决问题 #15

**D2. `MainActivity` 权限请求**
- 提取 `util/PermissionsBootstrap.kt`
- 用 Compose `AlertDialog` 序列：
  1. 通知权限（Android 13+）
  2. 精确闹钟（Android 12+）
  3. 电池优化豁免
- 每步说明用途，给"暂不"选项（不阻塞 app 使用）
- 状态保存到 `PrefsManager`，下次启动跳过已询问过的项
- 解决问题 #16

## 数据流（变化前后）

**变化前**（举例 `ProactiveWorker.doWork`）：
```
WorkerParam → Context → 手搓 4 个对象 → 直接拼 prompt → API → 自己建 channel → 自己 build notification
```

**变化后**：
```
WorkerParam → ServiceLocator → 拿现成对象 → PromptTemplates.compose → API → NotificationHelper.send
```

## 错误处理

- 不改变现有 `Result<T>` 模式
- 不新增 try-catch（除非是修问题 #13 的 key 变更逻辑）
- ServiceLocator 在 init 阶段（首次 `getInstance(app)`）若 db 未就绪应抛出，不静默吞错

## 测试

项目当前**没有测试**。本次重构以"行为不变"为目标，验证手段：
- 编译通过（`./gradlew assembleDebug`）
- 手动冒烟：发消息 / 主动消息 / 定时提醒 / 自定义提醒 / TTS / 屏幕共享 / 设置项各开关
- **不引入测试框架**（YAGNI，超出本次 scope）

## 提交划分（4 个）

1. `refactor: 提取 ServiceLocator/NotificationHelper/PromptTemplates/TimeFormatter` (Phase A)
2. `refactor: 拆分 SettingsScreen 与 ChatViewModel 职责` (Phase B)
3. `chore: 清理调试日志，移除 API request body 日志` (Phase C)
4. `fix: PrefsManager 缓存、摘要节流、权限请求体验` (Phase D)

## 风险

- **Phase D1 改 db schema**：`fallbackToDestructiveMigration` 已在用，重新安装会清空数据库。这与现有行为一致，不新增用户感知风险。
- **Phase D2 权限流改动可见**：用户体验会变好，但流程变了。改动隔离在 `PermissionsBootstrap`，回滚方便。
- **Phase B 改 `ChatRepository` 接口**：调用方仅 `ChatViewModel` 和 `ProactiveWorker`，全部一起改，无外部依赖。

## 不做的事（YAGNI）

- 不引入 Hilt / Koin
- 不引入测试框架
- 不重构 `TtsManager`、`AsrManager`、`ScreenCapture` 内部实现
- 不替换 Gson 为 kotlinx.serialization
- 不改 API 端点 / 模型 ID / temperature 等业务参数
- 不抽 `BaseWorker` 抽象类（3 个 Worker 行为差异较大，强行抽容易反向耦合）
