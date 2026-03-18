# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在本项目中工作时提供指导。

---

## 项目愿景

JRemote Controller 不仅仅是一个遥控器，而是一个**一站式调试工具**。

- **核心定位**：局域网作为主要连接方式，ESP32 作为接收机，充当手机/电脑与被控设备通信的桥梁
- **连接方式**：支持局域网、AP 模式、蓝牙、USB HID（虚拟手柄/虚拟键盘）四种连接方式
- **上位机功能**：通过 ESP32 内置的 Web 服务器，可在电脑浏览器中进行实时调试和数据绘图
- **虚拟手柄**：ESP32 可作为 USB HID 设备，模拟 Xbox 手柄连接到电脑玩游戏
- **虚拟键盘/鼠标**：ESP32 可借助手机成为虚拟键盘/鼠标，向树莓派等嵌入式设备发送输入

---

## 固件开发路径

| 阶段 | 框架 | 描述 |
|------|------|------|
| Demo 版本 | Arduino | 快速原型验证功能 |
| 最终版本 | Zephyr RTOS | 生产级固件，需要支持 USB HID |

> 注意：最终版本使用 Zephyr RTOS 开发，两套代码需要保持 API 兼容

---

## 接收端示例代码（监听 ESP32 串口输出）

```
sample/
├── stm32_hal/           # STM32 HAL 库例程 (C/C++)
│   └── ...
├── stm32_zephyr/        # STM32 Zephyr RTOS 例程 (C++)
│   └── ...
├── arduino/             # Arduino 例程（独立于 ESP32 接收机）
│   └── ...
└── linux/               # Linux Python 脚本
    └── ...
```

> 注意：`sample/` 目录存放的是**接收 ESP32 接收机串口数据**的其他单片机或 Linux 设备的例程代码。
> ESP32 固件代码位于 `firmware/demo/` 目录。

### ESP32 固件（发射端）

```
firmware/demo/firmware_demo/   # Arduino Demo 版本
```

### 通信协议（JBus）

详细协议定义见 [JBus 通信协议文档](./docs/JBus_Protocol.md)

JBus 是手机、ESP32、MCU 三方通信协议：

```
手机 ──BLE/WiFi──► ESP32 ──UART──► MCU
```

- **手机 ↔ ESP32**：BLE 或 Wi-Fi UDP
- **ESP32 ↔ MCU**：UART 串口

ESP32 通过 UART 转发控制数据到 MCU，支持三种格式：

| 帧头 | 名称 | 数据长度 | 按钮数量 |
|------|------|----------|----------|
| 0xA0 | 最简帧 | 6 字节 | 8 个 |
| 0xAA | 标准帧 | 9 字节 | 32 个 |
| 0xAC | 16位帧 | 17 字节 | 32 个 |

### BLE 配网示例功能（ESP32 端）

- BLE 服务 UUID: `0000FFFF-0000-1000-8000-00805F9B34FB`
- WiFi SSID 特征: `0000FF01-0000-1000-8000-00805F9B34FB`
- WiFi 密码特征: `0000FF02-0000-1000-8000-00805F9B34FB`
- 状态特征: `0000FF03-0000-1000-8000-00805F9B34FB`
- 命令特征: `0000FF04-0000-1000-8000-00805F9B34FB`
- 通过 BLE 接收 WiFi SSID 和密码，保存到 Flash
- 支持命令：RESTART、RESET、START_UDP

### Wi-Fi UDP 接收端功能要求

- 支持 AP 模式（作为热点）
- 支持 Station 模式（连接 Wi-Fi）
- 监听 UDP 端口 1034 接收控制数据
- 监听 UDP 端口 1035 响应设备发现广播
- 广播格式：`JREMOTE:{设备名称}:{IP}:1034`
- 数据包格式见 JBus 协议（支持最简/标准/16位三种格式）
- 每次收到数据后回复 `0x50` 用于延迟计算

---

## 硬件板型

### ESP32-S3 开发板 (YD-ESP32-S3)

- **文档位置**: `boards/esp32s3_develop/BOARD_CONFIG.md`
- **原理图**: `boards/esp32s3_develop/YD-ESP32-S3-V1.4.pdf`
- **配置按钮**: GPIO0
- **RGB LED**: GPIO48 (WS2812)
- **推荐用于**: USB HID 虚拟手柄功能（ESP32-S3 支持 USB OTG）

### 引脚对应关系

