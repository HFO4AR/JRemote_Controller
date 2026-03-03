/*
 * JRemote Controller - ESP32 UDP 接收端示例
 * 支持 AP 模式和 Station 模式
 *
 * 引脚配置:
 * - LED_BUILTIN: 状态指示灯
 *
 * 通信协议:
 * - 数据端口: 1034
 * - 发现端口: 1035
 * - 发现响应格式: JREMOTE:{设备名称}:{IP}:{端口}
 * - 控制数据: 9 字节 (与 BLE 相同)
 *   - 字节 0: 帧头 (0xAA 正常, 0xEE 急停)
 *   - 字节 1-2: 左摇杆 X, Y (-127 ~ 127)
 *   - 字节 3-4: 右摇杆 X, Y (-127 ~ 127)
 *   - 字节 5-8: 按钮状态位掩码
 */

#include <WiFi.h>
#include <WiFiUDP.h>

// ==================== 配置 ====================

// 选择运行模式: true = AP 模式, false = Station 模式
const bool USE_AP_MODE = true;

// AP 模式配置
const char* AP_SSID = "JRemote_ESP32";
const char* AP_PASSWORD = "12345678";

// Station 模式配置 (请根据您的网络修改)
const char* STA_SSID = "Your_WiFi_SSID";
const char* STA_PASSWORD = "Your_WiFi_Password";

// 设备名称
const char* DEVICE_NAME = "ESP32_JRemote";

// UDP 端口配置
const int DATA_PORT = 1034;
const int DISCOVERY_PORT = 1035;

// ==================== 全局变量 ====================

WiFiUDP dataUdp;
WiFiUDP discoveryUdp;

bool isConnected = false;
unsigned long lastDataTime = 0;

// 摇杆数据
int leftJoystickX = 0;
int leftJoystickY = 0;
int rightJoystickX = 0;
int rightJoystickY = 0;

// 按钮状态 (32 位)
uint32_t buttonState = 0;

// LED 引脚
const int LED_PIN = LED_BUILTIN;

// ==================== 函数声明 ====================

void setup();
void loop();
void startAPMode();
void startStationMode();
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

    pinMode(LED_PIN, OUTPUT);
    digitalWrite(LED_PIN, LOW);

    Serial.println();
    Serial.println("=== JRemote Controller ESP32 UDP Receiver ===");
    Serial.printf("设备名称: %s\n", DEVICE_NAME);

    // 根据模式启动 WiFi
    if (USE_AP_MODE) {
        startAPMode();
    } else {
        startStationMode();
    }

    // 设置 UDP
    setupUDP();

    Serial.println("=== 初始化完成 ===");
}

void loop() {
    // 处理 UDP 数据
    handleData();

    // 处理设备发现请求
    handleDiscovery();

    // 检查连接超时 (5秒无数据视为断开)
    if (isConnected && (millis() - lastDataTime > 5000)) {
        isConnected = false;
        digitalWrite(LED_PIN, LOW);
        Serial.println("连接超时");
    }

    delay(1);
}

void startAPMode() {
    Serial.println("启动 AP 模式...");

    WiFi.mode(WIFI_AP);
    WiFi.softAP(AP_SSID, AP_PASSWORD);

    IPAddress IP = WiFi.softAPIP();
    Serial.print("AP IP 地址: ");
    Serial.println(IP);

    // 等待 AP 启动
    delay(100);
}

void startStationMode() {
    Serial.println("启动 Station 模式...");
    Serial.printf("连接 WiFi: %s\n", STA_SSID);

    WiFi.mode(WIFI_STA);
    WiFi.begin(STA_SSID, STA_PASSWORD);

    int attempts = 0;
    while (WiFi.status() != WL_CONNECTED && attempts < 30) {
        delay(500);
        Serial.print(".");
        attempts++;
    }

    if (WiFi.status() == WL_CONNECTED) {
        Serial.println();
        Serial.print("Station IP 地址: ");
        Serial.println(WiFi.localIP());
    } else {
        Serial.println();
        Serial.println("WiFi 连接失败!");
    }
}

void setupUDP() {
    // 绑定数据端口
    if (dataUdp.begin(DATA_PORT)) {
        Serial.printf("数据端口 %d 绑定成功\n", DATA_PORT);
    } else {
        Serial.printf("数据端口 %d 绑定失败!\n", DATA_PORT);
    }

    // 绑定发现端口
    if (discoveryUdp.begin(DISCOVERY_PORT)) {
        Serial.printf("发现端口 %d 绑定成功\n", DISCOVERY_PORT);
    } else {
        Serial.printf("发现端口 %d 绑定失败!\n", DISCOVERY_PORT);
    }
}

