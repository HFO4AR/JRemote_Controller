#ifndef CONTROL_DATA_H
#define CONTROL_DATA_H

#include <zephyr/kernel.h>

// 控制数据结构 (9 字节)
struct ControlData {
	uint8_t header;      // 字节0: 帧头 (0xAA=正常, 0xEE=急停)
	int8_t left_x;      // 字节1: 左摇杆 X (-127 ~ 127)
	int8_t left_y;      // 字节2: 左摇杆 Y (-127 ~ 127)
	int8_t right_x;     // 字节3: 右摇杆 X (-127 ~ 127)
	int8_t right_y;     // 字节4: 右摇杆 Y (-127 ~ 127)
	uint32_t buttons;   // 字节5-8: 按钮状态 (位掩码)
};

// 帧头定义
constexpr uint8_t kFrameHeaderNormal = 0xAA;
constexpr uint8_t kFrameHeaderEmergency = 0xEE;

// 连接类型
enum class ConnectionType {
	BLE,    // 蓝牙连接
	AP,     // AP 模式连接
	LAN,    // 局域网连接
	UART    // 串口连接
};

// 数据回调类型
typedef void (*ControlDataCallback)(const ControlData& data);

// 数据处理类
class ControlDataHandler {
public:
	ControlDataHandler();
	~ControlDataHandler() = default;

	// 设置数据接收回调
	void SetCallback(ControlDataCallback callback);

	// 处理接收到的原始数据
	// 返回: true=处理成功, false=数据无效
	bool ProcessData(const uint8_t* data, uint16_t len);

	// 获取最新数据
	ControlData GetData() const;

	// 获取连接类型
	ConnectionType GetConnectionType() const;

	// 设置连接类型
	void SetConnectionType(ConnectionType type);

	// 检查是否是急停指令
	bool IsEmergencyStop() const;

	// 检查是否有新数据
	bool HasNewData() const;

	// 标记数据已处理
	void ClearNewDataFlag();

private:
	ControlData data_;
	ControlDataCallback callback_;
	ConnectionType connection_type_;
	bool has_new_data_;
};

#endif  // CONTROL_DATA_H
