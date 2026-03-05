/*
 * JRemote Controller - Zephyr Firmware
 * Target: ESP32
 * Language: C++17
 */

#include <zephyr/kernel.h>
#include <zephyr/device.h>
#include <zephyr/devicetree.h>
#include <zephyr/logging/log.h>

#include "ws2812.h"

LOG_MODULE_REGISTER(jremote);

// Get LED strip device from device tree
static const struct device* led_strip_dev = DEVICE_DT_GET(DT_ALIAS(led_strip));

int main(void)
{
    LOG_INF("JRemote Controller Zephyr Firmware v0.1.0");
    LOG_INF("Target: ESP32");

    // Create LED instance
    Ws2812 led(led_strip_dev);

    // Initialize LED
    if (!led.Init()) {
        LOG_ERR("LED initialization failed");
        return 0;
    }

    LOG_INF("LED initialized: %s", led_strip_dev->name);
    led.SetColor(255, 0, 0);

    // Main loop - cycle through colors
    while (true) {
        // Red

        led.Show();

        k_sleep(K_MSEC(500));
    }

    return 0;
}
