#include "ble.h"
#include <zephyr/kernel.h>
#include <zephyr/bluetooth/bluetooth.h>

// 全局变量 (供回调使用)
BleDataCallback g_ble_callback = nullptr;
static bool g_ble_connected = false;
static struct bt_conn* g_conn = nullptr;
static Ble* g_ble_instance = nullptr;

// 连接回调函数
static void OnConnected(struct bt_conn* conn, uint8_t err) {
  if (err) {
    printk("BLE connection failed: %d\n", err);
    return;
  }
  printk("BLE connected\n");
  g_ble_connected = true;
  g_conn = bt_conn_ref(conn);
}

static void OnDisconnected(struct bt_conn* conn, uint8_t reason) {
  printk("BLE disconnected: %d\n", reason);
  g_ble_connected = false;
  if (g_conn) {
    bt_conn_unref(g_conn);
    g_conn = nullptr;
  }
}

// 连接回调定义
BT_CONN_CB_DEFINE(conn_callbacks) = {
  .connected = OnConnected,
  .disconnected = OnDisconnected,
};

// RX 写入回调
static ssize_t OnRxWrite(struct bt_conn* conn,
                         const struct bt_gatt_attr* attr,
                         const void* buf, uint16_t len,
                         uint16_t offset, uint8_t flags) {
  (void)conn;
  (void)attr;
  (void)offset;
  (void)flags;

  printk("BLE RX: %d bytes\n", len);

  if (g_ble_callback) {
    g_ble_callback((const uint8_t*)buf, len);
  }

  return len;
}

// GATT 服务定义
BT_GATT_SERVICE_DEFINE(ble_gatt_service,
  BT_GATT_PRIMARY_SERVICE(BLE_SERVICE_UUID),
  BT_GATT_CHARACTERISTIC(BLE_RX_UUID,
                        BT_GATT_CHRC_WRITE | BT_GATT_CHRC_WRITE_WITHOUT_RESP,
                        BT_GATT_PERM_WRITE, nullptr, OnRxWrite, nullptr),
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

  // 初始化 BLE 堆栈
  err = bt_enable(nullptr);
  if (err) {
    printk("BLE enable failed: %d\n", err);
    return err;
  }

  // 保存实例指针供回调使用
  g_ble_instance = this;

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
  if (!g_ble_connected || g_conn == nullptr) {
    return -1;
  }

  return bt_gatt_notify(g_conn, nullptr, data, len);
}

void Ble::SetReceiveCallback(BleDataCallback callback) {
  receive_callback_ = callback;
  g_ble_callback = callback;
}

bool Ble::IsConnected() const {
  return g_ble_connected;
}

void Ble::ThreadEntry(void* p1, void* p2, void* p3) {
  Ble* self = static_cast<Ble*>(p1);

  // 启动广播
  self->StartAdvertising();

  // 线程主循环
  while (true) {
    // BLE 处理可以根据需要添加周期性任务
    // 目前 BLE 事件通过回调处理，这里可以添加心跳等逻辑
    k_msleep(100);
  }
}
