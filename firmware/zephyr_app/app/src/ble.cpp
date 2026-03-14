#include "ble.h"
#include <zephyr/kernel.h>
#include <zephyr/bluetooth/bluetooth.h>
#include <zephyr/bluetooth/gatt.h>
#include <zephyr/logging/log.h>

LOG_MODULE_REGISTER(ble);

// 静态成员定义
Ble* Ble::instance_ = nullptr;

// Ping 定义
static constexpr uint8_t kPingRequest = 0x70;   // 'p'
static constexpr uint8_t kPingResponse = 0x50;  // 'P'

// 服务 UUID 128-bit (little-endian for BLE)
// UUID: 4fafc201-1fb5-459e-8fcc-c5c9c331914b
static const uint8_t service_uuid_128[] = {
	0x4b, 0x91, 0x31, 0xc3, 0xc9, 0xc5, 0xcc, 0x8f,
	0x9e, 0x45, 0xb5, 0x1f, 0x01, 0xc2, 0xaf, 0x4f
};

// 连接回调函数
static void OnConnectedCallback(struct bt_conn* conn, uint8_t err) {
	if (err) {
		LOG_ERR("Connection failed (err %u)", err);
		return;
	}
	if (Ble::instance_) {
		Ble::instance_->OnConnected(conn);
	}
}

static void OnDisconnectedCallback(struct bt_conn* conn, uint8_t reason) {
	LOG_INF("Disconnected (reason %u)", reason);
	if (Ble::instance_) {
		Ble::instance_->OnDisconnected(conn);
	}
}

// MTU 协商回调
static void OnMtuExchangedCallback(struct bt_conn* conn, uint8_t err,
								   struct bt_gatt_exchange_params* params) {
	if (err) {
		LOG_WRN("MTU exchange failed (err %u)", err);
	} else {
		if (Ble::instance_) {
			Ble::instance_->OnMtuExchanged(conn);
		}
	}
}

static struct bt_gatt_exchange_params mtu_exchange_params;

// 连接回调定义
BT_CONN_CB_DEFINE(conn_callbacks) = {
	.connected = OnConnectedCallback,
	.disconnected = OnDisconnectedCallback,
};

// RX 写入回调
static ssize_t OnRxWriteCallback(struct bt_conn* conn,
								 const struct bt_gatt_attr* attr,
								 const void* buf, uint16_t len,
								 uint16_t offset, uint8_t flags) {
	(void)conn;
	(void)attr;
	(void)offset;
	(void)flags;

	if (Ble::instance_) {
		Ble::instance_->OnRxData((const uint8_t*)buf, len);
	}

	return len;
}

// CCC 配置回调
static void OnCccConfigChangedCallback(const struct bt_gatt_attr* attr,
									   uint16_t value) {
	if (Ble::instance_) {
		Ble::instance_->OnCccChanged(value);
	}
}

// GATT 服务定义
BT_GATT_SERVICE_DEFINE(ble_gatt_service,
	BT_GATT_PRIMARY_SERVICE(BLE_SERVICE_UUID),
	BT_GATT_CHARACTERISTIC(BLE_RX_UUID,
						  BT_GATT_CHRC_WRITE | BT_GATT_CHRC_WRITE_WITHOUT_RESP,
						  BT_GATT_PERM_WRITE, nullptr, OnRxWriteCallback, nullptr),
	BT_GATT_CHARACTERISTIC(BLE_TX_UUID,
						  BT_GATT_CHRC_NOTIFY,
						  BT_GATT_PERM_NONE, nullptr, nullptr, nullptr),
	BT_GATT_CCC(OnCccConfigChangedCallback, BT_GATT_PERM_READ | BT_GATT_PERM_WRITE),
);

Ble::Ble(k_thread_stack_t* stack, size_t stack_size)
	: stack_(stack), stack_size_(stack_size),
	  initialized_(false), connected_(false), subscribed_(false), conn_(nullptr),
	  receive_callback_(nullptr), mtu_(23) {
}

Ble::~Ble() {
}

