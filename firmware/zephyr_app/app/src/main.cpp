/*
 * JRemote Controller - Zephyr Firmware
 * Target: ESP32
 * Language: C++17
 */

#include <zephyr/kernel.h>
#include <zephyr/device.h>
#include <zephyr/devicetree.h>
#include <zephyr/logging/log.h>

LOG_MODULE_REGISTER(jremote);

int main(void)
{
    LOG_INF("JRemote Controller Zephyr Firmware v0.1.0");
    LOG_INF("Target: ESP32");

    /* TODO: Add WiFi initialization */
    /* TODO: Add BLE initialization */
    /* TODO: Add UDP server */
    /* TODO: Add control data handling */

    return 0;
}
