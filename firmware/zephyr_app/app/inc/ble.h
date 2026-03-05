#ifndef BLE_H
#define BLE_H

#include <zephyr/bluetooth/bluetooth.h>
#include <zephyr/bluetooth/conn.h>
#include <zephyr/bluetooth/gatt.h>
#include <zephyr/bluetooth/hci.h>
#include <zephyr/kernel.h>

// BLE UUID 定义 (与 Android App 兼容)
#define BLE_SERVICE_UUID_VAL     BT_UUID_128_ENCODE(0x4afc0001, 0x1fb5, 0x459e, 0x8fcc, 0xc5c9c331914b)
#define BLE_TX_UUID_VAL          BT_UUID_128_ENCODE(0xbeb5483e, 0x36e1, 0x4688, 0xb7f5, 0xea07361b26a8)
#define BLE_RX_UUID_VAL          BT_UUID_128_ENCODE(0x6e400002, 0xb5a3, 0xf393, 0xe0a9, 0xe50e24dcca9e)

#define BLE_SERVICE_UUID     BT_UUID_DECLARE_128(BLE_SERVICE_UUID_VAL)
#define BLE_TX_UUID          BT_UUID_DECLARE_128(BLE_TX_UUID_VAL)
#define BLE_RX_UUID          BT_UUID_DECLARE_128(BLE_RX_UUID_VAL)

// 数据回调类型
typedef void (*BleDataCallback)(const uint8_t* data, uint16_t len);

// BLE 类
class Ble {
 public:
  // 构造函数
  Ble(k_thread_stack_t* stack, size_t stack_size);
  ~Ble();

  // 初始化 BLE 堆栈并启动线程
  int Init();

  // 启动广播
  int StartAdvertising();

  // 发送数据到手机
  int SendData(const uint8_t* data, uint16_t len);

  // 设置数据接收回调
  void SetReceiveCallback(BleDataCallback callback);

  // 获取连接状态
  bool IsConnected() const;

 private:
  // 线程入口 (静态成员函数)
  static void ThreadEntry(void* p1, void* p2, void* p3);

  // BLE 回调函数
  static void OnConnected(struct bt_conn* conn, uint8_t err);
  static void OnDisconnected(struct bt_conn* conn, uint8_t reason);

  // GATT 回调
  static ssize_t OnRxWrite(struct bt_conn* conn,
                           const struct bt_gatt_attr* attr,
                           const void* buf, uint16_t len,
                           uint16_t offset, uint8_t flags);

  // 线程相关
  k_thread_stack_t* stack_;
  size_t stack_size_;
  struct k_thread thread_data_;

  // 成员变量
  bool initialized_;
  bool connected_;
  struct bt_conn* conn_;
  BleDataCallback receive_callback_;
};

// 全局回调指针 (供 GATT 回调使用)
extern BleDataCallback g_ble_callback;

#endif  // BLE_H
