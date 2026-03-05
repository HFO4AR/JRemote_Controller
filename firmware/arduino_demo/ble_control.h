// ==================== BLE 控制模式 ====================

#ifndef BLE_CONTROL_H
#define BLE_CONTROL_H

#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

void switchToBLEMode();
void startBLEControlMode();
void stopBLEControlMode();

#endif
