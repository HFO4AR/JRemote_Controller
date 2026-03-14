// JRemote Controller - Zephyr Firmware
// Target: ESP32
// Language: C++17

#include <zephyr/kernel.h>
#include <zephyr/device.h>
#include <zephyr/devicetree.h>
#include <zephyr/logging/log.h>

#include "ws2812_led.h"
#include "ble.h"
#include "control_data.h"
#include "serial.h"

LOG_MODULE_REGISTER(jremote);

// BLE 线程栈
K_THREAD_STACK_DEFINE(ble_stack_area, 2048);

// 全局 BLE 实例 (传入线程栈)
static Ble g_ble(ble_stack_area, K_THREAD_STACK_SIZEOF(ble_stack_area));

// WS2812 LED 设备
static const struct device* led_strip_dev = DEVICE_DT_GET(DT_ALIAS(led_strip));

// MCU UART 设备
static const struct device* mcu_uart_dev = DEVICE_DT_GET(DT_NODELABEL(uart1));

// Serial 实例 (RX ring buffer 512 bytes, DMA buffer 256 bytes)
static Serial<512, 256> g_serial(mcu_uart_dev, kInterrupt, 115200);

// 数据处理器
static ControlDataHandler g_control_handler;

// 控制数据接收回调
static void OnControlDataReceived(const ControlData& data) {
	if (data.header == kFrameHeaderEmergency) {
		LOG_INF("Emergency stop received!");
		// TODO: 执行急停逻辑
	} else {
		LOG_INF("Control data: L=(%d,%d) R=(%d,%d) buttons=0x%08X",
				data.left_x, data.left_y, data.right_x, data.right_y, data.buttons);
	}

	// 转发控制数据到 MCU (通过 UART)
	const uint8_t* raw_data = reinterpret_cast<const uint8_t*>(&data);
	g_serial.Write(raw_data, sizeof(ControlData));
}

// BLE 数据接收回调
static void OnBleDataReceived(const uint8_t* data, uint16_t len) {
	g_control_handler.ProcessData(data, len);
}

int main(void)
{
	LOG_INF("JRemote Controller Zephyr Firmware v0.1.0");
	LOG_INF("Target: ESP32");

	// 初始化数据处理器
	g_control_handler.SetCallback(OnControlDataReceived);
	g_control_handler.SetConnectionType(ConnectionType::BLE);

	// 初始化 WS2812 LED
	Ws2812Led led(led_strip_dev);
	if (!led.Init()) {
		LOG_ERR("WS2812 LED initialization failed");
	} else {
		LOG_INF("WS2812 LED initialized: %s", led_strip_dev->name);
	}

	// 设置 LED 为蓝色表示 BLE 初始化中
	led.SetColor(0, 0, 255);

	// 初始化 Serial
	if (!g_serial.IsReady()) {
		LOG_ERR("Serial device not ready");
	} else {
		LOG_INF("Serial initialized");
	}

	// 初始化 BLE (会在内部创建线程)
	if (g_ble.Init() != 0) {
		LOG_ERR("BLE initialization failed");
	} else {
		g_ble.SetReceiveCallback(OnBleDataReceived);
		LOG_INF("BLE thread started");
	}

	// 主循环
	while (true) {
		// 读取 MCU 发来的数据并转发到 BLE
		uint8_t rx_buf[64]="test";
		int32_t len = g_serial.Write(rx_buf, sizeof(rx_buf));
		LOG_INF("test");
		if (len > 0 && g_ble.IsConnected()) {
			g_ble.SendData(rx_buf, len);
		}

		// 根据连接状态改变 LED 颜色
		if (g_ble.IsConnected()) {
			// 已连接 - 绿色
			led.SetColor(0, 255, 0);
		} else {
			// 未连接 - 蓝色
			led.SetColor(0, 0, 255);
		}

		k_sleep(K_MSEC(100));
	}

	return 0;
}
