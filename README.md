# G-Agent —— 移动端 GUI 智能操作框架

<div align="center">

**基于多模态视觉语言模型的移动端应用自主操作框架**

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-purple.svg)](https://kotlinlang.org)

</div>

## 项目简介

G-Agent 是一个基于多模态视觉语言模型（VLM）的移动端 GUI 智能操作框架。它以原生 Android APK 的形式运行在手机上，能够理解用户的自然语言指令，通过视觉模型分析屏幕内容，并利用 Android 无障碍服务自动规划并执行 GUI 操作，帮助用户完成复杂的手机任务。

用户只需用自然语言描述需求，如"打开美团搜索附近的火锅店"，G-Agent 即可自动解析意图、理解当前界面、规划下一步动作并完成整个流程。系统还内置敏感操作确认机制，并支持在登录或验证码场景下进行人工接管。

> ⚠️ 本项目仅供研究和学习使用。严禁用于非法获取信息、干扰系统或任何违法活动。

### 核心特性

- 🎯 **自然语言控制**：用语音或文字描述需求，Agent 自动完成任务
- 👁️ **视觉理解**：基于多模态视觉语言模型，深度理解屏幕 UI 内容和元素
- 🤖 **智能决策**：自动规划执行步骤，支持多步骤任务的循环执行
- 🎤 **语音输入**：内置 ASR 语音识别，支持语音转文字输入
- 🔒 **无需 Root**：基于 Android 无障碍服务，无需 Root 权限
- 💬 **对话式交互**：极简 Chat 风格界面，直观易用
- 🔄 **任务循环**：自动截图→模型分析→执行操作→检查完成，闭环执行
- 🛡️ **安全确认**：遇到登录、验证码等敏感场景自动请求用户协助
- 📊 **双模式支持**：通用任务模式 + 商品比价模式，覆盖日常与电商场景

---

## cs-glm 模型

G-Agent 使用自研的 **cs-glm** 多模态视觉语言模型作为核心决策引擎。

cs-glm 在开源视觉语言模型的基础上，针对**移动端 GUI 操作场景**进行了大规模专项训练与优化：

- **海量应用适配**：在数十款主流 Android 应用（特别是电商购物类 APP）上进行了大量操作数据的训练，覆盖电商、外卖、社交、视频、地图等高频使用场景，使模型能够准确理解各类应用的 UI 布局和交互逻辑
- **复杂任务适配**：针对多步骤、跨应用、需要上下文理解的复杂任务链进行了专项训练，提升了在长流程任务中的规划和执行能力
- **坐标精度优化**：针对移动端屏幕的点击、滑动等操作进行了坐标归一化训练，显著提升了操作精度

### 训练数据构建

cs-glm 的训练数据由三大类数据构成，分别解决 GUI 操作中"定位元素"、"规划任务"和"理解状态变化"三个核心问题。

#### 一、Grounding 定位数据

Grounding 数据解决**"精准找到 UI 元素"**的问题。模型需根据自然语言指令（如"点击左上角返回键"、"选中价格高于 500 的商品"），在界面中定位对应的 UI 元素（按钮、输入框、图标等）。

实现思路为**多来源数据融合 + 细粒度拆分**，构建覆盖"功能、外观、布局"多维度的定位数据集。

**1. UI Element Grounding（UI 元素定位数据）**

针对不同平台的 UI 元素特性差异和不同指令风格的定位需求，从三个来源生成数据：

| 数据来源 | 方法 | 说明 |
|---------|------|------|
| 开源数据集复用 | 采用公开 GUI 定位数据集 | 已标注"界面截图 + 元素坐标 + 指令"的对应关系，覆盖移动端、PC 端常见界面场景 |
| Accessibility 树合成 | 解析系统级 a11y 树 | 提取功能描述（如"button:搜索"）生成功能导向指令，结合多模态模型生成外观布局导向指令（如"点击右上角红色放大镜图标"），快速生成大规模、高保真定位数据 |
| PC 端密集定位数据 | 爬取 + 分割 + 指令生成 | 以热门软件名称为关键词爬取 PC 界面截图，用 SAM 模型分割为细粒度子区域，再用多模态大模型生成定位指令，覆盖 PC 端复杂界面场景 |

其中，基于 Accessibility 树的合成流程如下：

```
Android/Windows 系统 a11y 树
       │
       ├── 功能标签（如 "button:搜索"）
       │        → 生成功能导向指令："点击搜索按钮"
       │
       └── 元素外观/布局（颜色、位置等）
                → 多模态模型描述 → 生成外观布局导向指令：
                  "点击右上角红色放大镜图标"
```

PC 端密集定位数据的构建流程：

```
Step 1: 爬取  → 以 "Excel"、"Chrome" 等关键词从搜索引擎爬取大量 PC 界面截图
Step 2: 分割  → 用 SAM (Segment Anything) 模型将截图分割为细粒度子区域
Step 3: 生成  → 用多模态大模型为每个分割区域生成定位指令
```

**2. Fine-grained Words/Chars Grounding（细粒度文字定位数据）**

解决在文档类界面（如 PDF、表格、表单）中**精准定位文本片段**的问题。

```
Step 1: 数据采集  → 收集大量文档界面截图（合同、报表、网页文本等）
Step 2: OCR 提取  → 用 OCR 工具提取文本内容及每个文字/字符的坐标位置
Step 3: 指令生成  → 基于文本与坐标生成细粒度定位指令
                    例："选中'甲方：阿里巴巴集团'中的'阿里巴巴集团'"
                    例："点击数字'100'所在位置"
```

**3. 数据质量优化**

对生成的原始定位数据进行过滤与优化：

- **坐标校验**：使用 [OmniParser-V2](https://huggingface.co/microsoft/OmniParser-v2.0)（UI 元素检测模型）检测界面中的 UI 元素，计算原始标注坐标与检测结果的 IoU（交并比），剔除 IoU < 0.5 的低质量标注
- **指令自然化**：使用大语言模型将原始指令（如"点击坐标（100,200）的按钮"）重写为自然语言指令（如"点击左上角的搜索按钮"），使其更贴近真实用户的交互习惯

#### 二、Task Planning 任务规划数据

Task Planning 数据解决**"拆解长周期复杂任务"**的问题。复杂 GUI 任务（如"查航班→订酒店→填行程单"）需要模型具备步骤拆解、优先级排序、跨 APP 协同的规划能力。

**1. 从历史成功轨迹中蒸馏流程知识**

基于真实任务执行的历史轨迹（如"打开航旅 APP → 输入出发地/目的地 → 选择日期 → 查看航班列表"），提取规划数据：

```
Step 1: 轨迹解析  → 对每一步生成"页面状态描述"和"动作意图"
                    例 - 页面描述："当前界面为航班搜索结果页，显示10个航班"
                    例 - 动作意图："筛选'经济舱'航班"

Step 2: 手册生成  → 用 LLM 将页面描述 + 动作意图整合为"任务执行手册"
                    例："订机票流程：
                         1. 打开航旅 APP
                         2. 输入出发地、目的地
                         3. 选择出行日期
                         4. 筛选舱位类型
                         5. 点击预订"

Step 3: 质量验证  → 将手册输入 GUI-Owl 测试执行成功率，仅保留能提升
                    任务完成率的手册作为规划数据
```

**2. 从 LLM 中蒸馏泛化规划能力**

历史轨迹仅覆盖已执行过的任务，为提升对"未见过的任务"的规划泛化性，从大语言模型中进行蒸馏：

```
Step 1: 任务生成  → 收集主流 APP 列表（如美团、12306、京东等），
                    由人工或 LLM 生成复杂多约束任务
                    例："用12306订2024年10月1日北京到上海的高铁票，
                         要求二等座、靠窗位置，出发时间在上午8点-10点之间"

Step 2: 规划生成  → 将任务输入大语言模型，输出详细执行步骤
                    例："1. 打开12306 APP
                         2. 点击'车票预订'
                         3. 输入出发地'北京'、目的地'上海'
                         4. 选择日期'2024-10-01'
                         5. 设置出发时间'08:00-10:00'
                         6. 筛选'二等座'
                         7. 选择靠窗座位"

Step 3: 数据清洗  → 由人工或 LLM 验证步骤合理性，剔除不符合
                    APP 实际逻辑的规划
```

#### 三、Action Semantics 动作语义数据

Action Semantics 数据解决**"理解动作与状态变化关联"**的问题，是三类数据中最为关键的一类。模型不仅要"会执行动作"，还需理解"动作会导致什么界面变化"（如"点击'提交'按钮后，应跳转到支付页面"），否则无法判断动作是否有效、是否需要修正。

**1. 动作预测数据（从"状态变化"反推"动作"）**

基于离线收集的轨迹，提取 **（Sₜ, aₜ, Sₜ₊₁）** 三元组——即"动作前截图、执行动作、动作后截图"，让模型学习：给定 Sₜ 和 Sₜ₊₁，预测中间执行的动作类型与参数。

```
示例：
  Sₜ  = 搜索框空白的界面截图
  Sₜ₊₁ = 搜索框已输入"酒店"的界面截图
  aₜ  = type(text="酒店")    ← 模型需要预测出这个动作
```

这类数据可直接从历史轨迹中提取，无需额外标注，规模大且真实性高。

**2. 动作描述数据（用自然语言解释"动作与变化"）**

为让模型理解动作的语义含义（而非仅学习坐标映射），通过多模态模型生成动作描述文本：

```
Step 1: 动作结果描述  → 输入 Sₜ + aₜ 到多模态模型，生成动作结果描述
                        例："在搜索框中输入'北京到上海机票'，
                             界面显示输入框内容更新，下方出现相关搜索建议"

Step 2: 状态变化验证  → 输入 Sₜ + Sₜ₊₁ 到同一模型，分析界面变化
                        例："搜索框从空白变为填充'北京到上海机票'，
                             下方新增5条搜索建议"

Step 3: 一致性校验    → 对比两者语义一致性，通过多轮投票保留高分描述
                        最终形成"动作 + 状态变化 + 自然语言描述"的配对数据
```

### 训练流程

cs-glm 采用**三阶段训练流程**，逐步提升模型在多样化 GUI 任务上的性能：

#### 阶段一：持续预训练（Continual Pre-training）

使用上述收集的完整数据集（不包括反思调优数据），以恒定学习率进行持续预训练。这一基础阶段使模型掌握自动化 GUI 交互所需的全部核心知识，包括**感知、定位和动作轨迹**，确保对各类 GUI 元素与交互模式的全面覆盖。

#### 阶段二：退火微调（Annealing Fine-tuning）

从感知、定位、动作轨迹及反思调优数据中**筛选出高质量子集**，用于退火训练。该过程逐步调整模型的学习动态，促使模型在真实 GUI 交互场景中形成更优的决策策略。

#### 阶段三：DPO 优化（Direct Preference Optimization）

利用在线引导（Online Bootstrapping）过程中标注的**反思配对数据（Reflective Pairs）**进行 DPO 训练。在此阶段，模型通过**强化最优动作、抑制次优行为**来优化决策能力，显著提升在真实 GUI 环境中做出精准上下文感知决策的能力。

```
训练流程总览：

  ┌────────────────────────────────────────────────────────────┐
  │                                                            │
  │  阶段一：持续预训练                                          │
  │  ┌──────────────────────────────────────────────────────┐  │
  │  │ 全量数据（Grounding + Planning + Action Semantics）  │  │
  │  │ 恒定学习率 · 掌握感知/定位/动作轨迹核心知识             │  │
  │  └──────────────────────────┬───────────────────────────┘  │
  │                             │                              │
  │                             ▼                              │
  │  阶段二：退火微调                                           │
  │  ┌──────────────────────────────────────────────────────┐  │
  │  │ 高质量数据子集 + 反思调优数据                           │  │
  │  │ 逐步调整学习动态 · 优化决策策略                         │  │
  │  └──────────────────────────┬───────────────────────────┘  │
  │                             │                              │
  │                             ▼                              │
  │  阶段三：DPO 优化                                          │
  │  ┌──────────────────────────────────────────────────────┐  │
  │  │ 反思配对数据（Reflective Pairs）                      │  │
  │  │ 强化最优动作 · 抑制次优行为 · 精准上下文感知决策         │  │
  │  └──────────────────────────────────────────────────────┘  │
  │                                                            │
  └────────────────────────────────────────────────────────────┘
```

---

## 使用模式

G-Agent 支持 **两种使用模式**，分别面向不同的使用场景：

### 🔹 模式一：通用任务模式

适用于各类通用 GUI 操作场景。用户输入自然语言指令，Agent 自动在手机上完成操作。

**示例任务：**

```
"打开京东搜索感冒药"
"打开淘宝搜索维生素C"
"打开美团点外卖"
"在小红书搜索美食攻略"
"打开微博看热搜"
```

**使用方式：** 在输入框中输入任意任务描述，点击"发送"即可。

### 🔹 模式二：商品比价模式

专为电商比价场景设计。用户只需输入**商品名称**，Agent 会自动在多个电商平台（京东、淘宝、拼多多等）上搜索该商品，采集价格、销量、评价等信息，最终生成一份**跨平台比价报告**。

**使用方式：**

1. 切换到"比价模式"
2. 输入商品名称（如"蓝牙耳机"、"维生素C"）
3. Agent 自动在各电商平台搜索、采集数据
4. 最终输出比价报告，包含各平台价格对比、推荐等信息

**示例：**

```
输入: "蓝牙耳机"

→ Agent 自动打开京东搜索蓝牙耳机，记录价格
→ Agent 自动打开淘宝搜索蓝牙耳机，记录价格
→ Agent 自动打开拼多多搜索蓝牙耳机，记录价格
→ 生成跨平台比价报告
```

---

## 工作原理

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
  │  （cs-glm 模型）   │           │
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

### 系统架构

```
┌──────────────────────────────────────────┐
│            MainActivity                   │
│     （用户界面 · 输入控制 · 语音录制）        │
│     （通用模式 / 比价模式 切换）              │
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
    ├── 2. 调用 cs-glm 模型 (AgentApiClient)
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

---

## 快速开始

### 环境要求

- **Android 版本**：Android 8.0（API 26）及以上
- **模型服务**：需要可访问的 cs-glm 模型 API（云端或局域网）
- **开发环境**（仅源码编译需要）：Android Studio 2022.3+、JDK 17

### 安装步骤

#### 方式 A：直接安装 APK（推荐）

1. 从 [Releases](https://github.com/https-www-csaiagent-com/g-agent/releases) 下载最新 APK
2. 在手机上安装 APK（需允许安装未知来源应用）
3. 按照下方 [权限配置](#权限配置) 开启必要权限

也可以通过 ADB 命令安装：

```bash
adb install g-agent.apk
```

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

编译完成后通过 ADB 安装到手机：

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

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

---

## 模型服务配置

### 选项 A：使用远程 API 服务（推荐，无需 GPU）

如果你已有可用的 cs-glm 模型 API 服务，直接在 APK 应用内配置 API 地址和模型名称即可使用。

**配置参数：**

| 参数 | 说明 | 示例 |
|------|------|------|
| 服务器地址 | 模型服务 API 地址 | `http://your-server:8000/v1` |
| 模型名称 | 模型名称 | `cs-glm` |

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

### 修改 APK 默认配置

编辑 `apk/app/src/main/java/com/agent/mobileagent/util/PreferenceManager.kt`：

```kotlin
// 模型服务地址
private const val DEFAULT_VLLM_URL = "http://your-server:8002/v1"

// 模型名称
private const val DEFAULT_MODEL_NAME = "cs-glm"

// ASR 语音识别服务地址（可选）
private const val DEFAULT_ASR_URL = "http://your-server:8787/recognize"
```

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

## 支持的操作

Agent 可以执行以下 GUI 操作：

| 操作类型 | 说明 | JSON 格式 |
|---------|------|----------|
| `click` | 点击屏幕元素 | `{"action_type": "click", "box_2d": [[x1,y1,x2,y2]]}` |
| `swipe` | 滑动屏幕 | `{"action_type": "swipe", "start_point": [x,y], "end_point": [x,y]}` |
| `input_text` | 输入文字 | `{"action_type": "input_text", "text": "内容", "box_2d": [[x1,y1,x2,y2]]}` |
| `navigate_back` | 返回上一页 | `{"action_type": "navigate_back"}` |
| `open_app` | 打开应用 | `{"action_type": "open_app", "app_name": "京东"}` |
| `wait` | 等待页面加载 | `{"action_type": "wait"}` |
| `status` | 报告任务状态 | `{"action_type": "status", "goal_status": "complete"}` |
| `call_user` | 请求用户协助 | `{"action_type": "call_user", "text": "需要登录"}` |

> **坐标说明**：所有坐标均为归一化值（0-999），Agent 会自动将其转换为实际屏幕像素坐标。

---

## 支持的应用

APK 内置了以下常用应用的包名映射，可通过 `open_app` 直接启动：

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

> 💡 如需添加更多应用，可在 `AutoAgentAccessibilityService.kt` 的 `resolveAppPackage()` 方法中添加映射。

---

## 项目结构

```
g-agent/
├── README.md                               # 📄 项目文档（本文件）
└── apk/                                    # 📱 Android 应用
    ├── app/
    │   ├── build.gradle                    #   构建配置
    │   └── src/main/
    │       ├── AndroidManifest.xml         #   应用清单（权限、服务声明）
    │       ├── java/com/agent/mobileagent/
    │       │   ├── MainActivity.kt         #   主界面（输入、语音、模式切换）
    │       │   ├── model/
    │       │   │   └── ActionModels.kt     #   数据模型（动作、请求、响应、状态）
    │       │   ├── service/
    │       │   │   ├── AgentService.kt     #   Agent 后台服务（任务循环调度）
    │       │   │   ├── AutoAgentAccessibilityService.kt  # 无障碍服务（操作执行）
    │       │   │   └── ScreenCaptureService.kt           # 截图权限服务
    │       │   ├── network/
    │       │   │   ├── AgentApiClient.kt   #   VLM API 客户端（Prompt 构建、响应解析）
    │       │   │   └── AsrApiClient.kt     #   ASR 语音识别客户端
    │       │   └── util/
    │       │       ├── LogUtils.kt         #   日志工具
    │       │       └── PreferenceManager.kt#   配置管理（服务器地址、模型名称）
    │       └── res/
    │           ├── layout/                 #   界面布局
    │           ├── values/                 #   字符串、颜色、主题
    │           └── xml/                    #   无障碍服务配置
    ├── build.gradle                        #   项目根构建文件
    ├── settings.gradle                     #   项目设置
    ├── gradle.properties                   #   Gradle 属性
    └── gradle/wrapper/                     #   Gradle Wrapper
```

---

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

---

## 开发指南

### 修改 Prompt 模板

编辑 `AgentApiClient.kt` 的 `buildPrompt()` 方法：

```kotlin
// apk/app/src/main/java/com/agent/mobileagent/network/AgentApiClient.kt
private fun buildPrompt(query: String, historyInfo: String): String {
    return """
You are a GUI Agent...
// 在这里修改 Prompt 内容
""".trimIndent()
}
```

### 添加新的应用映射

在 `AutoAgentAccessibilityService.kt` 的 `resolveAppPackage()` 中添加：

```kotlin
val appPackageMap = mapOf(
    "应用名称" to "com.example.package",
    // ...
)
```

### 添加新的操作类型

1. 在 `ActionModels.kt` 的 `ActionType` 中添加新类型
2. 在 `AgentService.kt` 的 `executeAction()` 中添加处理逻辑
3. 在 `AgentApiClient.kt` 的 Prompt 中描述新操作

### 编译构建

```bash
# 命令行编译
./gradlew assembleDebug      # Debug 版本
./gradlew assembleRelease    # Release 版本

# 输出路径
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

---

## ADB 安装与调试

ADB（Android Debug Bridge）是 Android 官方提供的调试工具，可用于安装 APK、查看日志、远程调试等。

### 安装 ADB 工具

**Windows：**

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

**macOS：**

```bash
# 使用 Homebrew 安装（推荐）
brew install android-platform-tools

# 或手动下载解压后配置环境变量
export PATH=${PATH}:~/Downloads/platform-tools
```

**Linux：**

```bash
# Ubuntu/Debian
sudo apt install android-tools-adb

# 或手动下载
# https://developer.android.com/tools/releases/platform-tools
```

### 手机端开启 USB 调试

#### 开启开发者模式

```
设置 → 关于手机 → 版本号 → 连续快速点击 7~10 次
```

直到弹出提示"开发者模式已启用"（不同品牌路径可能略有不同）。

#### 开启 USB 调试

```
设置 → 开发者选项 → USB 调试 → 开启
```

> ⚠️ **重要**：部分机型（如小米、OPPO）还需要同时开启 **"USB 调试（安全设置）"**，否则可能无法执行点击操作。

#### 连接设备验证

1. 确认 **USB 数据线具有数据传输功能**，而不是仅有充电功能
2. 手机上弹出"允许 USB 调试"提示时，点击**允许**
3. 在电脑终端执行：

```bash
adb devices

# 应输出：
# List of devices attached
# XXXXXXXX    device
```

如果显示 `unauthorized`，请在手机上点击允许授权；如果无任何输出，请检查数据线和 USB 调试设置。部分机型在设置开发者选项以后，可能需要重启设备才能生效。

### WiFi 远程调试

无需 USB 数据线，通过 WiFi 连接手机：

```bash
# 手机端：设置 → 开发者选项 → 无线调试 → 开启
# 确保手机和电脑在同一 WiFi 网络

# 电脑端连接（改成手机显示的 IP 地址和端口）
adb connect 192.168.1.100:5555

# 验证连接
adb devices
# 应显示: 192.168.1.100:5555    device

# 断开连接
adb disconnect 192.168.1.100:5555
```

### ADB 常用命令速查

```bash
# === 应用管理 ===
adb install g-agent.apk                 # 安装 APK
adb uninstall com.agent.mobileagent      # 卸载应用

# === 调试日志 ===
adb logcat -s MobileAgent                # 查看 G-Agent 日志
adb logcat -s MobileAgent:I              # 只看 INFO 及以上级别

# === 屏幕操作 ===
adb shell screencap /sdcard/screen.png   # 截图
adb pull /sdcard/screen.png .            # 拉取截图到电脑

# === 设备管理 ===
adb devices                              # 查看已连接设备
adb kill-server && adb start-server      # 重启 ADB 服务
```

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

## 参考与致谢

- [Open-AutoGLM](https://github.com/zai-org/Open-AutoGLM) — 本项目在架构设计与交互流程上参考了 Open-AutoGLM 开源手机 Agent 框架
- [Material Design](https://material.io/) — UI 设计规范
- [vLLM](https://github.com/vllm-project/vllm) — 高性能模型推理引擎
- [SGLang](https://github.com/sgl-project/sglang) — 模型推理框架

## 许可证

本项目采用 [MIT License](LICENSE) 开源协议。

---

<div align="center">

如果这个项目对你有帮助，请给个 ⭐ 支持一下！

</div>