int Ble::Init() {
	int err;

	// 保存实例指针供回调使用
	instance_ = this;

	// 初始化 BLE 堆栈
	err = bt_enable(nullptr);
	if (err) {
		LOG_ERR("Bluetooth init failed (err %d)", err);
		return err;
	}

	initialized_ = true;
	LOG_INF("Bluetooth initialized");

	// 创建线程
	k_thread_create(&thread_data_,
					stack_,
					stack_size_,
					ThreadEntry,
					this, nullptr, nullptr,
					5,  // 优先级
					0,  // 选项
					K_NO_WAIT);

	return 0;
}

int Ble::StartAdvertising() {
	int err;

	if (!initialized_) {
		LOG_ERR("Bluetooth not initialized");
		return -1;
	}

	// 广播数据: 包含标志和完整服务 UUID
	static const uint8_t flags[] = { BT_LE_AD_GENERAL | BT_LE_AD_NO_BREDR };

	struct bt_data adv_data[] = {
		BT_DATA(BT_DATA_FLAGS, flags, sizeof(flags)),
		BT_DATA(BT_DATA_UUID128_ALL, service_uuid_128, sizeof(service_uuid_128)),
	};

	// 扫描响应数据: 设备名称
	struct bt_data scan_data[] = {
		BT_DATA(BT_DATA_NAME_COMPLETE, "JRemote", 7),
	};

	err = bt_le_adv_start(BT_LE_ADV_CONN_FAST_1, adv_data, ARRAY_SIZE(adv_data),
						  scan_data, ARRAY_SIZE(scan_data));
	if (err) {
		LOG_ERR("Advertising failed to start (err %d)", err);
		return err;
	}

	LOG_INF("Advertising started");
	return 0;
}

int Ble::SendData(const uint8_t* data, uint16_t len) {
	if (!connected_ || conn_ == nullptr) {
		return -1;
	}

	if (!subscribed_) {
		return -2;
	}

	// 使用 TX 特征发送通知
	const struct bt_gatt_attr* attr = &ble_gatt_service.attrs[3];
	return bt_gatt_notify(conn_, attr, data, len);
}

void Ble::SetReceiveCallback(BleDataCallback callback) {
	receive_callback_ = callback;
}

bool Ble::IsConnected() const {
	return connected_;
}

bool Ble::IsSubscribed() const {
	return subscribed_;
}

uint16_t Ble::GetMtu() const {
	return mtu_;
}

void Ble::OnConnected(struct bt_conn* conn) {
	connected_ = true;
	conn_ = bt_conn_ref(conn);

	LOG_INF("Connected");

	// 请求更大的 MTU
	mtu_exchange_params.func = OnMtuExchangedCallback;
	int err = bt_gatt_exchange_mtu(conn, &mtu_exchange_params);
	if (err) {
		LOG_WRN("MTU exchange request failed (err %d)", err);
	}
}

void Ble::OnDisconnected(struct bt_conn* conn) {
	(void)conn;
	connected_ = false;
	subscribed_ = false;
	mtu_ = 23;
	if (conn_) {
		bt_conn_unref(conn_);
		conn_ = nullptr;
	}

	// 重新开始广播
	StartAdvertising();
}

void Ble::OnCccChanged(uint16_t value) {
	if (value == BT_GATT_CCC_NOTIFY) {
		subscribed_ = true;
		LOG_INF("Client subscribed to notifications");
	} else {
		subscribed_ = false;
		LOG_INF("Client unsubscribed from notifications");
	}
}

void Ble::OnMtuExchanged(struct bt_conn* conn) {
	// 获取当前 MTU
	mtu_ = bt_gatt_get_mtu(conn);
	LOG_INF("MTU exchanged: %u", mtu_);
}

void Ble::OnRxData(const uint8_t* data, uint16_t len) {
	LOG_INF("RX data: %s", data);
	// 检查是否是 Ping 请求
	if (len == 1 && data[0] == kPingRequest) {
		// 响应 Ping
		uint8_t response = kPingResponse;
		SendData(&response, 1);
		LOG_INF("Ping received, responded");
		return;
	}

	// 其他数据传递给回调
	if (receive_callback_) {
		receive_callback_(data, len);
	}
}

void Ble::ThreadEntry(void* p1, void* p2, void* p3) {
	Ble* self = static_cast<Ble*>(p1);

	// 启动广播
	self->StartAdvertising();

	// 线程主循环
	while (true) {
		// BLE 处理可以根据需要添加周期性任务
		k_msleep(100);
	}
}
