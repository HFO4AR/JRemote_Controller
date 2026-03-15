#ifndef CONTROL_DATA_H
#define CONTROL_DATA_H

#include <cstdint>

// 控制数据长度（9字节）
constexpr size_t kControlDataLen = 9;

// 控制数据结构（9字节）
struct ControlData {
	uint8_t header;      // 字节0: 帧头 (0xAA=正常, 0xEE=急停)
	int8_t left_x;       // 字节1: 左摇杆 X (-127 ~ 127)
	int8_t left_y;       // 字节2: 左摇杆 Y (-127 ~ 127)
	int8_t right_x;      // 字节3: 右摇杆 X (-127 ~ 127)
	int8_t right_y;      // 字节4: 右摇杆 Y (-127 ~ 127)
	uint32_t buttons;    // 字节5-8: 按钮状态（位掩码）
};

// 帧类型定义
enum class FrameType : uint8_t {
	kControlData = 0xAA,  // 控制数据（9字节固定长度）
	kUserData = 0xBB,     // 用户数据（变长）
	kEmergency = 0xEE,    // 急停
};

// 连接类型
enum class ConnectionType : uint8_t {
	BLE = 0,    // 蓝牙连接
	AP = 1,     // AP 模式连接
	LAN = 2,    // 局域网连接
	UART = 3,   // 串口连接
};

#endif  // CONTROL_DATA_H