| 功能 | GPIO | 说明 |
|------|------|------|
| 配置按钮 | GPIO0 | 按下进入 BLE 配网模式 |
| RGB LED | GPIO48 | WS2812 可编程 RGB LED |
| MCU TX | GPIO37 | ESP32→MCU 通信 |
| MCU RX | GPIO36 | MCU→ESP32 通信 |

### 接口说明

- **Type-C 接口 1**: 直通模拟串口（烧录 + 调试日志）
- **Type-C 接口 2**: CH340 转 TTL（ESP32 与 MCU 通信）
| USB D+ | GPIO20 | ESP32-S3 USB D+ |
| USB D- | GPIO19 | ESP32-S3 USB D- |

---

## 连接模式

应用支持四种连接模式：

- **局域网模式（Wi-Fi）**：手机与 ESP32 连接同一 Wi-Fi 网络，UDP/WebSocket 通信（推荐）
- **AP 模式（Wi-Fi 直连）**：手机连接 ESP32 热点，UDP/WebSocket 通信
- **蓝牙模式（BLE）**：传统低功耗蓝牙连接
- **USB HID 模式**：ESP32 作为 USB HID 设备，模拟 Xbox 手柄

---

## 项目概述

JRemote Controller 是一个 Android 遥控应用 + 一站式调试工具。使用 Kotlin 和 Jetpack Compose 构建。

---

## 功能需求清单

### 一、核心架构

| 需求 | 描述 | 优先级 |
|------|------|--------|
| 多连接模式 | 支持：局域网、AP、蓝牙、USB（HID）四种连接方式 | P0 |
| 统一通信协议 | 设计新的通信协议，支持双向数据传输 | P0 |
| 设备发现协议 | 局域网/AP 模式下 UDP 广播发现设备 | P0 |

### 二、通信协议（增强版）

| 需求 | 描述 | 优先级 |
|------|------|--------|
| 控制指令 | 下行：摇杆数据 + 按钮状态（现有，扩展） | P0 |
| 传感器回传 | 上行：ESP32 发送传感器数据（温湿度、IMU 等） | P1 |
| 调试信息 | 上行：串口日志、系统状态 | P1 |
| 文件传输 | 上行/下行：配置文件、固件更新 | P2 |
| 心跳机制 | 双向心跳，保持连接活跃 | P1 |

### 三、ESP32 固件

| 需求 | 描述 | 优先级 |
|------|------|--------|
| Web 服务器 | 内置 HTTP 服务器，提供上位机界面 | P0 |
| WebSocket | 实时双向通信，用于数据回传和指令下发 | P0 |
| USB HID 手柄 | 模拟 Xbox 手柄，USB 连接到电脑 | P0 |
| USB HID 键盘 | 模拟键盘，USB 连接到电脑（如树莓派） | P1 |
| USB HID 鼠标 | 模拟鼠标，支持点击和移动 | P1 |
| OTA 更新 | 支持通过网络升级固件 | P1 |
| 多角色切换 | 可在遥控模式/虚拟手柄模式之间切换 | P1 |

### 四、Android App - 遥控界面自定义

| 需求 | 描述 | 优先级 |
|------|------|--------|
| 自由拖拽 | 摇杆、按钮可以随意拖拽到任意位置 | P0 |
| 调整大小 | 组件支持调整尺寸 | P0 |
| 添加组件 | 可添加新的摇杆、按钮 | P0 |
| 删除组件 | 可删除已有的摇杆、按钮 | P0 |
| 组件属性 | 编辑组件名称、颜色、形状 | P1 |
| 缩放/旋转 | 支持组件缩放和旋转 | P2 |
| 多页面支持 | 支持多个控制页面（Page 1, Page 2...） | P1 |
| 图层管理 | 调整组件前后层级 | P2 |

### 五、Android App - 功能映射

| 需求 | 描述 | 优先级 |
|------|------|--------|
| 指令配置 | 每个按钮可配置发送的指令（字节数组） | P0 |
| 摇杆映射 | 摇杆数据可配置映射方式（直接值、按钮映射） | P0 |
| 快捷指令 | 支持预设快捷指令（如急停、复位） | P1 |
| 宏命令 | 支持录制和播放序列指令 | P2 |

### 六、Android App - 预设系统

| 需求 | 描述 | 优先级 |
|------|------|--------|
| 预设保存 | 保存当前界面为预设（JSON 格式） | P0 |
| 预设加载 | 一键加载预设 | P0 |
| 预设管理 | 增删改预设 | P0 |
| 内置预设 | 出厂自带多套预设模板 | P0 |
| 预设导入导出 | 分享预设文件 | P1 |
| 云同步 | 备份预设到云端 | P2 |

