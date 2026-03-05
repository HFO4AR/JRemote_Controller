// ==================== 按钮处理实现 ====================

#include "button.h"
#include "led.h"
#include "wifi.h"
#include "ble_control.h"
#include "ble_config.h"

// 外部变量声明
extern int CONFIG_BUTTON_PIN;
extern WorkMode currentMode;
extern WorkMode savedMode;

// 按钮变量
static unsigned long lastButtonPress = 0;
static int buttonPressCount = 0;

void handleButton() {
    static bool lastButtonState = HIGH;
    bool currentButtonState = digitalRead(CONFIG_BUTTON_PIN);

    // 检测按钮按下
    if (currentButtonState == LOW && lastButtonState == HIGH) {
        unsigned long pressTime = millis();

        // 如果距离上次按下超过 500ms，重新计数
        if (pressTime - lastButtonPress > 500) {
            buttonPressCount = 0;
        }

        buttonPressCount++;
        lastButtonPress = pressTime;

        Serial.printf("按钮按下: 第 %d 次\n", buttonPressCount);
    }

    // 检测双击（两次按下间隔小于 500ms）
    if (buttonPressCount >= 2 && millis() - lastButtonPress > 500) {
        Serial.println("检测到双击！切换模式");

        if (currentMode == MODE_CONFIG) {
            // 退出配网模式，恢复之前的模式
            stopConfigMode();
            if (savedMode == MODE_WIFI_UDP) {
                switchToWiFiMode();
            } else {
                switchToBLEMode();
            }
        } else if (currentMode == MODE_WIFI_UDP) {
            // 切换到 BLE 模式
            savedMode = MODE_WIFI_UDP;
            switchToBLEMode();
        } else if (currentMode == MODE_BLE) {
            // 切换到 WiFi 模式
            savedMode = MODE_BLE;
            switchToWiFiMode();
        }

        buttonPressCount = 0;
    }

    lastButtonState = currentButtonState;
}
