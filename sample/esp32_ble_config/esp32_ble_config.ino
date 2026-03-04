/*
 * JRemote Controller - ESP32 BLE 配网示例
 *
 * 功能：
 * - 通过 BLE 接收 WiFi SSID 和密码
 * - 保存到 Flash 重启后自动连接
 * - 配网成功后可切换到 UDP 模式
 *
 * BLE 服务 UUID: 0000FFFF-0000-1000-8000-00805F9B34FB
 * WiFi SSID 特征: 0000FF01-0000-1000-8000-00805F9B34FB
 * WiFi 密码特征: 0000FF02-0000-1000-8000-00805F9B34FB
 * 状态特征:    0000FF03-0000-1000-8000-00805F9B34FB
 */

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <Preferences.h>
#include <WiFi.h>

// ==================== 配置 ====================

// BLE 服务和特征 UUID
#define CONFIG_SERVICE_UUID "0000FFFF-0000-1000-8000-00805F9B34FB"
#define WIFI_SSID_UUID    "0000FF01-0000-1000-8000-00805F9B34FB"
#define WIFI_PASSWORD_UUID "0000FF02-0000-1000-8000-00805F9B34FB"
#define STATUS_UUID       "0000FF03-0000-1000-8000-00805F9B34FB"
#define COMMAND_UUID     "0000FF04-0000-1000-8000-00805F9B34FB"

// 设备名称前缀
const char* DEVICE_PREFIX = "JRemote_";

// ==================== 全局变量 ====================

Preferences preferences;
BLEServer *pServer = nullptr;
BLEService *pConfigService = nullptr;
BLECharacteristic *pWifiSsidCharacteristic = nullptr;
BLECharacteristic *pWifiPasswordCharacteristic = nullptr;
BLECharacteristic *pStatusCharacteristic = nullptr;
BLECharacteristic *pCommandCharacteristic = nullptr;

bool deviceConnected = false;
bool isConfigMode = true;  // 配网模式标志
unsigned long lastStatusTime = 0;

// WiFi 状态
String savedSsid = "";
String savedPassword = "";

// ==================== 函数声明 ====================

void setup();
void loop();
void startConfigMode();
void startNormalMode();
void saveWiFiConfig(const String& ssid, const String& password);
bool loadWiFiConfig();
void connectWiFi();
void updateStatus(const String& status);

// ==================== BLE 回调 ====================

class ServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
        deviceConnected = true;
        Serial.println("手机已连接");
        updateStatus("已连接");
    }

    void onDisconnect(BLEServer* pServer) {
        deviceConnected = false;
        Serial.println("手机已断开");
        delay(500); // 等待蓝牙重置
        pServer->startAdvertising(); // 重新开始广播
    }
};

class SsidCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
        String ssid = pCharacteristic->getValue().c_str();
        Serial.printf("收到 SSID: %s\n", ssid.c_str());
        savedSsid = ssid;

        // 保存到 Flash
        preferences.putString("ssid", ssid);
        updateStatus("SSID 已保存");
    }
};

class PasswordCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
        String password = pCharacteristic->getValue().c_str();
        Serial.printf("收到密码: %s\n", password.c_str());
        savedPassword = password;

        // 保存到 Flash
        preferences.putString("password", password);
        updateStatus("密码已保存");

        // 如果 SSID 也收到了，尝试连接 WiFi
        if (savedSsid.length() > 0) {
            updateStatus("正在连接 WiFi...");
            connectWiFi();
        }
    }
};

class CommandCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
        String command = pCharacteristic->getValue().c_str();
        Serial.printf("收到命令: %s\n", command.c_str());

        if (command == "RESTART") {
            updateStatus("重启中...");
            delay(1000);
            ESP.restart();
        } else if (command == "RESET") {
            preferences.clear();
            updateStatus("配置已清除");
        } else if (command == "START_UDP") {
            updateStatus("启动 UDP 模式");
            isConfigMode = false;
        }
    }
};

// ==================== 实现 ====================

void setup() {
    Serial.begin(115200);
    delay(100);

    Serial.println();
    Serial.println("=== ESP32 BLE 配网 ===");

    // 初始化 Preferences
    preferences.begin("wifi-config", false);

    // 加载保存的 WiFi 配置
    if (loadWiFiConfig()) {
        Serial.println("找到保存的 WiFi 配置");
        Serial.printf("SSID: %s\n", savedSsid.c_str());

        // 尝试连接 WiFi
        connectWiFi();
    } else {
        Serial.println("未找到 WiFi 配置，进入配网模式");
        startConfigMode();
    }
}