### 七、预设模板（开箱即用）

| 预设名称 | 描述 | 适用场景 |
|----------|------|----------|
| 基础遥控 | 双摇杆 + 4 按钮 | 入门 |
| 小车控制 | 摇杆 + 方向按钮 | 移动机器人 |
| 飞行模式 | 双摇杆 + 切换开关 | 无人机/穿越机 |
| 机械臂 | 多摇杆 + 多个按钮 | 机械臂控制 |
| 调试面板 | 纯传感器显示 + 指令输入 | 调试 |
| 虚拟手柄 | 专为 USB HID 手柄设计 | 电脑游戏 |
| 虚拟键盘 | 键盘/快捷键输入 | 树莓派等嵌入式设备 |
| 虚拟鼠标 | 摇杆控制移动 + 按钮点击 | 嵌入式设备远程控制 |

### 八、局域网上位机（Web 界面）

| 需求 | 描述 | 优先级 |
|------|------|--------|
| 实时数据绘图 | 使用 Chart.js 绘制传感器数据曲线 | P0 |
| 指令发送 | 网页上发送控制指令 | P0 |
| 串口监视器 | 显示 ESP32 串口输出 | P1 |
| 配置页面 | 配置 ESP32 参数 | P1 |
| 文件管理 | 上传/下载文件到 ESP32 | P2 |

---

## 开发计划

```
阶段 0：架构设计
├── 定义统一通信协议（兼容所有连接方式）
├── 设计 ESP32 抽象层（Arduino ↔ Zephyr）
└── 确定硬件选型

阶段 1：Demo 版本（Arduino）
├── 基础通信 + 控制
├── Web 服务器
├── WebSocket 双向通信
└── USB HID 虚拟手柄

阶段 2：Android App 预设系统
├── 预设保存/加载
├── 内置预设模板
└── 界面自定义

阶段 3：遥控界面重构
├── 自由拖拽布局
├── 功能映射
└── 多页面支持

阶段 4：Zephyr 固件开发
├── 迁移通信协议
├── 实现 USB HID
└── 优化和测试

阶段 5：上位机增强
└── 数据绘图等
```

---

## 构建命令

```bash
# 构建调试版 APK
cd android_app && ./gradlew assembleDebug

# 构建正式版 APK
cd android_app && ./gradlew assembleRelease

# 仅编译 Kotlin
cd android_app && ./gradlew compileDebugKotlin

# 运行测试
cd android_app && ./gradlew test

# 清理构建
cd android_app && ./gradlew clean
```

---

## 架构

### 整体结构

```
android_app/
├── app/src/main/java/com/example/jremote/
│   ├── MainActivity.kt           # 入口点，导航设置
│   ├── bluetooth/
│   │   ├── BleService.kt        # 蓝牙连接与通信
│   │   └── BluetoothService.kt  #（旧版，未使用）
│   ├── wifi/
│   │   ├── WifiService.kt       # Wi-Fi UDP 连接与通信
│   │   └── UdpDiscovery.kt      # UDP 设备发现
│   ├── components/
│   │   ├── ControlButton.kt     # 可复用按钮组件
│   │   ├── Joystick.kt          # 摇杆组件
│   │   └── DebugPanel.kt        # 调试信息面板
│   ├── data/
│   │   ├── AppSettings.kt       # 设置数据模型
│   │   ├── ButtonConfig.kt      # 按钮配置
│   │   ├── ConnectionStatus.kt  # 连接状态
│   │   ├── ConnectionMode.kt    # 连接模式枚举（BLE/AP/局域网）
│   │   ├── DiscoveredDevice.kt  # 发现设备数据模型
│   │   ├── ControlData.kt       # 控制数据包格式
│   │   ├── SettingsRepository.kt # DataStore 持久化
│   │   ├── JoystickState.kt     # 摇杆位置数据
│   │   ├── DebugManager.kt      # 统一调试消息管理
│   │   ├── DebugMessage.kt      # 调试消息数据类
│   │   └── DebugLevel.kt        # 调试级别枚举
│   ├── screen/
│   │   ├── ControlScreen.kt     # 主控制界面（横屏）
│   │   ├── ConnectionScreen.kt   # 设备扫描与连接
│   │   └── SettingsScreen.kt    # 应用设置
│   ├── viewmodel/
│   │   └── ControlViewModel.kt  # 业务逻辑与状态管理
│   └── ui/theme/
│       ├── Theme.kt              # Material 3 主题
│       ├── Color.kt             # 颜色定义
│       └── Type.kt              # 字体排版
```

