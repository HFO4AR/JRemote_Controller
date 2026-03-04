# JRemote Controller 固件 V1.0 (Demo)

ESP32 固件，支持 WiFi UDP + BLE 控制 + BLE 配网 + Web 服务器

## 功能列表

| 功能 | 描述 |
|------|------|
| WiFi UDP | 局域网模式，UDP 通信 |
| BLE 控制 | 蓝牙模式接收控制指令 |
| BLE 配网 | 通过蓝牙配置 WiFi 凭证 |
| Web 服务器 | 调试上位机界面 |
| LED 状态 | WS2812 彩色指示 |
| MCU 通信 | UART 与 MCU 通信 |

## 模式切换

- **双击 GPIO0**：切换 WiFi/蓝牙模式
- **配网**：首次使用或无保存 WiFi 时自动进入

## 引脚配置

| 功能 | GPIO |
|------|------|
| 配置按钮 | GPIO0 |
| RGB LED | GPIO48 |
| MCU TX | GPIO37 |
| MCU RX | GPIO36 |

## 库依赖

通过 Arduino Library Manager 安装：

- **FastLED** - WS2812 LED 控制

手动安装：
- **WebSocketServer**: https://github.com/Links2004/arduinoWebSockets

## 使用说明

### 1. 编译固件

1. 打开 `firmware_demo.ino`
2. 选择开发板：`ESP32-S3 Dev Module`
3. 编译并上传

### 2. 首次配网

1. 首次上电后自动进入配网模式
2. 使用 Android App 连接 "ESP32_Config"
3. 配置 WiFi SSID 和密码

### 3. 访问上位机

连接 WiFi 后，在浏览器中访问 ESP32 的 IP 地址

### 4. 与 MCU 通信

ESP32 通过 UART (GPIO36/37) 与 MCU 通信

## 通信协议

### UDP 数据格式（9 字节）

| 字节 | 描述 |
|------|------|
| 0 | 帧头 (0xAA=正常, 0xEE=急停) |
| 1-2 | 左摇杆 X, Y (-127~127) |
| 3-4 | 右摇杆 X, Y (-127~127) |
| 5-8 | 按钮状态 |

### BLE UUID

- 服务: `4fafc201-1fb5-459e-8fcc-c5c9c331914b`
- TX: `beb5483e-36e1-4688-b7f5-ea07361b26a8`
- RX: `6e400002-b5a3-f393-e0a9-e50e24dcca9e`

## LED 状态

| 状态 | 颜色 |
|------|------|
| WiFi 连接中 | 白色快闪 |
| WiFi 已连接 | 绿色呼吸灯 |
| WiFi 失败 | 红色慢闪 |
| BLE 连接中 | 紫色快闪 |
| BLE 已连接 | 紫色呼吸灯 |
| 配网模式 | 蓝色快闪 |
| 收到数据 | 青色闪烁 |
