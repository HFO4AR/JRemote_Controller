// ==================== MCU 通信 ====================

#include "globals.h"
#include "mcu.h"
#include "led.h"
#include "webserver.h"

// 外部变量声明
extern unsigned long lastDataTime;

// 外部函数声明
extern void broadcastWebSocket(const char* json);

void sendToMCU(uint8_t* data, int length) {
    // 通过 UART 发送给 MCU
    Serial1.write(data, length);

    // 同时通过串口调试输出
    Serial.print("发送到 MCU: ");
    for (int i = 0; i < length; i++) {
        Serial.printf("%02X ", data[i]);
    }
    Serial.println();
}

void handleMCUMessage(String message) {
    Serial.printf("收到 MCU 消息: %s\n", message.c_str());

    // 解析 MCU 消息
    // 格式: SENSOR:temp:25.5:hum:60.0
    // 格式: LOG:info:message
    // 格式: DEBUG:message

    if (message.startsWith("SENSOR:")) {
        // 传感器数据，发送到 WebSocket
        char json[256];
        snprintf(json, sizeof(json), "{\"type\":\"sensor\",\"data\":\"%s\"}", message.c_str() + 7);
        broadcastWebSocket(json);

        // 同时更新 LED 状态表示有数据收到
        lastDataTime = millis();
        setLEDStatus(LED_DATA_RECEIVED);
    }
    else if (message.startsWith("LOG:")) {
        // 日志消息
        String logMsg = message.substring(4);
        char json[256];
        snprintf(json, sizeof(json), "{\"type\":\"log\",\"level\":\"info\",\"message\":\"MCU: %s\"}", logMsg.c_str());
        broadcastWebSocket(json);
    }
    else if (message.startsWith("DEBUG:")) {
        // 调试消息
        String debugMsg = message.substring(6);
        char json[256];
        snprintf(json, sizeof(json), "{\"type\":\"log\",\"level\":\"info\",\"message\":\"MCU: %s\"}", debugMsg.c_str());
        broadcastWebSocket(json);
    }
    else {
        // 未知消息，也发送到 WebSocket
        char json[256];
        snprintf(json, sizeof(json), "{\"type\":\"log\",\"level\":\"info\",\"message\":\"MCU: %s\"}", message.c_str());
        broadcastWebSocket(json);
    }
}
