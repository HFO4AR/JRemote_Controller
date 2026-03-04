# ESP32-S3 开发板配置

## 硬件信息

- **型号**: YD-ESP32-S3 (ESP32-S3-0702)
- **原理图**: `YD-ESP32-S3-V1.4.pdf`

## 接口分布

### Type-C 接口

| 接口 | 功能 | 说明 |
|------|------|------|
| 串口 1 | 直通模拟串口 | 用于固件烧录和调试日志输出 |
| 串口 2 | CH340 转 TTL | ESP32 与 MCU 通信 |

### 引脚配置

| 功能 | GPIO | 说明 |
|------|------|------|
| 配置按钮 | GPIO0 | 按下进入 BLE 配网模式 |
| RGB LED | GPIO48 | WS2812 可编程 RGB LED |
| MCU TX (ESP32→MCU) | GPIO37 | CH340 转接板的 RX |
| MCU RX (MCU→ESP32) | GPIO36 | CH340 转接板的 TX |

> **注意**：ESP32 与 MCU 通过 TTL 串口通信，通信引脚为 GPIO37 (TX) 和 GPIO36 (RX)

## WS2812 LED 控制

### 库依赖

需要在 Arduino IDE 中安装 `FastLED` 库：

```cpp
#include <FastLED.h>
```

### 基本使用

```cpp
#include <FastLED.h>

#define LED_PIN 48
#define NUM_LEDS 1

CRGB leds[NUM_LEDS];

void setup() {
    FastLED.addLeds<WS2812, LED_PIN, GRB>(leds, NUM_LEDS);
}

void loop() {
    // 设置颜色
    leds[0] = CRGB::Red;      // 红色
    leds[0] = CRGB::Green;    // 绿色
    leds[0] = CRGB::Blue;    // 蓝色
    leds[0] = CRGB::Purple;  // 紫色
    leds[0] = CRGB::Black;   // 关闭

    // 自定义颜色 (R, G, B)
    leds[0] = CRGB(255, 128, 0);  // 橙色

    FastLED.show();
    delay(100);
}
```

### 常用颜色示例

| 颜色 | 代码 |
|------|------|
| 红色 | `CRGB::Red` |
| 绿色 | `CRGB::Green` |
| 蓝色 | `CRGB::Blue` |
| 黄色 | `CRGB::Yellow` |
| 紫色 | `CRGB::Purple` |
| 青色 | `CRGB::Cyan` |
| 白色 | `CRGB::White` |
| 黑色(关闭) | `CRGB::Black` |

## 固件配置

### 当前引脚定义

```cpp
// 配置按钮引脚（运行时按下进入配网模式）
const int CONFIG_BUTTON_PIN = 0;

// WS2812 LED 引脚
#define LED_PIN 48
```

### 模式指示

| 状态 | LED 颜色/行为 |
|------|---------------|
| 配网模式 | 蓝色快闪 |
| WiFi 连接中 | 白色快闪 |
| WiFi 连接成功 | 绿色呼吸灯 |
| WiFi 连接失败 | 红色慢闪 |
| 收到控制数据 | 青色闪烁 |

### 代码中的 LED 控制

```cpp
// 设置 LED 状态
setLEDStatus(LED_CONFIG_MODE);      // 配网模式 - 蓝色快闪
setLEDStatus(LED_WIFI_CONNECTING); // 连接中 - 白色快闪
setLEDStatus(LED_WIFI_CONNECTED);  // 连接成功 - 绿色呼吸灯
setLEDStatus(LED_WIFI_FAILED);     // 连接失败 - 红色慢闪
setLEDStatus(LED_DATA_RECEIVED);   // 收到数据 - 青色闪烁
setLEDStatus(LED_OFF);             // 关闭 LED
```

## 注意事项

1. GPIO0 在启动时用于进入下载模式，正常运行时作为普通 GPIO
2. WS2812 需要严格的时序控制，使用 FastLED 库
3. LED 电源为 5V，与 ESP32-S3 的 3.3V 逻辑电平兼容
