#ifndef BLE_H
#define BLE_H

#include <zephyr/bluetooth/bluetooth.h>
#include <zephyr/bluetooth/conn.h>
#include <zephyr/bluetooth/gatt.h>
#include <zephyr/bluetooth/hci.h>
#include <zephyr/kernel.h>
#include "data/control_data.h"

// 前向声明
class DataHandler;

// BLE UUID 定义（与 Android App 兼容）
// 服务: 4fafc201-1fb5-459e-8fcc-c5c9c331914b
// TX (通知): beb5483e-36e1-4688-b7f5-ea07361b26a8（设备发送，App 接收）
// RX (写入): 6e400002-b5a3-f393-e0a9-e50e24dcca9e（App 发送，设备接收）
#define BLE_SERVICE_UUID_VAL     BT_UUID_128_ENCODE(0x4fafc201, 0x1fb5, 0x459e, 0x8fcc, 0xc5c9c331914b)
#define BLE_TX_UUID_VAL          BT_UUID_128_ENCODE(0xbeb5483e, 0x36e1, 0x4688, 0xb7f5, 0xea07361b26a8)
#define BLE_RX_UUID_VAL          BT_UUID_128_ENCODE(0x6e400002, 0xb5a3, 0xf393, 0xe0a9, 0xe50e24dcca9e)

#define BLE_SERVICE_UUID     BT_UUID_DECLARE_128(BLE_SERVICE_UUID_VAL)
#define BLE_TX_UUID          BT_UUID_DECLARE_128(BLE_TX_UUID_VAL)
#define BLE_RX_UUID          BT_UUID_DECLARE_128(BLE_RX_UUID_VAL)

// BLE 传输类 - 仅处理 BLE 通信
class Ble {
public:
	// 构造函数
	Ble(k_thread_stack_t* stack, size_t stack_size);
	~Ble();

	// 初始化 BLE 协议栈并启动线程
	int Init();

	// 启动广播
	int StartAdvertising();

	// 发送数据到已连接设备
	int SendData(const uint8_t* data, uint16_t len);

	// 设置数据处理器（用于接收数据）
	void SetDataHandler(DataHandler* handler);

	// 获取连接状态
	bool IsConnected() const;

	// 获取通知订阅状态
	bool IsSubscribed() const;

	// 获取当前 MTU
	uint16_t GetMtu() const;

	// 供回调使用的公共接口
	void OnConnected(struct bt_conn* conn);
	void OnDisconnected(struct bt_conn* conn);
	void OnRxData(const uint8_t* data, uint16_t len);
	void OnMtuExchanged(struct bt_conn* conn);
	void OnCccChanged(uint16_t value);

	// 静态实例指针（供回调使用）
	static Ble* instance_;

private:
	// 线程入口（静态成员函数）
	static void ThreadEntry(void* p1, void* p2, void* p3);

	// 线程相关
	k_thread_stack_t* stack_;
	size_t stack_size_;
	struct k_thread thread_data_;

	// 成员变量
	bool initialized_;
	bool connected_;
	bool subscribed_;
	struct bt_conn* conn_;
	uint16_t mtu_;

	// 数据处理器指针（由 main 拥有，非 Ble 拥有）
	DataHandler* data_handler_;
};

#endif  // BLE_H