### 数据流

1. **设置**：SettingsScreen → ControlViewModel → SettingsRepository → DataStore（持久化）
2. **蓝牙连接**：ControlViewModel → BleService → BluetoothGatt 回调
3. **Wi-Fi 连接**：ControlViewModel → WifiService → UDP Socket
4. **控制数据**：UI → ControlViewModel → (BleService / WifiService) → 设备
5. **状态**：设备回调 → Service → ControlViewModel → UI（StateFlow）

### 核心类

- **BleService**：处理所有蓝牙操作（扫描、连接、断开、发送数据、RSSI/ping）
- **WifiService**：处理 Wi-Fi UDP 连接（连接、断开、发送数据、ping）
- **UdpDiscovery**：处理 UDP 设备发现（广播发现局域网/AP 模式设备）
- **ControlViewModel**：中央状态管理器，负责连接模式切换、数据发送、设置管理
- **SettingsRepository**：封装 DataStore，用于设置持久化
- **DebugManager**：统一调试消息管理，所有调试输出通过此类的 log/info/warning/error 方法
- **ConnectionScreen**：设备扫描与连接界面，包含模式切换下拉菜单
- **ControlScreen**：横屏 UI，包含双摇杆和按钮
- **ControlButton/Joystick**：支持触觉反馈的可复用 UI 组件

### 调试消息架构

调试消息使用统一的 DebugManager 管理：

```
DebugManager (data/DebugManager.kt)
├── debugMessages: StateFlow<List<DebugMessage>>
├── log(level, tag, message)
├── info(tag, message)
├── warning(tag, message)
├── error(tag, message)
└── clear()

BleService / WifiService
└── 每个服务创建自己的 DebugManager 实例
    └── debugMessages 通过 combine 合并到 ControlViewModel

ControlScreen
└── 从 ControlViewModel 获取合并后的 debugMessages 显示
```

- **MUC 消息**：来自 ESP32 的数据使用 "MUC" 标签，绿色显示
- **BLE/WiFi 服务消息**：各自的 DebugManager 管理
- **合并显示**：ControlViewModel 使用 combine 合并两个服务的消息

### 通信协议

> 详细协议定义见 [JBus 通信协议文档](./docs/JBus_Protocol.md)

#### BLE（蓝牙）

蓝牙服务 UUID：`4fafc201-1fb5-459e-8fcc-c5c9c331914b`
- TX 特征：`beb5483e-36e1-4688-b7f5-ea07361b26a8`（从设备接收）
- RX 特征：`6e400002-b5a3-f393-e0a9-e50e24dcca9e`（向设备发送）

#### Wi-Fi（UDP）

- **通信端口**：1034
- **发现协议**：UDP 广播
- **发现端口**：1035
- **设备广播内容**：`JREMOTE:{设备名称}:{IP}:{端口}`

#### 控制数据包

| 帧头 | 名称 | 数据长度 | 按钮数量 |
|------|------|----------|----------|
| 0xA0 | 最简帧 | 6 字节 | 8 个 |
| 0xAA | 标准帧 | 9 字节 | 32 个 |
| 0xAC | 16位帧 | 17 字节 | 32 个 |
| 0xEE | 急停 | 1 字节 | - |

#### Ping 机制

- Wi-Fi 模式：发送单字节 `0x70` ('p')，设备响应 `0x50` ('P')

---

## Zephyr 固件架构

### 分层设计

Zephyr 固件采用传输层与数据处理层分离的分层架构，便于后期添加 AP/LAN 模式：

```
┌─────────────────────────────────────────────────────────────────┐
│                         Transport Layer                          │
│  (只负责收发数据，通过消息队列将数据传递给 DataHandler)            │
├─────────────┬─────────────┬─────────────┬─────────────────────────┤
│    Ble      │   WifiAp    │   WifiSta   │   (未来扩展)            │
│  (BLE传输)   │  (AP模式)   │  (LAN模式)   │                         │
└──────┬──────┴──────┬──────┴──────┬──────┴─────────────────────────┘
       │             │             │
       │  k_msgq     │  k_msgq     │  k_msgq
       ▼             ▼             ▼
┌─────────────────────────────────────────────────────────────────┐
│                        DataHandler (路由器)                       │
│  - 接收来自所有传输层的数据                                        │
│  - 根据帧头路由到对应的处理器                                      │
│  - 帧头: 0xAA -> ControlDataHandler, 0xBB -> UserDataHandler     │
└───────────────────────────┬─────────────────────────────────────┘
                            │
            ┌───────────────┴───────────────┐
            ▼                               ▼
┌─────────────────────┐       ┌─────────────────────┐
│ ControlDataHandler  │       │  UserDataHandler    │
│ (控制数据处理)       │       │  (用户数据处理)      │
│ -> UART -> MCU      │       │  -> 回传给 App       │
└─────────────────────┘       └─────────────────────┘
```