void loop() {
    // 如果已连接 WiFi 且不在配网模式，不做任何事
    if (!isConfigMode && WiFi.status() == WL_CONNECTED) {
        delay(100);
        return;
    }

    // 配网模式下定期更新状态
    if (isConfigMode && deviceConnected && millis() - lastStatusTime > 2000) {
        if (savedSsid.length() > 0) {
            if (WiFi.status() == WL_CONNECTED) {
                updateStatus("WiFi 已连接!");
            } else {
                updateStatus("WiFi 未连接");
            }
        } else {
            updateStatus("等待配置");
        }
        lastStatusTime = millis();
    }

    delay(100);
}

void startConfigMode() {
    isConfigMode = true;

    // 创建 BLE 设备
    BLEDevice::init("ESP32_Config");

    // 创建 BLE 服务器
    pServer = BLEDevice::createServer();
    pServer->setCallbacks(new ServerCallbacks());

    // 创建配网服务
    pConfigService = pServer->createService(CONFIG_SERVICE_UUID);

    // WiFi SSID 特征
    pWifiSsidCharacteristic = pConfigService->createCharacteristic(
        WIFI_SSID_UUID,
        BLECharacteristic::PROPERTY_WRITE
    );
    pWifiSsidCharacteristic->setCallbacks(new SsidCallbacks());
    pWifiSsidCharacteristic->addDescriptor(new BLE2902());

    // WiFi 密码特征
    pWifiPasswordCharacteristic = pConfigService->createCharacteristic(
        WIFI_PASSWORD_UUID,
        BLECharacteristic::PROPERTY_WRITE
    );
    pWifiPasswordCharacteristic->setCallbacks(new PasswordCallbacks());
    pWifiPasswordCharacteristic->addDescriptor(new BLE2902());

    // 状态特征（通知手机）
    pStatusCharacteristic = pConfigService->createCharacteristic(
        STATUS_UUID,
        BLECharacteristic::PROPERTY_NOTIFY
    );
    pStatusCharacteristic->addDescriptor(new BLE2902());

    // 命令特征
    pCommandCharacteristic = pConfigService->createCharacteristic(
        COMMAND_UUID,
        BLECharacteristic::PROPERTY_WRITE
    );
    pCommandCharacteristic->setCallbacks(new CommandCallbacks());
    pCommandCharacteristic->addDescriptor(new BLE2902());

    // 启动服务
    pConfigService->start();

    // 开始广播
    BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
    pAdvertising->addServiceUUID(CONFIG_SERVICE_UUID);
    pAdvertising->setScanResponse(true);
    pAdvertising->setMinPreferred(0x06);
    pAdvertising->setMinPreferred(0x12);
    BLEDevice::startAdvertising();

    Serial.println("BLE 配网服务已启动");
    Serial.println("请使用 Android 应用连接并配置 WiFi");
}

void startNormalMode() {
    isConfigMode = false;
    // 停止 BLE 广播
    BLEDevice::getAdvertising()->stop();
    Serial.println("退出配网模式");
}

bool loadWiFiConfig() {
    savedSsid = preferences.getString("ssid", "");
    savedPassword = preferences.getString("password", "");
    return savedSsid.length() > 0;
}

void saveWiFiConfig(const String& ssid, const String& password) {
    preferences.putString("ssid", ssid);
    preferences.putString("password", password);
    savedSsid = ssid;
    savedPassword = password;
    Serial.println("WiFi 配置已保存");
}

void connectWiFi() {
    if (savedSsid.length() == 0) {
        Serial.println("SSID 为空，无法连接");
        return;
    }

    Serial.printf("正在连接 WiFi: %s\n", savedSsid.c_str());

    WiFi.mode(WIFI_STA);
    WiFi.begin(savedSsid.c_str(), savedPassword.c_str());

    int attempts = 0;
    while (WiFi.status() != WL_CONNECTED && attempts < 30) {
        delay(500);
        Serial.print(".");
        attempts++;
    }

    if (WiFi.status() == WL_CONNECTED) {
        Serial.println();
        Serial.print("WiFi 连接成功! IP: ");
        Serial.println(WiFi.localIP());
        updateStatus("WiFi 连接成功!");
    } else {
        Serial.println();
        Serial.println("WiFi 连接失败!");
        updateStatus("WiFi 连接失败");
    }
}

void updateStatus(const String& status) {
    if (pStatusCharacteristic != nullptr && deviceConnected) {
        pStatusCharacteristic->setValue(status.c_str());
        pStatusCharacteristic->notify();
    }
    Serial.println("状态: " + status);
}
