// ==================== MCU 通信 ====================

#ifndef MCU_H
#define MCU_H

#include <Arduino.h>

void sendToMCU(uint8_t* data, int length);
void handleMCUMessage(String message);

#endif
