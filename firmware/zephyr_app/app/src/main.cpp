/*
 * JRemote Controller - Zephyr Firmware
 * Target: ESP32
 * Language: C++17
 */

#include <zephyr/kernel.h>
#include <zephyr/device.h>
#include <zephyr/devicetree.h>
#include <zephyr/logging/log.h>

#include "ws2812_led.h"
#include "ble.h"

LOG_MODULE_REGISTER(jremote);

// BLE 线程栈
K_THREAD_STACK_DEFINE(ble_stack_area, 2048);

// 全局 BLE 实例 (传入线程栈)
static Ble g_ble(ble_stack_area, K_THREAD_STACK_SIZEOF(ble_stack_area));

// WS2812 LED 设备
static const struct device* led_strip_dev = DEVICE_DT_GET(DT_ALIAS(led_strip));

// BLE 数据接收回调
static void OnBleDataReceived(const uint8_t* data, uint16_t len) {
    LOG_INF("BLE data received: %d bytes", len);
    // TODO: 处理控制数据
    // 数据格式: 9 字节
    // data[0]: 帧头 (0xAA 正常, 0xEE 急停)
    // data[1-2]: 左摇杆 X, Y
    // data[3-4]: 右摇杆 X, Y
    // data[5-8]: 按钮状态
}

int main(void)
{
    LOG_INF("JRemote Controller Zephyr Firmware v0.1.0");
    LOG_INF("Target: ESP32");

    // 初始化 WS2812 LED
    Ws2812Led led(led_strip_dev);
    if (!led.Init()) {
        LOG_ERR("WS2812 LED initialization failed");
    } else {
        LOG_INF("WS2812 LED initialized: %s", led_strip_dev->name);
    }

    // 设置 LED 为蓝色表示 BLE 初始化中
    led.SetColor(0, 0, 255);
    led.Show();

    // 初始化 BLE (会在内部创建线程)
    if (g_ble.Init() != 0) {
        LOG_ERR("BLE initialization failed");
    } else {
        g_ble.SetReceiveCallback(OnBleDataReceived);
        LOG_INF("BLE thread started");
    }

    // 主循环
    while (true) {
        // 根据连接状态改变 LED 颜色
        if (g_ble.IsConnected()) {
            // 已连接 - 绿色
            led.SetColor(0, 255, 0);
        } else {
            // 未连接 - 蓝色
            led.SetColor(0, 0, 255);
        }
        led.Show();

        k_sleep(K_MSEC(1000));
    }

    return 0;
}
