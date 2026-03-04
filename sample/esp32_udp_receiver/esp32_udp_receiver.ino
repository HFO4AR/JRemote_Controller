/*
 * JRemote Controller - ESP32 UDP 接收端示例
 * 支持 AP 模式和 Station 模式 + BLE 配网
 */

#include <WiFi.h>
#include <WiFiUdp.h>
#include <Preferences.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <FastLED.h>
#include <string.h>

// ==================== 配置 ====================

// 选择运行模式: true = AP 模式, false = Station 模式
const bool USE_AP_MODE = false;

// AP 模式配置
const char* AP_SSID = "JRemote_ESP32";
const char* AP_PASSWORD = "12345678";

// Station 模式配置（硬编码方式，留空使用 BLE 配网）
const char* STA_SSID = "";
const char* STA_PASSWORD = "";

// 配置按钮引脚（运行时按下进入配网模式）
const int CONFIG_BUTTON_PIN = 0;

// 设备名称
const char* DEVICE_NAME = "ESP32_JRemote";

// UDP 端口配置
const int DATA_PORT = 1034;
const int DISCOVERY_PORT = 1035;

// BLE 配网服务 UUID
#define CONFIG_SERVICE_UUID "0000FFFF-0000-1000-8000-00805F9B34FB"
#define WIFI_SSID_UUID    "0000FF01-0000-1000-8000-00805F9B34FB"
#define WIFI_PASSWORD_UUID "0000FF02-0000-1000-8000-00805F9B34FB"
#define STATUS_UUID       "0000FF03-0000-1000-8000-00805F9B34FB"
#define COMMAND_UUID     "0000FF04-0000-1000-8000-00805F9B34FB"

// WS2812 RGB LED 配置
#define LED_PIN 48
#define NUM_LEDS 1
CRGB leds[NUM_LEDS];

// ==================== 全局变量 ====================

Preferences preferences;
WiFiUDP dataUdp;
WiFiUDP discoveryUdp;

// BLE 变量
BLEServer *pServer = nullptr;
BLEService *pConfigService = nullptr;
BLECharacteristic *pStatusCharacteristic = nullptr;
bool bleDeviceConnected = false;
bool isConfigMode = false;

// 运行时变量
bool isConnected = false;
unsigned long lastDataTime = 0;
unsigned long lastHelloTime = 0;

// 手机IP地址
IPAddress phoneIP;

// 摇杆数据
int leftJoystickX = 0, leftJoystickY = 0;
int rightJoystickX = 0, rightJoystickY = 0;
uint32_t buttonState = 0;

// ==================== LED 状态 ====================

// LED 状态枚举
enum LEDStatus {
    LED_OFF,
    LED_WIFI_CONNECTING,   // 白色快闪 - WiFi 连接中
    LED_WIFI_CONNECTED,    // 绿色 - WiFi 已连接
    LED_WIFI_FAILED,       // 红色 - WiFi 连接失败
    LED_CONFIG_MODE,       // 蓝色闪烁 - 配网模式
    LED_DATA_RECEIVED      // 青色闪烁 - 收到数据
};

LEDStatus currentLEDStatus = LED_OFF;
unsigned long lastLEDUpdate = 0;

// LED 函数声明
void setLEDStatus(LEDStatus status);
void updateLEDStatus();

// ==================== 函数声明 ====================

void setup();
void loop();
void startAPMode();
void startStationMode(const char* ssid = nullptr, const char* password = nullptr);
void startBleConfigMode();
void setupUDP();
void handleData();
void handleDiscovery();
void processControlData(uint8_t* data, int length);
void sendDiscoveryResponse(IPAddress& clientIP);
void blinkLED(int times, int delayMs);

// ==================== 实现 ====================

