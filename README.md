# G-Agent —— 移动端 GUI 智能操作框架

<div align="center">

**基于多模态视觉语言模型的移动端应用自主操作框架**

[English](README_EN.md) | 简体中文

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Python](https://img.shields.io/badge/python-3.10+-blue.svg)](https://www.python.org/)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-purple.svg)](https://kotlinlang.org)

</div>

## 项目简介

G-Agent 是一个基于多模态视觉语言模型（VLM）的移动端 GUI 智能操作框架，能够理解用户的自然语言指令，通过视觉模型分析手机屏幕内容，自动规划并执行操作，帮助用户完成复杂的手机任务。

系统通过 ADB（Android Debug Bridge）或无障碍服务来控制设备，以视觉语言模型进行屏幕感知，再结合智能规划能力生成并执行操作流程。用户只需用自然语言描述需求，如"打开美团搜索附近的火锅店"，G-Agent 即可自动解析意图、理解当前界面、规划下一步动作并完成整个流程。系统还内置敏感操作确认机制，并支持在登录或验证码场景下进行人工接管。

> ⚠️ 本项目仅供研究和学习使用。严禁用于非法获取信息、干扰系统或任何违法活动。

本项目提供 **两种使用方式**：

| 模式 | 说明 | 适用场景 |
|------|------|---------|
| 📱 **APK 模式（端侧执行）** | 安装原生 Android APK，通过**无障碍服务**直接在手机上运行 Agent，无需连接电脑 | 终端用户、演示、离线使用 |
| 💻 **ADB 模式（PC 端控制）** | 通过 ADB 从电脑端 Python 脚本控制手机，截图→模型理解→ADB 执行操作 | 开发调试、批量测试、远程控制 |

### 核心特性

- 🎯 **自然语言控制**：用语音或文字描述需求，Agent 自动完成任务
- 👁️ **视觉理解**：基于多模态视觉语言模型，深度理解屏幕 UI 内容和元素
- 🤖 **智能决策**：自动规划执行步骤，支持多步骤任务的循环执行
- 🎤 **语音输入**：内置 ASR 语音识别，支持语音转文字输入
- 🔒 **无需 Root**：基于 Android 无障碍服务 / ADB 调试，无需 Root 权限
- 💬 **对话式交互**：极简 Chat 风格界面，直观易用
- 🔄 **任务循环**：自动截图→模型分析→执行操作→检查完成，闭环执行
- 🛡️ **安全确认**：遇到登录、验证码等敏感场景自动请求用户协助
- 🌐 **远程控制**：支持 WiFi 远程 ADB 调试，无需 USB 连接

### 工作原理

```
用户输入自然语言指令
       │
       ▼
  ┌─────────────────┐
  │  回到桌面 / 打开应用  │
  └────────┬────────┘
           │
           ▼
  ┌─────────────────┐
  │    截取屏幕截图     │◄──────────┐
  └────────┬────────┘           │
           │                    │
           ▼                    │
  ┌─────────────────┐           │
  │ 发送截图+指令给 VLM │           │
  │  （视觉语言模型）    │           │
  └────────┬────────┘           │
           │                    │
           ▼                    │
  ┌─────────────────┐           │
  │ 解析模型返回的动作   │           │
  │ （点击/滑动/输入等）  │           │
  └────────┬────────┘           │
           │                    │
           ▼                    │
  ┌─────────────────┐    否     │
  │    执行操作       │──────────┘
  └────────┬────────┘
           │ 是（任务完成）
           ▼
  ┌─────────────────┐
  │   返回结果给用户    │
  └─────────────────┘
```

---

## 模型服务

G-Agent 使用名为 **cs-glm** 的多模态视觉语言模型进行屏幕理解和决策。你可以选择使用已部署的远程 API 服务，或自行本地部署模型。

### 选项 A：使用远程 API 服务（推荐，无需 GPU）

如果你已有可用的模型 API 服务，直接配置 API 地址和密钥即可使用。

**配置参数：**

| 参数 | 说明 | 示例 |
|------|------|------|
| `base-url` | 模型服务 API 地址 | `http://your-server:8000/v1` |
| `model` | 模型名称 | `cs-glm` |
| `apikey` | API 认证密钥 | `your-api-key` |

**使用示例：**

```bash
# ADB 模式
python main.py --base-url http://your-server:8000/v1 --model "cs-glm" --apikey "your-api-key" "打开美团搜索附近的火锅店"
```

对于 APK 模式，将 API 地址和模型名称填入应用设置即可。

### 选项 B：本地部署模型（需要 GPU）

如果希望在自己的服务器上部署模型：

**硬件要求：**

- NVIDIA GPU（建议 24GB+ 显存，如 RTX 3090/4090、A100）
- 约 20GB 磁盘空间用于存储模型

**使用 vLLM 部署：**

```bash
# 安装 vLLM
pip install vllm

# 启动模型服务
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

**使用 SGLang 部署：**

```bash
python3 -m sglang.launch_server \
    --model-path your-model-path \
    --served-model-name cs-glm \
    --context-length 25480 \
    --mm-enable-dp-encoder \
    --mm-process-config '{"image":{"max_pixels":5000000}}' \
    --port 8000
```

**Docker 部署：**

```bash
# vLLM Docker
docker pull vllm/vllm-openai:v0.12.0
# 进入容器后执行
pip install -U transformers --pre

# SGLang Docker
docker pull lmsysorg/sglang:v0.5.6.post1
# 进入容器后执行
pip install nvidia-cudnn-cu12==9.16.0.29
```

> 运行成功后，模型服务地址为 `http://localhost:8000/v1`。如果部署在远程服务器，使用该服务器的 IP 地址访问。

### 验证模型部署

```bash
cd g-agent/adb_agent

# 验证模型服务是否正常
python scripts/check_deployment_cn.py --base-url http://your-server:8000/v1 --model cs-glm
```

脚本将发送测试请求并展示模型的推理结果，你可以根据输出判断模型部署是否正常工作。

### ASR 语音识别服务（可选）

APK 的语音输入功能需要一个 ASR 服务。接口格式：

```bash
# 请求
curl -X POST http://localhost:8787/recognize \
  -F "file=@audio.wav" \
  -F "language=auto"

# 响应
{
    "success": true,
    "text": "打开淘宝搜索蓝牙耳机",
    "language": "auto"
}
```

---

## 📱 方式一：APK 模式（端侧执行）

APK 模式将 Agent 直接运行在 Android 手机上，通过无障碍服务实现 GUI 操作，**无需连接电脑**。

### 环境要求

- **Android 版本**：Android 8.0（API 26）及以上
- **开发环境**（仅编译需要）：Android Studio 2022.3+、JDK 17
- **模型服务**：需要可访问的 VLM 模型 API（云端或局域网）

### 安装步骤

#### 方式 A：直接安装 APK（推荐）

1. 从 [Releases](https://github.com/https-www-csaiagent-com/g-agent/releases) 下载最新 APK
2. 在手机上安装 APK（需允许安装未知来源应用）
3. 按照下方 [权限配置](#权限配置) 开启必要权限

#### 方式 B：从源码编译

```bash
# 克隆项目
git clone https://github.com/https-www-csaiagent-com/g-agent.git
cd g-agent/apk

# 使用 Gradle 编译（需要 Android SDK 环境）
# Linux/macOS:
./gradlew assembleDebug

# Windows:
gradlew.bat assembleDebug

# APK 输出路径
# app/build/outputs/apk/debug/app-debug.apk
```

也可以使用 Android Studio 打开 `apk/` 目录，直接 Build → Build APK(s) 编译。

### 权限配置

安装 APK 后需要开启以下权限：

#### 1. 开启无障碍服务（必需）

这是 Agent 执行点击、滑动等操作的核心能力。

```
设置 → 无障碍 → 已下载的服务 → 智能手机助手 → 开启
```

> ⚠️ 不同品牌手机路径可能略有不同，搜索"无障碍"即可找到。

#### 2. 授予截图权限

首次执行任务时，系统会弹出截图权限请求，点击"允许"即可。

- **Android 11+**：使用无障碍服务自带的截图能力，无需额外权限
- **Android 11 以下**：需要 MediaProjection 权限

#### 3. 录音权限（语音输入需要）

使用语音输入时，需要授予麦克风权限。

#### 4. 防止后台被杀

部分手机会在后台杀死服务，建议进行以下设置：

```
设置 → 应用管理 → 智能手机助手 → 允许后台运行 / 关闭省电优化
设置 → 电池 → 将本应用加入白名单
```

### 配置模型服务

APK 需要连接到 VLM 模型服务才能分析屏幕内容。首次启动会使用默认配置，你可以在应用内修改。

#### 修改默认配置

编辑 `apk/app/src/main/java/com/agent/mobileagent/util/PreferenceManager.kt`：

```kotlin
// 模型服务地址
private const val DEFAULT_VLLM_URL = "http://your-server:8002/v1"

// 模型名称
private const val DEFAULT_MODEL_NAME = "cs-glm"

// ASR 语音识别服务地址（可选）
private const val DEFAULT_ASR_URL = "http://your-server:8787/recognize"
```

### 使用方式

#### 文字输入

1. 在输入框输入任务描述（如"打开京东搜索感冒药"）
2. 点击"发送"按钮
3. 应用会自动最小化并开始在手机上执行任务
4. 任务完成后会通过通知提示

#### 语音输入

1. 点击输入框左侧的键盘图标，切换到语音模式
2. 按住"按住 说话"按钮录音
3. 松开后自动调用 ASR 识别，结果填入输入框
4. 确认后点击"发送"执行

#### 停止任务

任务执行过程中，点击右下角红色浮动按钮即可停止。

#### 示例任务

```
"打开京东搜索感冒药"
"打开淘宝搜索维生素C"
"打开美团点外卖"
"在小红书搜索美食攻略"
"打开微博看热搜"
```

### APK 技术栈

| 技术 | 说明 |
|------|------|
| Kotlin 1.9 | 开发语言 |
| Material Design 3 | UI 框架 |
| View Binding | 视图绑定 |
| OkHttp3 | 网络请求 |
| Kotlin Coroutines | 异步处理 |
| Gson | JSON 解析 |
| AccessibilityService | 无障碍服务（核心操作能力） |
| MediaProjection | 屏幕截图 |
| MediaRecorder | 语音录制 |

---

## 💻 方式二：ADB 模式（PC 端控制）

ADB 模式通过 PC 端 Python 脚本控制手机，适合开发调试和批量测试。系统通过截图→模型理解界面→输出操作坐标→ADB 执行操作的闭环实现自动化。

### Android 环境准备

#### 1. Python 环境

建议使用 Python 3.10 及以上版本。

#### 2. 安装 ADB 工具

ADB（Android Debug Bridge）是连接电脑和手机的桥梁。

**Windows 安装方法：**

1. 下载官方 [ADB 安装包](https://developer.android.com/tools/releases/platform-tools?hl=zh-cn)（platform-tools）
2. 解压到自定义目录（例如 `C:\platform-tools`）
3. 将该目录添加到系统 `PATH` 环境变量：
   ```
   此电脑 → 右键属性 → 高级系统设置 → 环境变量 → Path → 编辑 → 新建 → 输入解压路径
   ```
4. 打开新的 CMD/PowerShell 验证：
   ```bash
   adb version
   # 应输出类似: Android Debug Bridge version 1.0.41
   ```

**macOS 安装方法：**

```bash
# 使用 Homebrew 安装（推荐）
brew install android-platform-tools

# 或手动下载解压后配置环境变量
export PATH=${PATH}:~/Downloads/platform-tools
```

**Linux 安装方法：**

```bash
# Ubuntu/Debian
sudo apt install android-tools-adb

# 或手动下载
# https://developer.android.com/tools/releases/platform-tools
```

#### 3. 手机端配置

##### 开启开发者模式

```
设置 → 关于手机 → 版本号 → 连续快速点击 7~10 次
```

直到弹出提示"开发者模式已启用"（不同品牌路径可能略有不同）。

##### 开启 USB 调试

```
设置 → 开发者选项 → USB 调试 → 开启
```

> ⚠️ **重要**：部分机型（如小米、OPPO）还需要同时开启 **"USB 调试（安全设置）"**，否则可能无法执行点击操作。

**请务必仔细检查相关权限**

##### 连接设备验证

1. 确认 **USB 数据线具有数据传输功能**，而不是仅有充电功能
2. 手机上弹出"允许 USB 调试"提示时，点击**允许**
3. 在电脑终端执行：

```bash
adb devices

# 应输出：
# List of devices attached
# XXXXXXXX    device
```

如果显示 `unauthorized`，请在手机上点击允许授权；如果无任何输出，请检查数据线和 USB 调试设置。

部分机型在设置开发者选项以后，可能需要重启设备才能生效。

#### 4. 安装 ADB Keyboard（用于文本输入）

Android 设备需要安装 ADB Keyboard 才能正确输入中文文本：

1. 下载 [ADB Keyboard APK](https://github.com/senzhk/ADBKeyBoard/blob/master/ADBKeyboard.apk)
2. 安装到手机：
   ```bash
   adb install ADBKeyboard.apk
   ```
3. 在手机上启用：
   ```
   设置 → 输入法 / 键盘列表 → 启用 ADB Keyboard
   ```
   或使用命令：
   ```bash
   adb shell ime enable com.android.adbkeyboard/.AdbIME
   ```

### 安装依赖

```bash
cd g-agent/adb_agent

# 创建虚拟环境（推荐）
python -m venv venv

# 激活虚拟环境
# Windows:
venv\Scripts\activate
# Linux/macOS:
source venv/bin/activate

# 安装依赖
pip install -r requirements.txt
pip install -e .
```

### 使用 ADB 模式

#### 命令行

根据你部署的模型，设置 `--base-url` 和 `--model` 参数。设置 `--device-type` 指定设备类型（默认 `adb` 为安卓设备，`hdc` 为鸿蒙设备）。

```bash
# 交互模式（连续对话）
python main.py --base-url http://localhost:8000/v1 --model "cs-glm"

# 执行单个任务
python main.py --base-url http://localhost:8000/v1 --model "cs-glm" "打开美团搜索附近的火锅店"

# 使用 API Key 认证
python main.py --base-url http://your-api-server/v1 --model "cs-glm" --apikey "your-api-key" "打开京东搜索耳机"

# 使用英文 system prompt
python main.py --lang en --base-url http://localhost:8000/v1 --model "cs-glm" "Open Chrome browser"

# 查看支持的应用列表
python main.py --list-apps

# 鸿蒙设备
python main.py --device-type hdc --base-url http://localhost:8000/v1 --model "cs-glm" "打开美团搜索附近的火锅店"
```

#### Python API

```python
from phone_agent import PhoneAgent
from phone_agent.model import ModelConfig

# 配置模型
model_config = ModelConfig(
    base_url="http://localhost:8000/v1",
    model_name="cs-glm",
)

# 创建 Agent
agent = PhoneAgent(model_config=model_config)

# 执行任务
result = agent.run("打开淘宝搜索无线耳机")
print(result)
```

#### 自定义回调

处理敏感操作确认和人工接管：

```python
def my_confirmation(message: str) -> bool:
    """敏感操作确认回调"""
    return input(f"确认执行 {message}？(y/n): ").lower() == "y"

def my_takeover(message: str) -> None:
    """人工接管回调"""
    print(f"请手动完成: {message}")
    input("完成后按回车继续...")

agent = PhoneAgent(
    confirmation_callback=my_confirmation,
    takeover_callback=my_takeover,
)
```

### 远程调试（WiFi 连接）

无需 USB 数据线，通过 WiFi 连接手机：

确保手机和电脑在同一个 WiFi 中。

```bash
# 手机端：设置 → 开发者选项 → 无线调试 → 开启

# 电脑端连接（改成手机显示的 IP 地址和端口）
adb connect 192.168.1.100:5555

# 验证连接
adb devices
# 应显示: 192.168.1.100:5555    device

# 断开连接
adb disconnect 192.168.1.100:5555

# 指定设备执行任务
python main.py --device-id 192.168.1.100:5555 --base-url http://localhost:8000/v1 --model "cs-glm" "打开微信"
```

### 环境变量

| 变量 | 描述 | 默认值 |
|------|------|--------|
| `PHONE_AGENT_BASE_URL` | 模型 API 地址 | `http://localhost:8000/v1` |
| `PHONE_AGENT_MODEL` | 模型名称 | `cs-glm` |
| `PHONE_AGENT_API_KEY` | 模型认证 API Key | `EMPTY` |
| `PHONE_AGENT_MAX_STEPS` | 每个任务最大步数 | `100` |
| `PHONE_AGENT_DEVICE_ID` | ADB/HDC 设备 ID | (自动检测) |
| `PHONE_AGENT_DEVICE_TYPE` | 设备类型 (`adb` 或 `hdc`) | `adb` |
| `PHONE_AGENT_LANG` | 语言 (`cn` 或 `en`) | `cn` |

### 配置

#### 模型配置

```python
from phone_agent.model import ModelConfig

config = ModelConfig(
    base_url="http://localhost:8000/v1",
    api_key="EMPTY",           # API 密钥（如需要）
    model_name="cs-glm",      # 模型名称
    max_tokens=3000,           # 最大输出 token 数
    temperature=0.1,           # 采样温度
    frequency_penalty=0.2,     # 频率惩罚
)
```

#### Agent 配置

```python
from phone_agent.agent import AgentConfig

config = AgentConfig(
    max_steps=100,             # 每个任务最大步数
    device_id=None,            # ADB 设备 ID（None 为自动检测）
    lang="cn",                 # 语言选择：cn（中文）或 en（英文）
    verbose=True,              # 打印调试信息（包括思考过程和执行动作）
)
```

#### 自定义 System Prompt

系统提供中英文两套 prompt，通过 `--lang` 参数切换：

- `--lang cn` — 中文 prompt（默认），配置文件：`phone_agent/config/prompts_zh.py`
- `--lang en` — 英文 prompt，配置文件：`phone_agent/config/prompts_en.py`

可以直接修改对应的配置文件来增强模型在特定领域的能力。

#### Verbose 模式输出

当 `verbose=True` 时，Agent 会在每一步输出详细信息：

```
==================================================
💭 思考过程:
--------------------------------------------------
当前在系统桌面，需要先启动小红书应用
--------------------------------------------------
🎯 执行动作:
{
  "_metadata": "do",
  "action": "Launch",
  "app": "小红书"
}
==================================================

... (执行动作后继续下一步)

🎉 ================================================
✅ 任务完成: 已成功搜索美食攻略
==================================================
```

---

## 可用操作

Agent 可以执行以下 GUI 操作：

| 操作 | 说明 | JSON 格式 |
|------|------|----------|
| `click` | 点击屏幕元素 | `{"action_type": "click", "box_2d": [[x1,y1,x2,y2]]}` |
| `swipe` | 滑动屏幕 | `{"action_type": "swipe", "start_point": [x,y], "end_point": [x,y]}` |
| `input_text` | 输入文字 | `{"action_type": "input_text", "text": "内容", "box_2d": [[x1,y1,x2,y2]]}` |
| `navigate_back` | 返回上一页 | `{"action_type": "navigate_back"}` |
| `open_app` / `Launch` | 打开应用 | `{"action_type": "open_app", "app_name": "京东"}` |
| `wait` / `Wait` | 等待页面加载 | `{"action_type": "wait"}` |
| `status` | 报告任务状态 | `{"action_type": "status", "goal_status": "complete"}` |
| `call_user` / `Take_over` | 请求用户协助 | `{"action_type": "call_user", "text": "需要登录"}` |
| `Long Press` | 长按 | ADB 模式支持 |
| `Double Tap` | 双击 | ADB 模式支持 |
| `Home` | 返回桌面 | ADB 模式支持 |

> **坐标说明**：所有坐标均为归一化值（0-999），Agent 会自动将其转换为实际屏幕像素坐标。

## 支持的应用

G-Agent 支持 50+ 款主流应用：

| 分类 | 应用 |
|------|------|
| 电商购物 | 京东、淘宝、拼多多、闲鱼 |
| 美食外卖 | 美团、饿了么 |
| 社交通讯 | 微信、QQ、微博、钉钉 |
| 视频娱乐 | 抖音、快手、哔哩哔哩、爱奇艺、腾讯视频 |
| 内容社区 | 小红书、知乎、百度 |
| 生活服务 | 高德地图、百度地图、支付宝、云闪付 |
| 阅读资讯 | 掌阅、番茄小说、东方财富 |
| 音乐音频 | 网易云音乐、QQ音乐 |

运行 `python main.py --list-apps` 查看完整列表。

> 💡 如需添加更多应用：
> - APK 模式：在 `AutoAgentAccessibilityService.kt` 的 `resolveAppPackage()` 中添加映射
> - ADB 模式：在 `phone_agent/config/apps.py` 中添加应用包名

---

## 完整项目结构

```
g-agent/
├── README.md                           # 📄 项目主文档（本文件）
│
├── apk/                                # 📱 APK 模式 - Android 端侧应用
│   ├── app/
│   │   ├── build.gradle                #   构建配置
│   │   └── src/main/
│   │       ├── AndroidManifest.xml     #   应用清单
│   │       ├── java/com/agent/mobileagent/
│   │       │   ├── MainActivity.kt     #   主界面
│   │       │   ├── model/              #   数据模型
│   │       │   │   └── ActionModels.kt #     动作、请求、响应模型
│   │       │   ├── service/            #   后台服务
│   │       │   │   ├── AgentService.kt #     Agent 任务循环
│   │       │   │   ├── AutoAgentAccessibilityService.kt  # 无障碍服务
│   │       │   │   └── ScreenCaptureService.kt           # 截图服务
│   │       │   ├── network/            #   网络层
│   │       │   │   ├── AgentApiClient.kt #   VLM API 客户端
│   │       │   │   └── AsrApiClient.kt   #   ASR 客户端
│   │       │   └── util/               #   工具类
│   │       │       ├── LogUtils.kt     #     日志
│   │       │       └── PreferenceManager.kt  # 配置管理
│   │       └── res/                    #   资源文件
│   ├── build.gradle                    #   根构建文件
│   ├── settings.gradle                 #   项目设置
│   └── gradle/                         #   Gradle Wrapper
│
└── adb_agent/                          # 💻 ADB 模式 - PC 端控制
    ├── main.py                         #   命令行入口
    ├── phone_agent/                    #   Agent 核心 Python 包
    │   ├── __init__.py                 #     包导出
    │   ├── agent.py                    #     PhoneAgent 主类
    │   ├── adb/                        #     ADB 工具
    │   │   ├── connection.py           #       远程/本地连接管理
    │   │   ├── screenshot.py           #       屏幕截图
    │   │   ├── input.py                #       文本输入（ADB Keyboard）
    │   │   └── device.py               #       设备控制（点击、滑动等）
    │   ├── actions/                    #     操作处理
    │   │   └── handler.py              #       操作执行器
    │   ├── config/                     #     配置
    │   │   ├── apps.py                 #       支持的应用映射
    │   │   ├── prompts_zh.py           #       中文系统提示词
    │   │   └── prompts_en.py           #       英文系统提示词
    │   └── model/                      #     AI 模型客户端
    │       └── client.py               #       OpenAI 兼容客户端
    ├── examples/                       #   使用示例
    │   └── basic_usage.py              #     基础任务执行
    ├── scripts/                        #   工具脚本
    │   └── check_deployment_cn.py      #     模型部署检查
    ├── requirements.txt                #   Python 依赖
    └── setup.py                        #   安装配置
```

---

## 开发指南

### 修改 Prompt 模板

**APK 模式**：编辑 `AgentApiClient.kt` 的 `buildPrompt()` 方法：

```kotlin
// apk/app/src/main/java/com/agent/mobileagent/network/AgentApiClient.kt
private fun buildPrompt(query: String, historyInfo: String): String {
    return """
You are a GUI Agent...
// 在这里修改 Prompt 内容
""".trimIndent()
}
```

**ADB 模式**：修改 `phone_agent/config/prompts_zh.py` 或 `prompts_en.py`。

### 添加新的应用映射

**APK 模式**：在 `AutoAgentAccessibilityService.kt` 的 `resolveAppPackage()` 中添加：

```kotlin
val appPackageMap = mapOf(
    "应用名称" to "com.example.package",
    // ...
)
```

**ADB 模式**：在 `phone_agent/config/apps.py` 中添加。

### 添加新的操作类型

1. 在 `ActionModels.kt` 的 `ActionType` 中添加新类型
2. 在 `AgentService.kt` 的 `executeAction()` 中添加处理逻辑
3. 在 Prompt 中描述新操作的调用格式

### 二次开发

```bash
# 安装开发依赖
pip install -e ".[dev]"

# 运行测试
pytest tests/
```

---

## ADB 常用命令速查

```bash
# === 设备管理 ===
adb devices                              # 查看已连接设备
adb kill-server && adb start-server      # 重启 ADB 服务
adb connect 192.168.1.100:5555           # WiFi 连接设备
adb disconnect 192.168.1.100:5555        # 断开设备

# === 应用管理 ===
adb install app-debug.apk               # 安装 APK
adb uninstall com.agent.mobileagent      # 卸载应用
adb shell pm list packages               # 列出所有已安装包

# === 调试 ===
adb logcat -s MobileAgent                # 查看 G-Agent 日志
adb logcat -s MobileAgent:I              # 只看 INFO 及以上级别
adb shell screencap /sdcard/screen.png   # 截图
adb pull /sdcard/screen.png .            # 拉取截图到电脑

# === 输入操作 ===
adb shell input tap 500 1000             # 点击屏幕坐标
adb shell input swipe 500 1500 500 500   # 向上滑动
adb shell input keyevent 4              # 返回键
adb shell input keyevent 3              # Home 键

# === ADB Keyboard ===
adb shell ime set com.android.adbkeyboard/.AdbIME     # 切换到 ADB Keyboard
adb shell am broadcast -a ADB_INPUT_TEXT --es msg "你好"  # 输入中文
```

---

## 常见问题

### 通用问题

#### Q: 模型调用失败？

1. 检查网络是否能访问模型服务地址
2. 确认模型服务正在运行
3. 检查模型名称和 API 地址是否正确
4. 查看日志中的错误信息

#### Q: 需要 GPU 吗？

- **使用远程 API**：不需要 GPU，只需网络连接
- **本地部署模型**：需要 NVIDIA GPU（建议 24GB+ 显存）

### APK 模式问题

#### Q: 无障碍服务开启后自动关闭？

部分手机有省电优化，需要：

1. `设置 → 应用管理 → 智能手机助手 → 允许后台运行`
2. `设置 → 电池 → 将本应用加入白名单`
3. 关闭应用的"自动优化"或"省电模式"

#### Q: 截图失败？

1. **Android 11+**：使用无障碍服务自带截图，确保服务已开启
2. **Android 11 以下**：需要 MediaProjection 权限，首次使用时会弹出授权
3. 部分敏感页面（支付、银行等）可能无法截图，这是系统安全限制

#### Q: 文本输入失败？

APK 使用剪贴板粘贴方式输入文本。如果输入失败：

1. 确保输入框已获取焦点（Agent 会先点击输入框）
2. 部分应用可能禁用了粘贴功能

#### Q: 语音识别失败？

1. 确保已授予录音权限
2. 检查 ASR 服务是否正常运行
3. 录音时间不能太短（至少 1 秒）

### ADB 模式问题

#### Q: 设备未找到？

```bash
# 重启 ADB 服务
adb kill-server
adb start-server
adb devices
```

如果仍然无法识别：

1. 检查 USB 调试是否已开启
2. 确认数据线支持数据传输（部分线仅支持充电）
3. 手机上弹出的授权框是否已点击"允许"
4. 部分机型需要重启后生效
5. 尝试更换 USB 接口或数据线

#### Q: 能打开应用，但无法点击？

部分机型需要同时开启两个调试选项：

- **USB 调试**
- **USB 调试（安全设置）**

在 `设置 → 开发者选项` 中检查两个选项是否都已启用。

#### Q: 文本输入不工作（ADB 模式）？

1. 确保设备已安装 ADB Keyboard
2. 在 `设置 > 系统 > 语言和输入法 > 虚拟键盘` 中启用
3. Agent 会在需要输入时自动切换到 ADB Keyboard

#### Q: 截图失败（黑屏）？

这通常意味着应用正在显示敏感页面（支付、密码、银行类应用）。Agent 会自动检测并请求人工接管。

#### Q: Windows 出现 `UnicodeEncodeError gbk code`？

在运行命令前加上环境变量：

```bash
set PYTHONIOENCODING=utf-8
python main.py ...
```

#### Q: 中文输入变成乱码？

确保已安装并启用 ADB Keyboard，详见 [安装 ADB Keyboard](#4-安装-adb-keyboard用于文本输入) 章节。

#### Q: 交互模式报 `EOF when reading a line`？

使用非交互模式直接指定任务，或者切换到 TTY 模式的终端应用。

---

## 注意事项

1. **隐私安全**
   - 应用需要无障碍服务权限，请确保从可信来源获取
   - 所有操作在本地/指定服务器执行，不会收集用户隐私数据
   - 截图仅用于模型分析，不会存储或上传

2. **使用限制**
   - 本项目仅供学习研究使用
   - 请遵守相关应用的服务条款
   - 不得用于非法用途

3. **兼容性**
   - 不同 Android 版本和设备可能存在兼容性差异
   - 部分应用可能限制无障碍服务访问
   - 建议在 Android 10+ 设备上使用以获得最佳体验

---

## 致谢

- [Material Design](https://material.io/) — UI 设计规范
- [ADB Keyboard](https://github.com/senzhk/ADBKeyBoard) — ADB 中文输入工具
- [vLLM](https://github.com/vllm-project/vllm) — 高性能模型推理引擎
- [SGLang](https://github.com/sgl-project/sglang) — 模型推理框架

## 许可证

本项目采用 [MIT License](LICENSE) 开源协议。

---

<div align="center">

如果这个项目对你有帮助，请给个 ⭐ 支持一下！

</div>
