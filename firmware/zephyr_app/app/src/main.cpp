// JRemote Controller - Zephyr 固件
// 目标平台: ESP32
// 语言: C++17

#include <zephyr/kernel.h>
#include <zephyr/device.h>
#include <zephyr/devicetree.h>
#include <zephyr/logging/log.h>

#include "peripheral/ws2812_led.h"
#include "peripheral/ble.h"
#include "peripheral/serial.h"
#include "data/data_handler.h"
#include "data/control_data.h"

LOG_MODULE_REGISTER(jremote);

// BLE 线程栈
K_THREAD_STACK_DEFINE(ble_stack_area, 2048);

// 全局 BLE 实例（传入线程栈）
static Ble g_ble(ble_stack_area, K_THREAD_STACK_SIZEOF(ble_stack_area));

// 全局 DataHandler 实例
static DataHandler g_data_handler;

// WS2812 LED 设备
static const struct device* led_strip_dev = DEVICE_DT_GET(DT_ALIAS(led_strip));

// MCU UART 设备
static const struct device* mcu_uart_dev = DEVICE_DT_GET(DT_NODELABEL(uart1));

// Serial 实例（RX 环形缓冲区 512 字节，DMA 缓冲区 256 字节）
static Serial<512, 256> g_serial(mcu_uart_dev, kInterrupt, 115200);

// 控制数据回调（由 DataHandler 调用）
static void OnControlDataCallback(const uint8_t* data, uint16_t len, ConnectionType source) {
	if (len < kControlDataLen) {
		LOG_WRN("控制数据过短: %u", len);
		return;
	}

	// 解析控制数据
	ControlData control_data;
	memcpy(&control_data, data, sizeof(ControlData));

	if (control_data.header == static_cast<uint8_t>(FrameType::kEmergency)) {
		LOG_WRN("收到急停指令，来源: %u!", static_cast<uint8_t>(source));
		// TODO: 执行急停逻辑
	} else {
		LOG_DBG("控制数据: L=(%d,%d) R=(%d,%d) buttons=0x%08X",
				(int)control_data.left_x, (int)control_data.left_y,
				(int)control_data.right_x, (int)control_data.right_y,
				(unsigned int)control_data.buttons);
	}

	// 转发控制数据到 MCU（通过 UART）
	g_serial.SendData(data, len);
}

// 用户数据回调（由 DataHandler 调用）
static void OnUserDataCallback(const uint8_t* data, uint16_t len, ConnectionType source) {
	LOG_INF("收到用户数据: %u 字节，来源: %u", len, static_cast<uint8_t>(source));

	// 回传用户数据到 App（通过 BLE TX）
	// 构造响应包：帧头 + 长度 + 数据
	uint8_t response[2 + 255];
	response[0] = static_cast<uint8_t>(FrameType::kUserData);
	response[1] = static_cast<uint8_t>(len);
	if (len > 0 && len <= 255) {
		memcpy(&response[2], data, len);
		g_ble.SendData(response, 2 + len);
	}

	// 可选：转发用户数据到 MCU（通过 UART）
	// g_serial.SendData(data, len);
}

int main(void)
{
	LOG_INF("JRemote Controller Zephyr 固件 v0.1.0");
	LOG_INF("目标平台: ESP32");

	// 检查 LED 设备是否就绪
	if (!device_is_ready(led_strip_dev)) {
		LOG_ERR("LED 设备未就绪");
		return -1;
	}

	// 初始化 WS2812 LED
	Ws2812Led led(led_strip_dev);
	if (!led.Init()) {
		LOG_ERR("WS2812 LED 初始化失败");
	} else {
		LOG_INF("WS2812 LED 已初始化");
	}

	// 设置 LED 为蓝色表示 BLE 初始化中
	led.SetColor(0, 0, 255);

	// 初始化 Serial
	if (!g_serial.IsReady()) {
		LOG_ERR("串口设备未就绪");
	} else {
		LOG_INF("串口已初始化");
	}

	// 初始化 DataHandler
	if (g_data_handler.Init() != 0) {
		LOG_ERR("DataHandler 初始化失败");
		return -1;
	}
	LOG_INF("DataHandler 已初始化");

	// 设置 DataHandler 回调
	g_data_handler.SetControlDataCallback(OnControlDataCallback);
	g_data_handler.SetUserDataCallback(OnUserDataCallback);

	// 初始化 BLE 并连接 DataHandler
	if (g_ble.Init() != 0) {
		LOG_ERR("BLE 初始化失败");
		return -1;
	}
	g_ble.SetDataHandler(&g_data_handler);
	LOG_INF("BLE 线程已启动");

	// 主循环
	while (true) {
		// 根据连接状态改变 LED 颜色
		if (g_ble.IsConnected()) {
			// 已连接 - 绿色
			led.SetColor(0, 255, 0);
			k_sleep(K_MSEC(100));
		} else {
			// 未连接 - 蓝色
			led.SetColor(0, 0, 255);
			k_sleep(K_MSEC(100));
		}
	}

	return 0;
}