void setup() {
    Serial.begin(115200);
    delay(100);

    // 初始化 WS2812 LED
    FastLED.addLeds<WS2812, LED_PIN, GRB>(leds, NUM_LEDS);
    leds[0] = CRGB::Black;
    FastLED.show();

    pinMode(CONFIG_BUTTON_PIN, INPUT_PULLUP);

    Serial.println();
    Serial.println("=== JRemote Controller ESP32 UDP Receiver ===");
    Serial.printf("设备名称: %s\n", DEVICE_NAME);

    // 初始化 Preferences
    preferences.begin("wifi-config", false);

    // 检查是否有硬编码的 WiFi 配置
    bool hasHardcodedConfig = strlen(STA_SSID) > 0;

    // 检查是否有保存的 WiFi 配置
    String savedSsid = preferences.getString("ssid", "");
    bool hasSavedConfig = savedSsid.length() > 0;

    Serial.printf("配置检查: 硬编码=%d, 已保存=%d, SSID=%s\n", hasHardcodedConfig, hasSavedConfig, savedSsid.c_str());

    if (USE_AP_MODE) {
        startAPMode();
    } else if (!hasHardcodedConfig && !hasSavedConfig) {
        startBleConfigMode();
    } else if (hasHardcodedConfig) {
        Serial.println("使用硬编码 WiFi 配置");
        startStationMode(STA_SSID, STA_PASSWORD);
    } else if (hasSavedConfig) {
        Serial.println("使用保存的 WiFi 配置");
        String savedPassword = preferences.getString("password", "");
        startStationMode(savedSsid.c_str(), savedPassword.c_str());
    }

    setupUDP();

    Serial.println("=== 初始化完成 ===");
    Serial.println("运行时按 IO0 按钮进入配网模式");
}

void loop() {
    // 处理 UDP 数据
    handleData();
    handleDiscovery();

    // IO0 按钮检测 - 按下进入配网模式
    static bool lastButtonState = HIGH;
    bool currentButtonState = digitalRead(CONFIG_BUTTON_PIN);

    if (currentButtonState == LOW && lastButtonState == HIGH) {
        Serial.println("IO0 按钮按下! 进入配网模式...");
        if (!isConfigMode) {
            // 停止 WiFi
            WiFi.disconnect();
            delay(100);
            startBleConfigMode();
        }
    }
    lastButtonState = currentButtonState;

    // LED 状态指示
    updateLEDStatus();

    delay(10);
}

void startStationMode(const char* ssid, const char* password) {
    Serial.println("启动 Station 模式...");

    bool hasPassword = (password != nullptr && strlen(password) > 0);
    Serial.printf("连接 WiFi: %s, 密码: %s\n", ssid, hasPassword ? "已设置" : "无密码(开放网络)");

    setLEDStatus(LED_WIFI_CONNECTING);
    WiFi.mode(WIFI_STA);

    if (hasPassword) {
        WiFi.begin(ssid, password);
    } else {
        WiFi.begin(ssid);
    }

    int attempts = 0;
    while (WiFi.status() != WL_CONNECTED && attempts < 30) {
        delay(500);
        Serial.print(".");
        Serial.printf(" WiFi状态: %d\n", WiFi.status());
        attempts++;
    }

    if (WiFi.status() == WL_CONNECTED) {
        Serial.println();
        Serial.print("Station IP 地址: ");
        Serial.println(WiFi.localIP());
        Serial.print("网关: ");
        Serial.println(WiFi.gatewayIP());
        setLEDStatus(LED_WIFI_CONNECTED);
    } else {
        Serial.println();
        Serial.println("WiFi 连接失败!");
        Serial.printf("最终 WiFi 状态: %d\n", WiFi.status());
        setLEDStatus(LED_WIFI_FAILED);
    }
}

void setupUDP() {
    if (dataUdp.begin(DATA_PORT)) {
        Serial.printf("数据端口 %d 绑定成功\n", DATA_PORT);
    } else {
        Serial.printf("数据端口 %d 绑定失败!\n", DATA_PORT);
    }

    if (discoveryUdp.begin(DISCOVERY_PORT)) {
        Serial.printf("发现端口 %d 绑定成功\n", DISCOVERY_PORT);
    } else {
        Serial.printf("发现端口 %d 绑定失败!\n", DISCOVERY_PORT);
    }
}

// ==================== BLE 配网模式 ====================

class ConfigServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
        bleDeviceConnected = true;
        Serial.println("手机已连接");
        updateConfigStatus("已连接");
    }

    void onDisconnect(BLEServer* pServer) {
        bleDeviceConnected = false;
        Serial.println("手机已断开");
        delay(500);
        pServer->startAdvertising();
    }
};

class SsidCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
        String ssid = pCharacteristic->getValue().c_str();
        Serial.printf("收到 SSID: %s\n", ssid.c_str());
        preferences.putString("ssid", ssid);
        updateConfigStatus("SSID 已保存");

        String savedPassword = preferences.getString("password", "");
        if (savedPassword.length() > 0) {
            updateConfigStatus("正在连接 WiFi...");
            delay(500);
            startStationMode(ssid.c_str(), savedPassword.c_str());

            if (WiFi.status() == WL_CONNECTED) {
                updateConfigStatus("WiFi 连接成功!");
                delay(1000);
                ESP.restart();
            } else {
                updateConfigStatus("WiFi 连接失败");
            }
        }
    }
};

class PasswordCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
        String password = pCharacteristic->getValue().c_str();
        Serial.printf("收到密码: %s\n", password.c_str());
        preferences.putString("password", password);
        updateConfigStatus("密码已保存");

        String savedSsid = preferences.getString("ssid", "");
        if (savedSsid.length() > 0) {
            updateConfigStatus("正在连接 WiFi...");
            delay(500);
            startStationMode(savedSsid.c_str(), password.c_str());

            if (WiFi.status() == WL_CONNECTED) {
                updateConfigStatus("WiFi 连接成功!");
                delay(1000);
                ESP.restart();
            } else {
                updateConfigStatus("WiFi 连接失败");
            }
        }
    }
};

class CommandCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
        String command = pCharacteristic->getValue().c_str();
        Serial.printf("收到命令: %s\n", command.c_str());

        if (command == "RESTART") {
            updateConfigStatus("重启中...");
            delay(1000);
            ESP.restart();
        } else if (command == "RESET") {
            preferences.clear();
            updateConfigStatus("配置已清除");
        }
    }
};

void startBleConfigMode() {
    isConfigMode = true;
    setLEDStatus(LED_CONFIG_MODE);
    Serial.println("进入 BLE 配网模式...");

    WiFi.mode(WIFI_OFF);
    delay(100);

    BLEDevice::init("ESP32_Config");

    pServer = BLEDevice::createServer();
    pServer->setCallbacks(new ConfigServerCallbacks());

    pConfigService = pServer->createService(CONFIG_SERVICE_UUID);

    BLECharacteristic *pWifiSsidCharacteristic = pConfigService->createCharacteristic(
        WIFI_SSID_UUID, BLECharacteristic::PROPERTY_WRITE);
    pWifiSsidCharacteristic->setCallbacks(new SsidCallbacks());
    pWifiSsidCharacteristic->addDescriptor(new BLE2902());

    BLECharacteristic *pWifiPasswordCharacteristic = pConfigService->createCharacteristic(
        WIFI_PASSWORD_UUID, BLECharacteristic::PROPERTY_WRITE);
    pWifiPasswordCharacteristic->setCallbacks(new PasswordCallbacks());
    pWifiPasswordCharacteristic->addDescriptor(new BLE2902());

    pStatusCharacteristic = pConfigService->createCharacteristic(
        STATUS_UUID, BLECharacteristic::PROPERTY_NOTIFY);
    pStatusCharacteristic->addDescriptor(new BLE2902());

    BLECharacteristic *pCommandCharacteristic = pConfigService->createCharacteristic(
        COMMAND_UUID, BLECharacteristic::PROPERTY_WRITE);
    pCommandCharacteristic->setCallbacks(new CommandCallbacks());
    pCommandCharacteristic->addDescriptor(new BLE2902());

    pConfigService->start();

    BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
    pAdvertising->addServiceUUID(CONFIG_SERVICE_UUID);
    pAdvertising->setScanResponse(true);
    BLEDevice::startAdvertising();

    Serial.println("BLE 配网服务已启动");
    Serial.println("请使用 Android 应用连接并配置 WiFi");
    updateConfigStatus("等待配置...");
}

void updateConfigStatus(const char* status) {
    if (pStatusCharacteristic != nullptr && bleDeviceConnected) {
        pStatusCharacteristic->setValue(status);
        pStatusCharacteristic->notify();
    }
    Serial.println(status);
}

// ==================== 数据处理 ====================

