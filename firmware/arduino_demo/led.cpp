// ==================== LED 控制实现 ====================

#include "led.h"

void setLEDStatus(LEDStatus status) {
    currentLEDStatus = status;
}

void updateLEDStatus() {
    static unsigned long lastUpdate = 0;

    if (millis() - lastUpdate < 50) return;
    lastUpdate = millis();

    unsigned long now = millis();

    switch (currentLEDStatus) {
        case LED_OFF:
            leds[0] = CRGB::Black;
            break;
        case LED_WIFI_CONNECTING:
            leds[0] = (now % 500 < 100) ? CRGB::Blue : CRGB::Black;
            break;
        case LED_WIFI_CONNECTED:
            leds[0] = CRGB::Green;
            break;
        case LED_WIFI_FAILED:
            leds[0] = (now % 1000 < 300) ? CRGB::Red : CRGB::Black;
            break;
        case LED_BLE_CONNECTING:
            leds[0] = (now % 500 < 100) ? CRGB::Purple : CRGB::Black;
            break;
        case LED_BLE_CONNECTED:
            leds[0] = CRGB::Purple;
            break;
        case LED_CONFIG_MODE:
            leds[0] = (now % 500 < 100) ? CRGB::Yellow : CRGB::Black;
            break;
        case LED_DATA_RECEIVED:
            if (now - lastDataTime < 200) {
                leds[0] = CRGB::White;
            } else {
                leds[0] = CRGB::Black;
            }
            break;
    }
    FastLED.show();
}

void initLED() {
    // LED 已在 firmware_demo.ino 中初始化
}
