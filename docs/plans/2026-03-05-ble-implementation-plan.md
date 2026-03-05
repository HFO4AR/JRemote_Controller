# BLE 控制功能实现计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 实现基于 Zephyr RTOS 的 BLE 控制功能，与现有 Android App 完全兼容

**Architecture:** 使用 Zephyr 原生 bt_gatt API 创建自定义 GATT 服务，实现 BLE 广播、连接管理、数据收发

**Tech Stack:** Zephyr RTOS, C++17, BLE/GATT

---

### 任务 1: 更新 prj.conf 添加 BLE 配置

**Files:**
- Modify: `firmware/zephyr_app/app/prj.conf`

**Step 1: 添加 BLE 配置**

在 prj.conf 末尾添加:
```
# Bluetooth
CONFIG_BT=y
CONFIG_BT_PERIPHERAL=y
CONFIG_BT_SMP=y
CONFIG_BT_DEVICE_NAME="JRemote Controller"
CONFIG_BT_DEBUG_LOG=y
```

**Step 2: 验证构建**

Run: `cd firmware/zephyr_app && source .venv/bin/activate && python3 build.py`
Expected: 构建成功

---

### 任务 2: 创建 ble.h 头文件

**Files:**
- Create: `firmware/zephyr_app/app/inc/ble.h`

**Step 1: 创建头文件**

```cpp
#ifndef BLE_H
#define BLE_H

#include <zephyr/bluetooth/bluetooth.h>
#include <zephyr/bluetooth/conn.h>
#include <zephyr/bluetooth/gatt.h>
#include <zephyr/bluetooth/hci.h>

// BLE UUID 定义 (与 Android App 兼容)
#define BLE_SERVICE_UUID     BT_UUID_DECLARE_16(0x4afc)
#define BLE_TX_UUID          BT_UUID_DECLARE_128(0xbeb5483e, 0x36e1, 0x4688, 0xb7f5, 0xea07361b26a8)
#define BLE_RX_UUID          BT_UUID_DECLARE_128(0x6e400002, 0xb5a3, 0xf393, 0xe0a9, 0xe50e24dcca9e)

// 数据回调类型
typedef void (*BleDataCallback)(const uint8_t* data, uint16_t len);

// BLE 类
class Ble {
 public:
  Ble();
  ~Ble();

  // 初始化 BLE 堆栈
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
  // BLE 回调函数
  static void OnConnected(struct bt_conn* conn, uint8_t err);
  static void OnDisconnected(struct bt_conn* conn, uint8_t reason);
  static void OnAuthComplete(struct bt_conn* conn, uint8_t err);

  // GATT 回调
  static ssize_t OnRxWrite(struct bt_conn* conn,
                           const struct bt_gatt_attr* attr,
                           const void* buf, uint16_t len,
                           uint16_t offset, uint8_t flags);

  // 成员变量
  bool initialized_;
  bool connected_;
  struct bt_conn* conn_;
  BleDataCallback receive_callback_;
};

#endif  // BLE_H
```

**Step 2: 验证文件创建**

Run: `ls -la firmware/zephyr_app/app/inc/ble.h`
Expected: 文件存在

---

### 任务 3: 创建 ble.cpp 实现文件

**Files:**
- Create: `firmware/zephyr_app/app/src/ble.cpp`

**Step 1: 创建实现文件**

