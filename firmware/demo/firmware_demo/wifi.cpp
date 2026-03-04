// ==================== WiFi UDP 模式实现 ====================

#include "globals.h"
#include "wifi.h"
#include "led.h"
#include "webserver.h"
#include "mcu.h"

// 外部依赖声明
extern Preferences preferences;
extern WiFiUDP dataUdp;
extern WiFiUDP discoveryUdp;
extern String deviceName;
extern IPAddress phoneIP;
extern bool isConnected;
extern unsigned long lastDataTime;
extern int leftJoystickX, leftJoystickY;
extern int rightJoystickX, rightJoystickY;
extern uint32_t buttonState;
extern WorkMode currentMode;

extern void stopBLEControlMode();
extern void stopConfigMode();
extern void switchToConfigMode();
extern void startWebServer();

void switchToWiFiMode() {
    Serial.println("切换到 WiFi UDP 模式");

    // 停止 BLE
    stopBLEControlMode();
    stopConfigMode();

    currentMode = MODE_WIFI_UDP;
    startWiFiMode();
}

void startWiFiMode() {
    // 获取保存的 WiFi 配置
    String savedSsid = preferences.getString("ssid", "");
    String savedPassword = preferences.getString("password", "");

    if (savedSsid.length() == 0) {
        Serial.println("没有保存的 WiFi 配置，进入配网模式");
        switchToConfigMode();
        return;
    }

    connectWiFi(savedSsid.c_str(), savedPassword.c_str());
    setupUDP();
}

void connectWiFi(const char* ssid, const char* password) {
    Serial.printf("连接 WiFi: %s\n", ssid);

    setLEDStatus(LED_WIFI_CONNECTING);
    WiFi.mode(WIFI_STA);

    bool hasPassword = (password != nullptr && strlen(password) > 0);
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
        Serial.print("WiFi 已连接! IP: ");
        Serial.println(WiFi.localIP());
        setLEDStatus(LED_WIFI_CONNECTED);
        isConnected = true;
    } else {
        Serial.println();
        Serial.println("WiFi 连接失败!");
        setLEDStatus(LED_WIFI_FAILED);
        isConnected = false;
    }
}

void setupUDP() {
    const int DATA_PORT = 1034;
    const int DISCOVERY_PORT = 1035;

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

    // 启动 Web 服务器
    startWebServer();
}

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

            // 发送响应
            dataUdp.beginPacket(remoteIP, remotePort);
            dataUdp.write(0x50);
            dataUdp.endPacket();

            if (len == 1 && packetBuffer[0] == 0x70) {
                Serial.println("收到 Ping");
            } else {
                processControlData(packetBuffer, len);
                lastDataTime = millis();
                setLEDStatus(LED_DATA_RECEIVED);
                isConnected = true;

                Serial.printf("收到控制数据: %d bytes\n", len);
            }
        }
    }
}

void handleDiscovery() {
    const int DISCOVERY_PORT = 1035;
    int packetSize = discoveryUdp.parsePacket();

    if (packetSize > 0) {
        char packetBuffer[64];
        int len = discoveryUdp.read(packetBuffer, sizeof(packetBuffer) - 1);

        if (len > 0) {
            packetBuffer[len] = 0;
            Serial.printf("收到发现请求: %s\n", packetBuffer);

            if (String(packetBuffer).startsWith("JREMOTE_DISCOVER")) {
                IPAddress clientIP = discoveryUdp.remoteIP();
                sendDiscoveryResponse(clientIP);
            }
        }
    }
}

void sendDiscoveryResponse(IPAddress& clientIP) {
    const int DATA_PORT = 1034;
    char response[128];
    IPAddress localIP = WiFi.localIP();

    snprintf(response, sizeof(response), "JREMOTE:%s:%d.%d.%d.%d:%d",
             deviceName.c_str(),
             localIP[0], localIP[1], localIP[2], localIP[3],
             DATA_PORT);

    const int DISCOVERY_PORT = 1035;
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

    // 急停检测
    if (header == 0xEE) {
        Serial.println("急停!");
    }

    // 发送给 MCU
    sendToMCU(data, length);
}
