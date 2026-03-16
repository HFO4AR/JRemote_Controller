#include "peripheral/ble.h"
#include "data/data_handler.h"
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

// 服务 UUID 128位（BLE 小端序）
// UUID: 4fafc201-1fb5-459e-8fcc-c5c9c331914b
static const uint8_t service_uuid_128[] = {
	0x4b, 0x91, 0x31, 0xc3, 0xc9, 0xc5, 0xcc, 0x8f,
	0x9e, 0x45, 0xb5, 0x1f, 0x01, 0xc2, 0xaf, 0x4f
};

// 连接回调函数
static void OnConnectedCallback(struct bt_conn* conn, uint8_t err) {
	if (err) {
		LOG_ERR("连接失败 (err %u)", err);
		return;
	}
	if (Ble::instance_) {
		Ble::instance_->OnConnected(conn);
	}
}

static void OnDisconnectedCallback(struct bt_conn* conn, uint8_t reason) {
	LOG_INF("已断开连接 (reason %u)", reason);
	if (Ble::instance_) {
		Ble::instance_->OnDisconnected(conn);
	}

}

// MTU 协商回调
static void OnMtuExchangedCallback(struct bt_conn* conn, uint8_t err,
								   struct bt_gatt_exchange_params* params) {
	if (err) {
		LOG_WRN("MTU 协商失败 (err %u)", err);
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
	  initialized_(false), connected_(false), subscribed_(false),
	  restart_advertising_(false), conn_(nullptr),
	  mtu_(23), data_handler_(nullptr) {
	k_mutex_init(&restart_mutex_);
}

Ble::~Ble() {
}

int Ble::Init() {
	int err;

	// 保存实例指针供回调使用
	instance_ = this;

	// 初始化 BLE 协议栈
	err = bt_enable(nullptr);
	if (err) {
		LOG_ERR("蓝牙初始化失败 (err %d)", err);
		return err;
	}

	initialized_ = true;
	LOG_INF("蓝牙已初始化");

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
		LOG_ERR("蓝牙未初始化");
		return -1;
	}

	// 先停止之前的广播（防止 EALREADY 错误）
	err = bt_le_adv_stop();
	if (err && err != -EALREADY) {
		LOG_WRN("停止广播失败 (err %d)", err);
	}

	// 广播数据：标志和完整服务 UUID
	static const uint8_t flags[] = { BT_LE_AD_GENERAL | BT_LE_AD_NO_BREDR };

	struct bt_data adv_data[] = {
		BT_DATA(BT_DATA_FLAGS, flags, sizeof(flags)),
		BT_DATA(BT_DATA_UUID128_ALL, service_uuid_128, sizeof(service_uuid_128)),
	};

	// 扫描响应数据：设备名称
	struct bt_data scan_data[] = {
		BT_DATA(BT_DATA_NAME_COMPLETE, "JRemote", 7),
	};

	err = bt_le_adv_start(BT_LE_ADV_CONN_FAST_1, adv_data, ARRAY_SIZE(adv_data),
						  scan_data, ARRAY_SIZE(scan_data));
	if (err) {
		LOG_ERR("启动广播失败 (err %d)", err);
		return err;
	}

	LOG_INF("广播已启动");
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
	// attrs[0] = 主服务, attrs[1] = RX 声明, attrs[2] = RX 值
	// attrs[3] = TX 声明, attrs[4] = TX 值, attrs[5] = CCC
	const struct bt_gatt_attr* attr = &ble_gatt_service.attrs[4];
	return bt_gatt_notify(conn_, attr, data, len);
}

void Ble::SetDataHandler(DataHandler* handler) {
	data_handler_ = handler;
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

	LOG_INF("已连接");

	// 请求更大的 MTU
	mtu_exchange_params.func = OnMtuExchangedCallback;
	int err = bt_gatt_exchange_mtu(conn, &mtu_exchange_params);
	if (err) {
		LOG_WRN("MTU 协商请求失败 (err %d)", err);
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

	// 请求线程重启广播
	LOG_INF("请求重启广播");
	RequestRestartAdvertising();
}

void Ble::RequestRestartAdvertising() {
	k_mutex_lock(&restart_mutex_, K_FOREVER);
	restart_advertising_ = true;
	k_mutex_unlock(&restart_mutex_);
}

void Ble::OnCccChanged(uint16_t value) {
	if (value == BT_GATT_CCC_NOTIFY) {
		subscribed_ = true;
		LOG_INF("客户端已订阅通知");
	} else {
		subscribed_ = false;
		LOG_INF("客户端已取消订阅通知");
	}
}

void Ble::OnMtuExchanged(struct bt_conn* conn) {
	// 获取当前 MTU
	mtu_ = bt_gatt_get_mtu(conn);
	LOG_INF("MTU 已协商: %u", mtu_);
}

void Ble::OnRxData(const uint8_t* data, uint16_t len) {
	// 检查是否为 Ping 请求
	if (len == 1 && data[0] == kPingRequest) {
		// 响应 Ping
		uint8_t response = kPingResponse;
		SendData(&response, 1);
		LOG_INF("收到 Ping，已响应");
		return;
	}

	// 转发数据到 DataHandler
	if (data_handler_) {
		data_handler_->SubmitData(ConnectionType::BLE, data, len);
	}
}

void Ble::ThreadEntry(void* p1, void* p2, void* p3) {
	Ble* self = static_cast<Ble*>(p1);

	// 启动广播
	self->StartAdvertising();

	// 线程主循环
	while (true) {
		// 检查是否需要重启广播
		k_mutex_lock(&self->restart_mutex_, K_FOREVER);
		bool need_restart = self->restart_advertising_;
		if (need_restart) {
			self->restart_advertising_ = false;
		}
		k_mutex_unlock(&self->restart_mutex_);

		if (need_restart) {
			// 延迟一段时间让 BLE 栈清理状态
			k_msleep(500);
			int err = self->StartAdvertising();
			if (err == 0) {
				LOG_INF("广播重启成功");
			} else {
				LOG_ERR("广播重启失败 (err %d)，将再次请求重启", err);
				// 再次请求重启
				self->RequestRestartAdvertising();
			}
		}

		k_msleep(100);
	}
}
