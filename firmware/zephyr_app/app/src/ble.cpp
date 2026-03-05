#include "ble.h"
#include <zephyr/kernel.h>
#include <zephyr/bluetooth/bluetooth.h>

// 静态成员定义
Ble* Ble::instance_ = nullptr;

// 连接回调函数
static void OnConnectedCallback(struct bt_conn* conn, uint8_t err) {
  if (err) {
    printk("BLE connection failed: %d\n", err);
    return;
  }
  printk("BLE connected\n");
  if (Ble::instance_) {
    Ble::instance_->OnConnected(conn);
  }
}

static void OnDisconnectedCallback(struct bt_conn* conn, uint8_t reason) {
  printk("BLE disconnected: %d\n", reason);
  if (Ble::instance_) {
    Ble::instance_->OnDisconnected(conn);
  }
}

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

  printk("BLE RX: %d bytes\n", len);

  if (Ble::instance_) {
    Ble::instance_->OnRxData((const uint8_t*)buf, len);
  }

  return len;
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
  BT_GATT_CCC(nullptr, BT_GATT_PERM_READ | BT_GATT_PERM_WRITE),
);

Ble::Ble(k_thread_stack_t* stack, size_t stack_size)
    : stack_(stack), stack_size_(stack_size),
      initialized_(false), connected_(false), conn_(nullptr),
      receive_callback_(nullptr) {
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
    printk("BLE enable failed: %d\n", err);
    return err;
  }

  initialized_ = true;
  printk("BLE initialized\n");

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
    return -1;
  }

  struct bt_data adv_data[] = {
    BT_DATA(BT_DATA_NAME_COMPLETE, "JRemote Controller", 17),
  };

  err = bt_le_adv_start(BT_LE_ADV_CONN_FAST_1, adv_data, ARRAY_SIZE(adv_data),
                         nullptr, 0);
  if (err) {
    printk("Advertising start failed: %d\n", err);
    return err;
  }

  printk("BLE advertising started\n");
  return 0;
}

int Ble::SendData(const uint8_t* data, uint16_t len) {
  if (!connected_ || conn_ == nullptr) {
    return -1;
  }

  return bt_gatt_notify(conn_, nullptr, data, len);
}

void Ble::SetReceiveCallback(BleDataCallback callback) {
  receive_callback_ = callback;
}

bool Ble::IsConnected() const {
  return connected_;
}

void Ble::OnConnected(struct bt_conn* conn) {
  connected_ = true;
  conn_ = bt_conn_ref(conn);
}

void Ble::OnDisconnected(struct bt_conn* conn) {
  (void)conn;
  connected_ = false;
  if (conn_) {
    bt_conn_unref(conn_);
    conn_ = nullptr;
  }
}

void Ble::OnRxData(const uint8_t* data, uint16_t len) {
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
    // printk("hello\n");
    k_msleep(100);
  }
}
