# Lan-Chat 应用 README

这是一个基于现代安卓技术栈构建的本地局域网（LAN）实时聊天应用。项目旨在展示如何利用最新的 Jetpack 库和最佳实践来构建一个功能完整、架构清晰、性能优异且高度健壮的应用程序。
> Tips: 项目整体框架由作者在阅读、学习Google《Android 之 Compose 开发基础》之后完，在借助Google gemini完成。

## 产品功能介绍

Lan-Chat 是一款轻量级的局域网内即时通讯工具，让您在同一个 Wi-Fi 网络下（可以连接到同一个后端API服务器，这里的消息接口都是后端API实现的），无需连接互联网，即可与朋友和同事进行快速、安全地沟通。

*   **个性化昵称设置**: 用户首次使用需设定专属昵称，作为您在聊天中的唯一身份标识。
*   **实时文本聊天**: 在同一网络下，与朋友进行毫秒级的即-时消息沟通。
*   **丰富媒体共享**: 不仅支持文字，还可以轻松地分享相册中的图片和手机存储中的各类文件。
*   **清晰的消息界面**: 采用现代化的气泡对话形式，清晰地区分自己与他人的消息，并显示发送者昵称与时间，让沟通一目了然。

## 程序概要设计

本应用遵循 Google 官方推荐的**现代化安卓应用架构**，以 **MVVM (Model-View-ViewModel)** 作为核心设计模式，实现了严格的关注点分离和单向数据流（Unidirectional Data Flow）。


#### **1. View (视图层) - 用户交互的起点**
*   **组件**: `MainActivity.kt` 中的 `ChatScreenContent` Composable。
*   **职责**: 它的职责是**“显示”**和**“通知”**。
    1.  **显示**: 它通过 `viewModel.messages.collectAsStateWithLifecycle()` 订阅 `ViewModel` 中的消息列表状态。当状态改变时，Compose 框架会自动重绘界面，显示最新的消息。
    2.  **通知**: 当用户点击“发送”按钮时，`Button` 的 `onClick` Lambda 表达式被触发。它**不会自己处理逻辑**，而是直接调用 `viewModel.sendMessage(text)`，将“用户想要发送一条消息”这个**意图**通知给 `ViewModel`。

#### **2. ViewModel (视图模型) - 业务逻辑的处理中心**
*   **组件**: `ChatViewModel.kt`
*   **职责**: 它是连接 UI 和数据的**大脑**。
    1.  **接收意图**: `sendMessage` 方法被 `View` 调用。
    2.  **处理业务**: 它可能会在这里处理一些业务逻辑，比如检查消息是否为空、是否包含敏感词等（本项目中为简化，直接转发）。
    3.  **委托数据操作**: 它不直接进行网络请求。而是调用 `repository.sendTextMessage(content)`，将数据操作任务**委托**给 `Model` 层的 `Repository`。`ViewModel` 不关心数据是发往 WebSocket 还是存入数据库，它只跟 `Repository` 这个唯一的“数据管家”对话。

#### **3. Model (模型层) - 数据的来源与去向**
*   **组件**: `ChatRepository.kt`, `WebSocketManager.kt`
*   **职责**: 这是所有**具体数据工作**的执行层。
    1.  **Repository 的调度**: `ChatRepository` 的 `sendTextMessage` 方法被 `ViewModel` 调用。它作为一个“调度中心”，决定这个任务应该交给谁。在这里，它调用 `webSocketManager.sendMessage(jsonString)`。
    2.  **数据源的执行**: `WebSocketManager` 是真正干活的人。它负责将文本消息序列化成 JSON 字符串，然后通过底层的 OkHttp WebSocket 客户端，将数据**发送到网络**中。

#### **数据的回流 (单向数据流闭环)**

1.  `WebSocketManager` 的 `onMessage` 监听器收到从服务器推回来的新消息。
2.  它立刻将这条新消息放入一个名为 `_messages` 的 `StateFlow`（一个数据流管道）中。
3.  `ChatRepository` 正在**订阅**这个 `StateFlow`。它一收到新数据，就立刻把它传递到自己对外暴露的 `messages` Flow 中。
4.  `ChatViewModel` 也正在**订阅** `ChatRepository` 的 `messages` Flow。它一收到新数据，就立刻更新自己持有的 `messages` 状态。
5.  由于 `View` 正在**订阅** `ChatViewModel` 的 `messages` 状态，Compose 框架检测到状态变化，自动触发**重组（Recomposition）**，UI 界面就这样被刷新了，新的消息 magically 出现在了屏幕上。


## 技术亮点

*   **健壮的网络连接管理**: 目前项目没有用到后台加载任务因此会碰到切刀后台webSocket就会断开。因此本项目在 `ChatViewModel` 中实现了一套重连策略：
    1.  **自动触发**: 当应用进入前台或网络连接意外断开时，会自动启动重连“管家”。
    2.  **限时重试**: “管家”会在一个2分钟的时间窗口内，持续地、每隔数秒尝试重新连接，以应对用户的切换后台。
    3.  **超时机制**: 如果2分钟后依然无法连接成功，“管家”会停止自动尝试，以避免不必要的电量消耗。
    4.  **用户控制**: 在超时后，UI上会显示一个明确的“点击重试”按钮，将最终的控制权交还给用户，提供了清晰的故障反馈和恢复路径。

*   **混合网络模型**: 针对不同的通信场景，项目聪明地结合了两种最合适的网络技术，实现了性能和稳定性的最优化：
    *   **WebSocket (用于实时通信)**: 对于聊天消息这种需要极低延迟、服务器可以主动推送、客户端与服务器需要频繁双向沟通的场景，WebSocket 保证了消息的“实时性”。
    *   **Retrofit (用于文件传输)**: 对于上传/下载图片、文件这类大数据量的、一次性的操作，传统的基于 HTTP 的 RESTful API 模式更为成熟和可靠。Retrofit非常适合这种“请求-响应”式的交互。 这种为不同场景选择最优解的“混合模式”，是构建高性能、高可靠性应用的标志。
