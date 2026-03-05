// ==================== BLE 配网模式 ====================

#ifndef BLE_CONFIG_H
#define BLE_CONFIG_H

#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <Preferences.h>

void switchToConfigMode();
void startConfigMode();
void stopConfigMode();
void updateConfigStatus(const char* status);

#endif
