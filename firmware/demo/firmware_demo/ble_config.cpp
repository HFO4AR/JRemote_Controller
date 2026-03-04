// ==================== BLE 配网模式实现 ====================

#include "ble_config.h"
#include "led.h"
#include "wifi.h"
#include "ble_control.h"

// 外部依赖声明
extern Preferences preferences;
extern WorkMode currentMode;
extern WorkMode savedMode;
extern bool isConfigMode;
extern bool bleConfigConnected;
extern BLECharacteristic* pStatusCharacteristic;
extern BLEServer* pConfigServer;

// BLE 配网服务 UUID
#define CONFIG_SERVICE_UUID "0000FFFF-0000-1000-8000-00805F9B34FB"
#define WIFI_SSID_UUID    "0000FF01-0000-1000-8000-00805F9B34FB"
#define WIFI_PASSWORD_UUID "0000FF02-0000-1000-8000-00805F9B34FB"
#define STATUS_UUID       "0000FF03-0000-1000-8000-00805F9B34FB"
#define COMMAND_UUID     "0000FF04-0000-1000-8000-00805F9B34FB"

// ==================== BLE 配网回调（定义在前）====================

class ConfigServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
        bleConfigConnected = true;
        Serial.println("配网：手机已连接");
        updateConfigStatus("已连接");
    }

    void onDisconnect(BLEServer* pServer) {
        bleConfigConnected = false;
        Serial.println("配网：手机已断开");
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

            // 连接 WiFi
            WiFi.mode(WIFI_STA);
            if (savedPassword.length() > 0) {
                WiFi.begin(ssid.c_str(), savedPassword.c_str());
            } else {
                WiFi.begin(ssid.c_str());
            }

            int attempts = 0;
            while (WiFi.status() != WL_CONNECTED && attempts < 30) {
                delay(500);
                attempts++;
            }

            if (WiFi.status() == WL_CONNECTED) {
                updateConfigStatus("WiFi 连接成功!");
                Serial.println("WiFi 连接成功!");
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

            WiFi.mode(WIFI_STA);
            if (password.length() > 0) {
                WiFi.begin(savedSsid.c_str(), password.c_str());
            } else {
                WiFi.begin(savedSsid.c_str());
            }

            int attempts = 0;
            while (WiFi.status() != WL_CONNECTED && attempts < 30) {
                delay(500);
                attempts++;
            }

            if (WiFi.status() == WL_CONNECTED) {
                updateConfigStatus("WiFi 连接成功!");
                Serial.println("WiFi 连接成功!");
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
        } else if (command == "WIFI") {
            // 切换到 WiFi 模式
            savedMode = MODE_WIFI_UDP;
            preferences.putString("workMode", "WIFI");
            updateConfigStatus("切换到 WiFi 模式...");
            delay(500);
            ESP.restart();
        } else if (command == "BLE") {
            // 切换到 BLE 控制模式
            savedMode = MODE_BLE;
            preferences.putString("workMode", "BLE");
            updateConfigStatus("切换到 BLE 模式...");
            delay(500);
            ESP.restart();
        }
    }
};

// ==================== BLE 配网函数 ====================

void switchToConfigMode() {
    Serial.println("切换到配网模式");
    currentMode = MODE_CONFIG;
    startConfigMode();
}

void startConfigMode() {
    isConfigMode = true;
    setLEDStatus(LED_CONFIG_MODE);

    // 停止 WiFi
    WiFi.disconnect();
    delay(100);
    WiFi.mode(WIFI_OFF);

    // 停止 BLE 控制
    stopBLEControlMode();

    BLEDevice::init("ESP32_Config");

    pConfigServer = BLEDevice::createServer();
    pConfigServer->setCallbacks(new ConfigServerCallbacks());

    BLEService *pConfigService = pConfigServer->createService(CONFIG_SERVICE_UUID);

    // SSID 特征
    BLECharacteristic *pWifiSsidCharacteristic = pConfigService->createCharacteristic(
        WIFI_SSID_UUID, BLECharacteristic::PROPERTY_WRITE);
    pWifiSsidCharacteristic->setCallbacks(new SsidCallbacks());
    pWifiSsidCharacteristic->addDescriptor(new BLE2902());

    // 密码特征
    BLECharacteristic *pWifiPasswordCharacteristic = pConfigService->createCharacteristic(
        WIFI_PASSWORD_UUID, BLECharacteristic::PROPERTY_WRITE);
    pWifiPasswordCharacteristic->setCallbacks(new PasswordCallbacks());
    pWifiPasswordCharacteristic->addDescriptor(new BLE2902());

    // 状态特征（通知）
    pStatusCharacteristic = pConfigService->createCharacteristic(
        STATUS_UUID, BLECharacteristic::PROPERTY_NOTIFY);
    pStatusCharacteristic->addDescriptor(new BLE2902());

    // 命令特征
    BLECharacteristic *pCommandCharacteristic = pConfigService->createCharacteristic(
        COMMAND_UUID, BLECharacteristic::PROPERTY_WRITE);
    pCommandCharacteristic->setCallbacks(new CommandCallbacks());
    pCommandCharacteristic->addDescriptor(new BLE2902());

    pConfigService->start();

    // 开始广播
    BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
    pAdvertising->addServiceUUID(CONFIG_SERVICE_UUID);
    pAdvertising->setScanResponse(true);
    BLEDevice::startAdvertising();

    Serial.println("BLE 配网服务已启动");
    updateConfigStatus("等待配置...");
}

void stopConfigMode() {
    isConfigMode = false;
    BLEDevice::deinit(false);
    Serial.println("配网模式已停止");
}

void updateConfigStatus(const char* status) {
    if (pStatusCharacteristic != nullptr && bleConfigConnected) {
        pStatusCharacteristic->setValue(status);
        pStatusCharacteristic->notify();
    }
    Serial.println(status);
}