### 文件结构

```
firmware/zephyr_app/app/
├── inc/
│   ├── peripheral/              # 外设相关头文件
│   │   ├── ble.h                # BLE 传输层
│   │   ├── serial.h             # 串口通信
│   │   └── ws2812_led.h         # LED 控制
│   └── data/                    # 数据处理相关头文件
│       ├── control_data.h       # 控制数据结构定义
│       ├── data_handler.h       # 数据路由器
│       ├── control_data_handler.h  # 控制数据处理器
│       └── user_data_handler.h  # 用户数据处理器
├── src/
│   ├── peripheral/              # 外设相关源文件
│   │   ├── ble.cpp              # BLE 实现
│   │   └── ws2812_led.cpp       # LED 实现
│   ├── data/                    # 数据处理相关源文件
│   │   ├── data_handler.cpp     # 数据路由实现
│   │   ├── control_data_handler.cpp  # 控制数据处理实现
│   │   └── user_data_handler.cpp    # 用户数据处理实现
│   └── main.cpp                 # 入口点，初始化各组件
├── boards/                      # 开发板配置
│   └── esp32s3_devkitm_procpu.overlay
├── CMakeLists.txt
├── Kconfig
└── prj.conf
```

### 核心类

| 类 | 职责 | 说明 |
|---|------|------|
| `Ble` | BLE 传输层 | 只负责 BLE 连接和数据收发，收到数据后调用 `DataHandler::SubmitData()` |
| `DataHandler` | 数据路由器 | 接收所有传输层的数据，根据帧头路由到对应处理器 |
| `ControlDataHandler` | 控制数据处理 | 解析控制数据（9字节），转发到 UART |
| `UserDataHandler` | 用户数据处理 | 处理用户数据，回传给 App |
| `Serial` | 串口通信 | 与 MCU 通信 |

### 数据流

```
BLE RX (ISR) -> Ble::OnRxData() -> DataHandler::SubmitData() -> k_msgq
                                                           -> DataHandler::ProcessLoop()
                                                           -> RouteMessage()
                                                           -> ControlDataHandler / UserDataHandler
```

### 消息队列格式

```cpp
// 统一的消息结构
struct TransportMessage {
    uint8_t source;      // 来源: 0=BLE, 1=AP, 2=LAN
    uint16_t length;     // 数据长度
    uint8_t data[255];   // 数据内容
};
```

### 帧头定义

```cpp
enum class FrameType : uint8_t {
    kControlData = 0xAA,  // 控制数据 (9 bytes)
    kUserData = 0xBB,     // 用户数据 (变长)
    kEmergency = 0xEE,    // 急停
};
```

### 连接类型

```cpp
enum class ConnectionType : uint8_t {
    BLE = 0,    // 蓝牙连接
    AP = 1,     // AP 模式连接
    LAN = 2,    // 局域网连接
    UART = 3,   // 串口连接
};
```

### 扩展指南

添加 AP 模式时：

```cpp
class WifiAp {
public:
    void Init();
    void SendData(const uint8_t* data, uint16_t len);
    void SetDataHandler(DataHandler* handler);
private:
    DataHandler* data_handler_;
};
```

WifiAp 收到数据后调用 `data_handler_->SubmitData(ConnectionType::AP, data, len)`，无需修改 DataHandler。

### 构建命令

```bash
cd firmware/zephyr_app
source .venv/bin/activate
west build -b esp32s3_devkitm/esp32s3/procpu app
west flash
```

---

## 开发说明

- 使用 Jetpack Compose 与 Material Design 3
- 采用 MVVM 架构，配合 StateFlow
- 设置使用 DataStore Preferences 持久化
- 触觉反馈可通过设置开关
- 自动重连功能会保存上次连接的设备地址（包含连接模式）
- 支持跟随系统的深色/浅色模式
- Android 12+ 支持动态颜色（跟随系统壁纸）
- 连接界面使用底部导航栏切换 BLE/AP/局域网 三种模式
- Wi-Fi 发现使用 UDP 广播，设备响应包含名称和 IP
- 连接异常时仅提示用户，不自动切换模式