void handleData() {
    int packetSize = dataUdp.parsePacket();

    if (packetSize > 0) {
        IPAddress remoteIP = dataUdp.remoteIP();
        int remotePort = dataUdp.remotePort();

        phoneIP = remoteIP;

        uint8_t packetBuffer[64];
        int len = dataUdp.read(packetBuffer, sizeof(packetBuffer) - 1);

        if (len > 0) {
            packetBuffer[len] = 0;

            // 每次收到数据都发送响应
            dataUdp.beginPacket(remoteIP, remotePort);
            dataUdp.write(0x50);
            dataUdp.endPacket();

            if (len == 1 && packetBuffer[0] == 0x70) {
                Serial.println("收到 Ping, 发送响应");
            } else {
                processControlData(packetBuffer, len);
                isConnected = true;
                lastDataTime = millis();
                setLEDStatus(LED_DATA_RECEIVED);

                Serial.printf("收到控制数据: %d bytes\n", len);
            }
        }
    }
}

void handleDiscovery() {
    int packetSize = discoveryUdp.parsePacket();

    if (packetSize > 0) {
        char packetBuffer[64];
        int len = discoveryUdp.read(packetBuffer, sizeof(packetBuffer) - 1);

        if (len > 0) {
            packetBuffer[len] = 0;
            Serial.printf("收到发现请求: %s\n", packetBuffer);

            if (String(packetBuffer).startsWith("JREMOTE_DISCOVER")) {
                IPAddress clientIP = discoveryUdp.remoteIP();
                int clientPort = discoveryUdp.remotePort();
                sendDiscoveryResponse(clientIP);
            }
        }
    }
}

void sendDiscoveryResponse(IPAddress& clientIP) {
    char response[128];
    IPAddress localIP = WiFi.localIP();

    if (USE_AP_MODE) {
        localIP = WiFi.softAPIP();
    }

    snprintf(response, sizeof(response), "JREMOTE:%s:%d.%d.%d.%d:%d",
             DEVICE_NAME,
             localIP[0], localIP[1], localIP[2], localIP[3],
             DATA_PORT);

    discoveryUdp.beginPacket(clientIP, DISCOVERY_PORT);
    discoveryUdp.write((const uint8_t*)response, strlen(response));
    discoveryUdp.endPacket();

    Serial.printf("发送发现响应: %s\n", response);
}

void processControlData(uint8_t* data, int length) {
    if (length < 9) return;

    // 解析控制数据
    uint8_t header = data[0];
    leftJoystickX = (int8_t)data[1];
    leftJoystickY = (int8_t)data[2];
    rightJoystickX = (int8_t)data[3];
    rightJoystickY = (int8_t)data[4];

    buttonState = ((uint32_t)data[5]) |
                  ((uint32_t)data[6] << 8) |
                  ((uint32_t)data[7] << 16) |
                  ((uint32_t)data[8] << 24);

    if (header == 0xEE) {
        Serial.println("急停!");
    }
}

void startAPMode() {
    Serial.println("启动 AP 模式...");

    WiFi.mode(WIFI_AP);
    WiFi.softAP(AP_SSID, AP_PASSWORD);

    Serial.print("AP IP 地址: ");
    Serial.println(WiFi.softAPIP());

    setLEDStatus(LED_WIFI_CONNECTED);
}

void setLEDStatus(LEDStatus status) {
    currentLEDStatus = status;
}

void updateLEDStatus() {
    unsigned long now = millis();

    switch (currentLEDStatus) {
        case LED_OFF:
            leds[0] = CRGB::Black;
            break;

        case LED_WIFI_CONNECTING:
            // 白色快闪
            leds[0] = (now % 500 < 100) ? CRGB::White : CRGB::Black;
            break;

        case LED_WIFI_CONNECTED:
            // 绿色呼吸灯
            leds[0] = CRGB::Green;
            leds[0].fadeToBlackBy(128 + (sin(now / 500.0) * 64 + 64));
            break;

        case LED_WIFI_FAILED:
            // 红色慢闪
            leds[0] = (now % 1000 < 300) ? CRGB::Red : CRGB::Black;
            break;

        case LED_CONFIG_MODE:
            // 蓝色快闪
            leds[0] = (now % 500 < 100) ? CRGB::Blue : CRGB::Black;
            break;

        case LED_DATA_RECEIVED:
            // 青色闪一下
            if (now - lastDataTime < 200) {
                leds[0] = CRGB::Cyan;
            } else {
                leds[0] = CRGB::Black;
            }
            break;
    }
    FastLED.show();
}

void blinkLED(int times, int delayMs) {
    for (int i = 0; i < times; i++) {
        leds[0] = CRGB::White;
        FastLED.show();
        delay(delayMs);
        leds[0] = CRGB::Black;
        FastLED.show();
        delay(delayMs);
    }
}
