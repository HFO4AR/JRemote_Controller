// ==================== BLE 控制模式实现 ====================

#include "ble_control.h"
#include "led.h"
#include "mcu.h"
#include "wifi.h"

// 外部依赖声明
extern String deviceName;
extern bool isConnected;
extern bool bleControlConnected;
extern WorkMode currentMode;
extern BLECharacteristic* pControlTxCharacteristic;
extern BLECharacteristic* pControlRxCharacteristic;
extern BLEServer* pControlServer;
extern unsigned long lastDataTime;

extern void stopConfigMode();
extern void processControlData(uint8_t* data, int length);

// BLE 控制服务 UUID
#define CONTROL_SERVICE_UUID "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CONTROL_TX_UUID    "beb5483e-36e1-4688-b7f5-ea07361b26a8"
#define CONTROL_RX_UUID   "6e400002-b5a3-f393-e0a9-e50e24dcca9e"

// ==================== BLE 控制回调（定义在前）====================

class ControlServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
        bleControlConnected = true;
        isConnected = true;
        Serial.println("控制：手机已连接");
        setLEDStatus(LED_BLE_CONNECTED);

        // 停止广播
        BLEDevice::getAdvertising()->stop();
    }

    void onDisconnect(BLEServer* pServer) {
        bleControlConnected = false;
        isConnected = false;
        Serial.println("控制：手机已断开");
        setLEDStatus(LED_BLE_CONNECTING);

        delay(500);
        // 重新开始广播
        BLEDevice::getAdvertising()->start();
    }
};

class ControlCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
        String dataStr = pCharacteristic->getValue().c_str();

        if (dataStr.length() > 0) {
            uint8_t* bytes = (uint8_t*)dataStr.c_str();
            int len = dataStr.length();

            Serial.printf("收到 BLE 数据: %d bytes\n", len);

            // 急停检测
            if (len > 0 && bytes[0] == 0xEE) {
                Serial.println("急停!");
            }

            // 处理控制数据
            if (len >= 9) {
                processControlData(bytes, len);
            }

            // 发送给 MCU
            sendToMCU(bytes, len);

            lastDataTime = millis();
            setLEDStatus(LED_DATA_RECEIVED);

            // 发送响应
            if (pControlTxCharacteristic != nullptr && bleControlConnected) {
                uint8_t response[] = {0x50};
                pControlTxCharacteristic->setValue(response, 1);
                pControlTxCharacteristic->notify();
            }
        }
    }
};

// ==================== BLE 控制函数 ====================

void switchToBLEMode() {
    Serial.println("切换到 BLE 控制模式");

    // 停止 WiFi
    WiFi.disconnect();
    delay(100);
    WiFi.mode(WIFI_OFF);

    // 停止配网
    stopConfigMode();

    currentMode = MODE_BLE;
    startBLEControlMode();
}

void startBLEControlMode() {
    setLEDStatus(LED_BLE_CONNECTING);

    BLEDevice::init(deviceName.c_str());

    pControlServer = BLEDevice::createServer();
    pControlServer->setCallbacks(new ControlServerCallbacks());

    BLEService *pControlService = pControlServer->createService(CONTROL_SERVICE_UUID);

    // TX 特征（发送数据给手机）
    pControlTxCharacteristic = pControlService->createCharacteristic(
        CONTROL_TX_UUID, BLECharacteristic::PROPERTY_NOTIFY);
    pControlTxCharacteristic->addDescriptor(new BLE2902());

    // RX 特征（接收手机数据）
    pControlRxCharacteristic = pControlService->createCharacteristic(
        CONTROL_RX_UUID, BLECharacteristic::PROPERTY_WRITE);
    pControlRxCharacteristic->setCallbacks(new ControlCallbacks());
    pControlRxCharacteristic->addDescriptor(new BLE2902());

    pControlService->start();

    // 开始广播
    BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
    pAdvertising->addServiceUUID(CONTROL_SERVICE_UUID);
    pAdvertising->setScanResponse(true);
    BLEDevice::startAdvertising();

    Serial.println("BLE 控制服务已启动");
}

void stopBLEControlMode() {
    if (pControlServer) {
        BLEDevice::deinit(false);
        pControlServer = nullptr;
    }
    bleControlConnected = false;
    Serial.println("BLE 控制服务已停止");
}
