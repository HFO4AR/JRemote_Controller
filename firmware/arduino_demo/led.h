// ==================== LED 控制 ====================

#ifndef LED_H
#define LED_H

#include <Arduino.h>
#include <FastLED.h>

// LED 状态枚举
enum LEDStatus {
    LED_OFF,
    LED_WIFI_CONNECTING,
    LED_WIFI_CONNECTED,
    LED_WIFI_FAILED,
    LED_BLE_CONNECTING,
    LED_BLE_CONNECTED,
    LED_CONFIG_MODE,
    LED_DATA_RECEIVED
};

extern LEDStatus currentLEDStatus;
extern unsigned long lastDataTime;
extern CRGB leds[1];

void setLEDStatus(LEDStatus status);
void updateLEDStatus();
void initLED();

#endif
