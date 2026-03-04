/*
 * JRemote Controller - ESP32 固件 V1.0 (Demo)
 * 支持：WiFi UDP + BLE 控制 + BLE 配网 + Web 服务器 + LED 状态指示
 * 双击 GPIO0 切换 WiFi/蓝牙模式
 *
 * 库依赖（通过 Arduino Library Manager 安装）：
 * - FastLED
 * - WebServer (ESP32 内置)
 * - WiFi (ESP32 内置)
 * - BLE (ESP32 内置)
 * - WebSocketServer: https://github.com/Links2004/arduinoWebSockets
 */

#include <Arduino.h>
#include <WiFi.h>
#include <WiFiUdp.h>
#include <Preferences.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <FastLED.h>
#include <WebServer.h>
#include <WebSocketsServer.h>
#include <math.h>

// 头文件
#include "led.h"
#include "button.h"
#include "wifi.h"
#include "ble_config.h"
#include "ble_control.h"
#include "webserver.h"
#include "mcu.h"
#include "globals.h"

// ==================== 配置 ====================

// WiFi 配置
const char* DEFAULT_AP_SSID = "JRemote_ESP32";
const char* DEFAULT_AP_PASSWORD = "12345678";

// GPIO 配置
extern const int CONFIG_BUTTON_PIN = 0;
const int LED_PIN = 48;
const int NUM_LEDS = 1;

// UART 配置 (与 MCU 通信)
const int MCU_UART_RX = 36;
const int MCU_UART_TX = 37;
const int MCU_UART_BAUD = 115200;

// UDP 端口
const int DATA_PORT = 1034;
const int DISCOVERY_PORT = 1035;

// BLE 配网服务 UUID
#define CONFIG_SERVICE_UUID "0000FFFF-0000-1000-8000-00805F9B34FB"
#define WIFI_SSID_UUID    "0000FF01-0000-1000-8000-00805F9B34FB"
#define WIFI_PASSWORD_UUID "0000FF02-0000-1000-8000-00805F9B34FB"
#define STATUS_UUID       "0000FF03-0000-1000-8000-00805F9B34FB"
#define COMMAND_UUID     "0000FF04-0000-1000-8000-00805F9B34FB"

// BLE 控制服务 UUID
#define CONTROL_SERVICE_UUID "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CONTROL_TX_UUID    "beb5483e-36e1-4688-b7f5-ea07361b26a8"
#define CONTROL_RX_UUID   "6e400002-b5a3-f393-e0a9-e50e24dcca9e"

// ==================== 全局变量 ====================

// LED
CRGB leds[NUM_LEDS];
LEDStatus currentLEDStatus = LED_OFF;
unsigned long lastDataTime = 0;

// 模式
WorkMode currentMode = MODE_WIFI_UDP;
WorkMode savedMode = MODE_WIFI_UDP;

// WiFi
Preferences preferences;
WiFiUDP dataUdp;
WiFiUDP discoveryUdp;
String deviceName = "ESP32_JRemote";
IPAddress phoneIP;

// BLE 配置模式
BLEServer *pConfigServer = nullptr;
BLECharacteristic *pStatusCharacteristic = nullptr;
bool bleConfigConnected = false;
bool isConfigMode = false;

// BLE 控制模式
BLEServer *pControlServer = nullptr;
BLECharacteristic *pControlTxCharacteristic = nullptr;
BLECharacteristic *pControlRxCharacteristic = nullptr;
bool bleControlConnected = false;

// Web 服务器
WebServer webServer(80);
WebSocketsServer webSocketServer(81);
String webSocketData = "";

// MCU 通信
String mcuBuffer = "";

// 运行时变量
bool isConnected = false;

// 控制数据
int leftJoystickX = 0, leftJoystickY = 0;
int rightJoystickX = 0, rightJoystickY = 0;
uint32_t buttonState = 0;

// ==================== 主程序 ====================

void setup() {
    Serial.begin(115200);
    delay(100);

    // 初始化 LED
    FastLED.addLeds<WS2812, LED_PIN, GRB>(leds, NUM_LEDS);
    leds[0] = CRGB::Black;
    FastLED.show();

    // 初始化按钮
    pinMode(CONFIG_BUTTON_PIN, INPUT_PULLUP);

    Serial.println();
    Serial.println("=== JRemote Controller V1.0 ===");

    // 初始化 Preferences
    preferences.begin("jremote-config", false);

    // 加载保存的模式
    String savedModeStr = preferences.getString("workMode", "WIFI");
    if (savedModeStr == "BLE") {
        savedMode = MODE_BLE;
    } else {
        savedMode = MODE_WIFI_UDP;
    }

    // 初始化 MCU 串口
    Serial1.begin(MCU_UART_BAUD, SERIAL_8N1, MCU_UART_RX, MCU_UART_TX);

    // 检查是否有保存的 WiFi 配置
    String savedSsid = preferences.getString("ssid", "");

    if (savedSsid.length() > 0) {
        // 有保存的 WiFi 配置，连接 WiFi
        startWiFiMode();
    } else {
        // 没有 WiFi 配置，进入配网模式
        switchToConfigMode();
    }

    Serial.println("初始化完成");
}

void loop() {
    // 处理按钮（双击检测）
    handleButton();

    // 根据模式处理任务
    switch (currentMode) {
        case MODE_WIFI_UDP:
            handleData();
            handleDiscovery();
            break;
        case MODE_BLE:
            // BLE 由回调处理
            break;
        case MODE_CONFIG:
            // 配网模式由回调处理
            break;
    }

    // 更新 LED
    updateLEDStatus();

    // 处理 Web 服务器
    webServer.handleClient();
    webSocketServer.loop();

    // 处理 MCU 消息
    while (Serial1.available()) {
        char c = Serial1.read();
        if (c == '\n' || c == '\r') {
            if (mcuBuffer.length() > 0) {
                handleMCUMessage(mcuBuffer);
                mcuBuffer = "";
            }
        } else {
            mcuBuffer += c;
        }
    }

    delay(10);
}
