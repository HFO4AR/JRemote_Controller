// ==================== WiFi UDP 模式 ====================

#ifndef WIFI_MODE_H
#define WIFI_MODE_H

#include <Arduino.h>
#include <WiFi.h>
#include <WiFiUdp.h>
#include <Preferences.h>

// WorkMode 枚举
enum WorkMode {
    MODE_WIFI_UDP,
    MODE_BLE,
    MODE_CONFIG
};

void switchToWiFiMode();
void startWiFiMode();
void connectWiFi(const char* ssid, const char* password = "");
void setupUDP();
void handleData();
void handleDiscovery();
void processControlData(uint8_t* data, int length);
void sendDiscoveryResponse(IPAddress& clientIP);

#endif
