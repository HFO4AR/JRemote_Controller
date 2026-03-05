# BLE 控制功能设计

## 概述

实现基于 Zephyr RTOS 的 BLE 控制功能，与现有 Android App 完全兼容。

## BLE 配置

### GATT 服务

| 属性 | 值 |
|------|-----|
| 服务 UUID | `4fafc201-1fb5-459e-8fcc-c5c9c331914b` |
| TX 特征 UUID | `beb5483e-36e1-4688-b7f5-ea07361b26a8` (Notify) |
| RX 特征 UUID | `6e400002-b5a3-f393-e0a9-e50e24dcca9e` (Write) |
| 设备名称 | `JRemote Controller` |

### 数据格式

9 字节控制数据包（与 UDP 协议一致）：
- 字节 0: 帧头 (0xAA = 正常, 0xEE = 急停)
- 字节 1-2: 左摇杆 X, Y (-127 ~ 127)
- 字节 3-4: 右摇杆 X, Y (-127 ~ 127)
- 字节 5-8: 按钮状态 (位掩码)

## 模块设计

### Ble 类

```cpp
class Ble {
 public:
  Ble();
  bool Init();
  bool StartAdvertising();
  bool SendData(const uint8_t* data, uint16_t len);
  void SetReceiveCallback(Callback cb);
  bool IsConnected() const;

 private:
  // BLE 回调处理
  static void Connected(struct bt_conn* conn, uint8_t err);
  static void Disconnected(struct bt_conn* conn, uint8_t reason);
  static ssize_t WriteCallback(struct bt_conn* conn,
                               const struct bt_gatt_attr* attr,
                               const void* buf, uint16_t len,
                               uint16_t offset, uint8_t flags);
};
```

### 核心功能

1. **初始化** - 配置 BLE 堆栈、设置设备名称
2. **广播** - 启动可连接广播
3. **连接管理** - 处理连接/断开事件
4. **数据接收** - RX 特征写入回调，处理控制指令
5. **数据发送** - 通过 TX 特征通知手机

## 配置项 (prj.conf)

```
CONFIG_BT=y
CONFIG_BT_PERIPHERAL=y
CONFIG_BT_SMP=y
CONFIG_BT_DEVICE_NAME="JRemote Controller"
CONFIG_BT_DEBUG_LOG=y
```

## 文件结构

```
app/
├── inc/
│   └── ble.h          # BLE 类头文件
├── src/
│   ├── ble.cpp        # BLE 类实现
│   └── main.cpp       # 主程序 (更新)
└── prj.conf          # 配置更新
```

## 实现步骤

1. 更新 prj.conf 添加 BLE 配置
2. 创建 ble.h 头文件
3. 创建 ble.cpp 实现文件
4. 更新 main.cpp 集成 BLE
5. 编译测试