```cpp
#include "ble.h"
#include <zephyr/logging/log.h>

LOG_MODULE_DECLARE(ble);

// GATT 服务定义
static struct bt_gatt_attr gatt_attrs[] = {
  // 服务声明
  BT_GATT_PRIMARY_SERVICE(BLE_SERVICE_UUID),

  // RX 特征 (手机 -> ESP32, 支持写入)
  BT_GATT_CHARACTERISTIC(BLE_RX_UUID,
                         BT_GATT_CHRC_WRITE | BT_GATT_CHRC_WRITE_WITHOUT_RESP,
                         BT_GATT_PERM_WRITE, nullptr, nullptr, nullptr),

  // TX 特征 (ESP32 -> 手机, 支持通知)
  BT_GATT_CHARACTERISTIC(BLE_TX_UUID,
                         BT_GATT_CHRC_NOTIFY,
                         BT_GATT_PERM_NONE, nullptr, nullptr, nullptr),

  // TX 特征描述符 (CCCD)
  BT_GATT_CCC(nullptr, BT_GATT_PERM_READ | BT_GATT_PERM_WRITE),
};

static struct bt_gatt_service gatt_service = BT_GATT_SERVICE(gatt_attrs);

Ble::Ble()
    : initialized_(false), connected_(false), conn_(nullptr),
      receive_callback_(nullptr) {
}

Ble::~Ble() {
}

int Ble::Init() {
  int err;

  // 初始化 BLE 堆栈
  err = bt_enable(nullptr);
  if (err) {
    LOG_ERR("BLE enable failed: %d", err);
    return err;
  }

  // 注册连接回调
  bt_conn_cb_register(OnConnected, OnDisconnected, OnAuthComplete);

  // 注册 GATT 服务
  err = bt_gatt_service_register(&gatt_service);
  if (err) {
    LOG_ERR("GATT service register failed: %d", err);
    return err;
  }

  initialized_ = true;
  LOG_INF("BLE initialized");
  return 0;
}

int Ble::StartAdvertising() {
  int err;

  if (!initialized_) {
    return -1;
  }

  // 广播参数
  struct bt_le_adv_param adv_param = {
    .options = BT_LE_ADV_OPT_CONNECTABLE,
    .interval_min = BT_GAP_ADV_FAST_INT_MIN_1,
    .interval_max = BT_GAP_ADV_FAST_INT_MAX_1,
    .own_addr = nullptr,
  };

  // 广播数据 (设备名称)
  struct bt_data adv_data[] = {
    BT_DATA(BT_DATA_NAME_COMPLETE, "JRemote Controller", 17),
  };

  // 开始广播
  err = bt_le_adv_start(&adv_param, adv_data, ARRAY_SIZE(adv_data),
                        nullptr, 0);
  if (err) {
    LOG_ERR("Advertising start failed: %d", err);
    return err;
  }

  LOG_INF("BLE advertising started");
  return 0;
}

int Ble::SendData(const uint8_t* data, uint16_t len) {
  if (!connected_ || conn_ == nullptr) {
    return -1;
  }

  // 通过 TX 特征发送通知
  return bt_gatt_notify(conn_, &gatt_attrs[4], data, len);
}

void Ble::SetReceiveCallback(BleDataCallback callback) {
  receive_callback_ = callback;
}

bool Ble::IsConnected() const {
  return connected_;
}

// 回调函数实现
void Ble::OnConnected(struct bt_conn* conn, uint8_t err) {
  if (err) {
    LOG_ERR("Connection failed: %d", err);
    return;
  }

  LOG_INF("BLE connected");
  // 注意: 需要通过实例方法设置 connected_
  // 这里使用静态回调，实际项目中需要通过全局变量处理
  extern bool g_ble_connected;
  g_ble_connected = true;
}

void Ble::OnDisconnected(struct bt_conn* conn, uint8_t reason) {
  LOG_INF("BLE disconnected: %d", reason);
  extern bool g_ble_connected;
  g_ble_connected = false;
}

void Ble::OnAuthComplete(struct bt_conn* conn, uint8_t err) {
  LOG_DBG("Auth complete: %d", err);
}

ssize_t Ble::OnRxWrite(struct bt_conn* conn,
                       const struct bt_gatt_attr* attr,
                       const void* buf, uint16_t len,
                       uint16_t offset, uint8_t flags) {
  // 处理接收到的数据
  LOG_HEXDUMP_DBG(buf, len, "RX data:");

  // 调用回调
  extern BleDataCallback g_ble_callback;
  if (g_ble_callback) {
    g_ble_callback((const uint8_t*)buf, len);
  }

  return len;
}

// 全局变量 (供回调使用)
bool g_ble_connected = false;
BleDataCallback g_ble_callback = nullptr;
```

**Step 2: 验证构建**

Run: `cd firmware/zephyr_app && source .venv/bin/activate && python3 build.py 2>&1 | tail -20`
Expected: 编译成功

---

### 任务 4: 更新 CMakeLists.txt 添加 ble.cpp

**Files:**
- Modify: `firmware/zephyr_app/app/CMakeLists.txt`

**Step 1: 添加源文件**

```cmake
# Source files (C++)
target_sources(app PRIVATE
    src/main.cpp
    src/led.cpp
    src/ble.cpp
)
```

**Step 2: 验证构建**

Run: `cd firmware/zephyr_app && source .venv/bin/activate && python3 build.py`
Expected: 构建成功

---

### 任务 5: 更新 main.cpp 集成 BLE

**Files:**
- Modify: `firmware/zephyr_app/app/src/main.cpp`

**Step 1: 更新 main.cpp**

```cpp
/*
 * JRemote Controller - Zephyr Firmware
 * Target: ESP32
 * Language: C++17
 */

#include <zephyr/kernel.h>
#include <zephyr/device.h>
#include <zephyr/devicetree.h>
#include <zephyr/logging/log.h>

#include "led.h"
#include "ble.h"

LOG_MODULE_REGISTER(jremote);

// 全局 BLE 实例
static Ble g_ble;

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

    // 初始化 LED
    const struct device* led_strip_dev = DEVICE_DT_GET(DT_ALIAS(led_strip));
    Led led(led_strip_dev);

    if (!led.Init()) {
        LOG_ERR("LED initialization failed");
    } else {
        LOG_INF("LED initialized: %s", led_strip_dev->name);
    }

    // 初始化 BLE
    if (g_ble.Init() != 0) {
        LOG_ERR("BLE initialization failed");
    } else {
        g_ble.SetReceiveCallback(OnBleDataReceived);
        g_ble.StartAdvertising();
    }

    // 设置 LED 为蓝色表示 BLE 已启动
    led.SetColor(0, 0, 255);
    led.Show();

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
```

**Step 2: 验证构建**

Run: `cd firmware/zephyr_app && source .venv/bin/activate && python3 build.py`
Expected: 构建成功

---

### 任务 6: 编译测试

**Step 1: 完整构建**

Run: `cd firmware/zephyr_app && source .venv/bin/activate && python3 build.py`
Expected: 编译成功，无错误

**Step 2: 烧录固件**

Run: `cd firmware/zephyr_app && source .venv/bin/activate && python3 flash.py`
Expected: 烧录成功

---

## 执行选择

**Plan complete and saved to `docs/plans/2026-03-05-ble-control-design.md`. Two execution options:**

**1. Subagent-Driven (this session)** - I dispatch fresh subagent per task, review between tasks, fast iteration

**2. Parallel Session (separate)** - Open new session with executing_plans, batch execution with checkpoints

**Which approach?**