void handleData() {
    int packetSize = dataUdp.parsePacket();

    if (packetSize > 0) {
        // 收到数据
        IPAddress remoteIP = dataUdp.remoteIP();
        int remotePort = dataUdp.remotePort();

        uint8_t packetBuffer[64];
        int len = dataUdp.read(packetBuffer, sizeof(packetBuffer) - 1);

        if (len > 0) {
            packetBuffer[len] = 0;

            // 检查是否是 ping 请求 (0x70)
            if (len == 1 && packetBuffer[0] == 0x70) {
                // 响应 ping
                dataUdp.beginPacket(remoteIP, remotePort);
                dataUdp.write(0x50);  // 'P'
                dataUdp.endPacket();
                Serial.println("收到 Ping, 发送响应");
            } else {
                // 处理控制数据
                processControlData(packetBuffer, len);
                isConnected = true;
                lastDataTime = millis();
                digitalWrite(LED_PIN, HIGH);

                Serial.printf("收到控制数据: %d bytes\n", len);
                Serial.printf("左摇杆: X=%d, Y=%d\n", leftJoystickX, leftJoystickY);
                Serial.printf("右摇杆: X=%d, Y=%d\n", rightJoystickX, rightJoystickY);
                Serial.printf("按钮状态: 0x%08X\n", buttonState);
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

            // 检查是否是发现消息
            if (String(packetBuffer).startsWith("JREMOTE_DISCOVER")) {
                IPAddress clientIP = discoveryUdp.remoteIP();
                int clientPort = discoveryUdp.remotePort();
                sendDiscoveryResponse(clientIP);
            }
        }
    }
}

void sendDiscoveryResponse(IPAddress& clientIP) {
    // 响应格式: JREMOTE:{设备名称}:{IP}:{端口}
    char response[128];
    IPAddress localIP = WiFi.localIP();

    // 如果是 AP 模式,使用 softAPIP
    if (USE_AP_MODE) {
        localIP = WiFi.softAPIP();
    }

    snprintf(response, sizeof(response), "JREMOTE:%s:%d.%d.%d.%d:%d",
             DEVICE_NAME,
             localIP[0], localIP[1], localIP[2], localIP[3],
             DATA_PORT);

    discoveryUdp.beginPacket(clientIP, DISCOVERY_PORT);
    discoveryUdp.write(response);
    discoveryUdp.endPacket();

    Serial.printf("发送发现响应: %s\n", response);
}

void processControlData(uint8_t* data, int length) {
    // 数据格式 (9 字节):
    // 字节 0: 帧头 (0xAA 正常, 0xEE 急停)
    // 字节 1-2: 左摇杆 X, Y
    // 字节 3-4: 右摇杆 X, Y
    // 字节 5-8: 按钮状态

    if (length < 9) {
        Serial.printf("数据长度不足: %d\n", length);
        return;
    }

    // 检查帧头
    uint8_t header = data[0];

    if (header == 0xEE) {
        // 急停指令
        Serial.println("收到急停指令!");
        leftJoystickX = 0;
        leftJoystickY = 0;
        rightJoystickX = 0;
        rightJoystickY = 0;
        buttonState = 0;

        // 这里添加急停处理逻辑
        // stopAllMotors();
        return;
    }

    if (header != 0xAA) {
        Serial.printf("未知帧头: 0x%02X\n", header);
        return;
    }

    // 解析摇杆数据 (有符号整数)
    leftJoystickX = (int8_t)data[1];
    leftJoystickY = (int8_t)data[2];
    rightJoystickX = (int8_t)data[3];
    rightJoystickY = (int8_t)data[4];

    // 解析按钮状态 (4 字节, 32 位)
    buttonState = ((uint32_t)data[5] << 0) |
                  ((uint32_t)data[6] << 8) |
                  ((uint32_t)data[7] << 16) |
                  ((uint32_t)data[8] << 24);

    // 在这里添加控制逻辑
    // 例如:
    // - 控制电机
    // - 控制舵机
    // - 读取传感器数据等
}

void blinkLED(int times, int delayMs) {
    for (int i = 0; i < times; i++) {
        digitalWrite(LED_PIN, HIGH);
        delay(delayMs);
        digitalWrite(LED_PIN, LOW);
        delay(delayMs);
    }
}
