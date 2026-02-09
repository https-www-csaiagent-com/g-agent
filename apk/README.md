# G-Agent APK —— 端侧 GUI 智能助手

<div align="center">

基于视觉语言模型的 Android 原生智能助手应用  
通过无障碍服务实现手机端自动化 GUI 操作

[English](README_EN.md) | 简体中文

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](../LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-purple.svg)](https://kotlinlang.org)

</div>

> 📖 本文档为 APK 子项目说明。完整的项目文档（包括 ADB 模式、模型部署等）请参阅 [项目主 README](../README.md)。

## 项目简介

G-Agent APK 是一个原生 Android 应用，它能够理解用户的自然语言指令，通过多模态视觉语言模型分析屏幕内容，并使用 Android 无障碍服务自动执行各种 GUI 操作。

与 ADB 模式不同，APK 模式**直接运行在手机上，无需连接电脑**，适合终端用户和演示场景。

### 核心特性

- 🎯 **自然语言控制**：用语音或文字描述需求，自动完成任务
- 👁️ **视觉理解**：基于多模态模型，理解屏幕内容和 UI 元素
- 🤖 **智能决策**：自动规划执行步骤，处理复杂多步骤任务
- 🎤 **语音输入**：内置 ASR 语音转文字，解放双手
- 🔒 **无需 Root**：基于无障碍服务，无需 Root 权限
- 💬 **极简界面**：对话式交互，简洁直观
- 🔄 **任务循环**：截图→分析→执行→检查，自动闭环
- 🛡️ **安全机制**：遇到登录/验证码等敏感操作自动请求用户协助

### 功能演示

支持的任务类型：

| 类型 | 示例 |
|------|------|
| 📦 应用操作 | "打开京东搜索感冒药" |
| 🔍 信息查询 | "在淘宝搜索蓝牙耳机并按价格排序" |
| 🍔 生活服务 | "打开美团点外卖" |
| 📱 内容浏览 | "在小红书搜索美食攻略" |
| ⚙️ 多步任务 | "打开微博看今天的热搜" |

## 快速开始

### 环境要求

| 项目 | 要求 |
|------|------|
| Android 版本 | 8.0（API 26）及以上 |
| 模型服务 | 可访问的 VLM API（云端/局域网） |
| 开发环境（编译用） | Android Studio 2022.3+、JDK 17 |

### 安装步骤

#### 方式 A：直接安装 APK（推荐）

1. 从 [Releases](https://github.com/https-www-csaiagent-com/g-agent/releases) 下载最新 APK
2. 在手机上安装 APK（需允许安装未知来源应用）
3. 开启无障碍服务权限

#### 方式 B：从源码编译

```bash
# 克隆项目
git clone https://github.com/https-www-csaiagent-com/g-agent.git
cd g-agent/apk

# Linux/macOS:
./gradlew assembleDebug

# Windows:
gradlew.bat assembleDebug

# APK 输出路径: app/build/outputs/apk/debug/app-debug.apk
```

### 权限配置

#### 1. 开启无障碍服务（必需）

```
设置 → 无障碍 → 已下载的服务 → 智能手机助手 → 开启
```

#### 2. 截图权限

首次执行任务时系统会弹出授权请求，点击"允许"。

#### 3. 录音权限（语音输入需要）

使用语音模式时需要授予麦克风权限。

#### 4. 后台保活

```
设置 → 应用管理 → 智能手机助手 → 允许后台运行
设置 → 电池 → 将本应用加入白名单
```

### 配置模型服务

APK 需要连接 VLM 模型服务才能分析屏幕。

#### 修改默认配置

编辑 `app/src/main/java/com/agent/mobileagent/util/PreferenceManager.kt`：

```kotlin
private const val DEFAULT_VLLM_URL = "http://your-server:8002/v1"
private const val DEFAULT_MODEL_NAME = "cs-glm"
private const val DEFAULT_ASR_URL = "http://your-server:8787/recognize"
```

#### 本地部署模型

```bash
pip install vllm

python3 -m vllm.entrypoints.openai.api_server \
    --served-model-name cs-glm \
    --allowed-local-media-path / \
    --mm-encoder-tp-mode data \
    --mm_processor_cache_type shm \
    --mm_processor_kwargs "{\"max_pixels\":5000000}" \
    --max-model-len 25480 \
    --chat-template-content-format string \
    --limit-mm-per-prompt "{\"image\":10}" \
    --model your-model-path \
    --port 8000
```

> 详细的模型部署说明请参阅 [项目主 README - 模型服务](../README.md#模型服务)。

## 使用指南

### 文字输入

1. 在输入框输入任务描述
2. 点击"发送"按钮执行
3. 应用自动最小化，开始执行任务
4. 可点击示例快速填充输入框

### 语音输入

1. 点击左侧键盘图标切换到语音模式
2. 按住"按住 说话"按钮录音
3. 松开后自动识别并填入输入框
4. 确认后点击"发送"执行

### 停止任务

任务执行时，点击右下角红色浮动按钮停止。

## 系统架构

```
┌──────────────────────────────────────────┐
│            MainActivity                   │
│     （用户界面 · 输入控制 · 语音录制）        │
└──────────────┬───────────────────────────┘
               │
    ┌──────────┼──────────────┐
    │          │              │
┌───▼────┐ ┌──▼──────┐ ┌────▼────┐
│ Agent  │ │ Screen  │ │  ASR    │
│Service │ │ Capture │ │ Client  │
│        │ │ Service │ │         │
│ 任务循环 │ │ 截图权限  │ │ 语音转文字 │
└───┬────┘ └─────────┘ └─────────┘
    │
    ├── 1. 截取屏幕 (takeScreenshot)
    ├── 2. 调用 VLM 模型 (AgentApiClient)
    ├── 3. 解析动作指令 (ActionResponse)
    ├── 4. 执行操作 (performClick/Swipe/Input...)
    └── 5. 循环直到完成或出错
    │
┌───▼────────────────────────────────────┐
│   AutoAgentAccessibilityService         │
│     （无障碍服务 · 核心执行引擎）           │
│                                         │
│   · 点击 (GestureDescription)           │
│   · 滑动 (GestureDescription)           │
│   · 输入 (ClipBoard + Paste)            │
│   · 返回/Home (GlobalAction)            │
│   · 打开应用 (PackageManager)            │
│   · 截图 (Android 11+ API)              │
└─────────────────────────────────────────┘
```

## 支持的操作

| 操作 | 说明 | JSON 格式 |
|------|------|----------|
| `click` | 点击屏幕元素 | `{"action_type": "click", "box_2d": [[x1,y1,x2,y2]]}` |
| `swipe` | 滑动屏幕 | `{"action_type": "swipe", "start_point": [x,y], "end_point": [x,y]}` |
| `input_text` | 输入文字 | `{"action_type": "input_text", "text": "内容", "box_2d": [[x1,y1,x2,y2]]}` |
| `navigate_back` | 返回上一页 | `{"action_type": "navigate_back"}` |
| `open_app` | 打开应用 | `{"action_type": "open_app", "app_name": "京东"}` |
| `wait` | 等待加载 | `{"action_type": "wait"}` |
| `status` | 任务状态 | `{"action_type": "status", "goal_status": "complete"}` |
| `call_user` | 请求协助 | `{"action_type": "call_user", "text": "需要登录"}` |

**坐标说明**：坐标为归一化值（0-999），自动转换为实际像素。

## 支持的应用

| 分类 | 应用 |
|------|------|
| 电商购物 | 京东、淘宝、拼多多、闲鱼 |
| 美食外卖 | 美团、饿了么 |
| 社交通讯 | 微信、QQ、微博、钉钉 |
| 视频娱乐 | 抖音、快手、哔哩哔哩、爱奇艺、腾讯视频 |
| 内容社区 | 小红书、知乎、百度 |
| 生活服务 | 高德地图、百度地图、支付宝、云闪付 |
| 阅读资讯 | 掌阅、番茄小说、东方财富 |

> 添加更多应用：在 `AutoAgentAccessibilityService.kt` 的 `resolveAppPackage()` 中添加映射。

## 项目结构

```
apk/
├── app/
│   ├── build.gradle                    # 应用构建配置
│   └── src/main/
│       ├── AndroidManifest.xml         # 应用清单（权限、服务声明）
│       ├── java/com/agent/mobileagent/
│       │   ├── MainActivity.kt         # 主界面（输入、语音、任务控制）
│       │   ├── model/
│       │   │   └── ActionModels.kt     # 数据模型（动作、请求、响应、状态）
│       │   ├── service/
│       │   │   ├── AgentService.kt     # Agent 后台服务（任务循环调度）
│       │   │   ├── AutoAgentAccessibilityService.kt  # 无障碍服务（操作执行）
│       │   │   └── ScreenCaptureService.kt           # 截图权限服务
│       │   ├── network/
│       │   │   ├── AgentApiClient.kt   # VLM API 客户端（Prompt + 解析）
│       │   │   └── AsrApiClient.kt     # ASR 语音识别客户端
│       │   └── util/
│       │       ├── LogUtils.kt         # 日志工具
│       │       └── PreferenceManager.kt # 配置管理
│       └── res/
│           ├── layout/                 # 界面布局
│           ├── values/                 # 字符串、颜色、主题
│           └── xml/                    # 无障碍服务配置
├── build.gradle                        # 项目根构建文件
├── settings.gradle                     # 项目设置
├── gradle.properties                   # Gradle 属性
└── gradle/wrapper/                     # Gradle Wrapper
```

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Kotlin | 1.9.22 | 开发语言 |
| compileSdk | 34 | 编译 SDK |
| minSdk | 26 (Android 8.0) | 最低支持 |
| Material Design 3 | 1.11.0 | UI 框架 |
| OkHttp3 | 4.12.0 | 网络请求 |
| Gson | 2.10.1 | JSON 解析 |
| Kotlin Coroutines | 1.7.3 | 异步处理 |
| Lifecycle | 2.7.0 | 生命周期管理 |

## 开发指南

### 修改 Prompt

编辑 `network/AgentApiClient.kt` 中的 `buildPrompt()` 方法：

```kotlin
private fun buildPrompt(query: String, historyInfo: String): String {
    return """
You are a GUI Agent...
// 修改 Prompt 内容
""".trimIndent()
}
```

### 添加应用映射

编辑 `service/AutoAgentAccessibilityService.kt` 中的 `resolveAppPackage()` 方法。

### 编译构建

```bash
# 命令行编译
./gradlew assembleDebug      # Debug 版本
./gradlew assembleRelease    # Release 版本

# 输出路径
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

## 常见问题

### Q: 无障碍服务开启后自动关闭？

部分手机有省电优化：

1. `设置 → 应用管理 → 智能手机助手 → 允许后台运行`
2. `设置 → 电池 → 将本应用加入白名单`

### Q: 截图失败？

1. 确保已授予截图权限
2. Android 11+ 使用无障碍服务自带截图，确保服务已开启
3. 低版本需要 MediaProjection 权限

### Q: 模型调用失败？

1. 检查网络连接
2. 确认模型服务正在运行
3. 查看日志：`adb logcat -s MobileAgent`

### Q: 语音识别失败？

1. 确保已授予录音权限
2. 检查 ASR 服务是否正常
3. 录音至少 1 秒

## 许可证

本项目采用 [MIT License](../LICENSE) 开源协议。

## 相关链接

- [G-Agent 主项目](../README.md) — 完整项目文档
