#ifndef CONTROL_DATA_H
#define CONTROL_DATA_H

#include <cstdint>

// ============================================================
// 控制数据帧长度定义
// ============================================================

// 标准帧长度
constexpr size_t kControlDataMinLen = 6;    // 最简帧: 帧头(1) + 摇杆(4) + 按钮(1)
constexpr size_t kControlDataLen = 9;        // 标准帧: 帧头(1) + 摇杆(4) + 按钮(4)
constexpr size_t kControlData16Len = 13;      // 16位帧: 帧头(1) + 摇杆(8) + 按钮(4)

// 多节点帧长度（比标准帧多1字节路由ID）
constexpr size_t kControlDataMinMultiLen = 7;    // 多节点最简: 7字节 (6+1路由)
constexpr size_t kControlDataMultiLen = 10;       // 多节点标准: 10字节 (9+1路由)
constexpr size_t kControlData16MultiLen = 14;    // 多节点16位: 14字节 (13+1路由)

// ============================================================
// 帧类型定义（手机 ↔ ESP32）
// ============================================================

enum class FrameType : uint8_t {
	// 控制数据（标准帧）
	kControlDataMin = 0xA0,   // 控制数据（最简，6字节）
	kControlData = 0xAA,      // 控制数据（标准，9字节）
	kControlData16 = 0xAC,    // 控制数据（16位，17字节）

	// 多节点控制数据（手机→ESP32，通过帧头区分路由）
	kControlDataMinMulti = 0xB0,   // 控制数据（多节点最简，10字节）
	kControlDataMulti = 0xB2,      // 控制数据（多节点标准，11字节）
	kControlData16Multi = 0xB3,     // 控制数据（多节点16位，19字节）

	// 用户数据
	kUserData = 0xBB,         // 用户数据（变长）
	kUserDataMulti = 0xB1,    // 用户数据（多节点，变长）

	// 急停
	kEmergency = 0xEE,        // 急停指令
};

// ============================================================
// UART 帧类型定义（ESP32 ↔ MCU）
// ============================================================

enum class UartFrameType : uint8_t {
	// 无校验
	kUartControlMin = 0xC0,   // UART 控制数据（最简，6字节）
	kUartControl = 0xC1,      // UART 控制数据（标准，9字节）
	kUartControl16 = 0xC2,    // UART 控制数据（16位，17字节）
	kUartUserData = 0xC3,     // UART 用户数据（变长）
	kUartEmergency = 0xC4,    // UART 急停（1字节）

	// 带 XOR 校验
	kUartControlMinChecksum = 0xC8,   // UART 最简+校验（7字节）
	kUartControlChecksum = 0xC9,      // UART 标准+校验（10字节）
	kUartControl16Checksum = 0xCA,    // UART 16位+校验（18字节）
	kUartUserDataChecksum = 0xCB,     // UART 用户数据+校验（变长）
};

// ============================================================
// 错误码定义
// ============================================================

enum class ErrorCode : uint8_t {
	kNoError = 0x00,       // 无错误
	kInvalidHeader = 0x01, // 无效帧头
	kChecksumFailed = 0x02, // 校验失败
	kDataOverflow = 0x03,   // 数据超限
	kFrameError = 0x04,     // 帧错误
	kUnknownError = 0xFF,   // 未知错误
};

// ============================================================
// 路由 ID 定义（多节点通信）
// ============================================================

enum class RouteId : uint8_t {
	kBroadcast = 0x00,  // 广播（所有节点）
	kNode1 = 0x01,      // 节点1
	kNode2 = 0x02,      // 节点2
	kNode3 = 0x03,      // 节点3
	kNode4 = 0x04,      // 节点4
	kNode5 = 0x05,      // 节点5
	kNode6 = 0x06,      // 节点6
	kReserved = 0xFF,   // 保留
};

// ============================================================
// 用户数据子类型定义
// ============================================================

enum class UserDataSubtype : uint8_t {
	kSensorData = 0x01,   // 传感器数据
	kDebugInfo = 0x02,    // 调试信息
	kSystemStatus = 0x03, // 系统状态
	kResponse = 0x04,     // 响应数据
	kErrorResponse = 0x05,// 错误响应
};

// ============================================================
// 控制数据结构
// ============================================================

// 最简控制数据（6字节）
struct ControlDataMin {
	uint8_t header;      // 0xA0
	int8_t left_x;       // 左摇杆 X (-127 ~ 127)
	int8_t left_y;       // 左摇杆 Y (-127 ~ 127)
	int8_t right_x;      // 右摇杆 X (-127 ~ 127)
	int8_t right_y;      // 右摇杆 Y (-127 ~ 127)
	uint8_t buttons;     // 按钮状态（位掩码，Bit0-Bit7）
};

// 标准控制数据（9字节）
struct ControlData {
	uint8_t header;      // 0xAA
	int8_t left_x;       // 左摇杆 X (-127 ~ 127)
	int8_t left_y;       // 左摇杆 Y (-127 ~ 127)
	int8_t right_x;      // 右摇杆 X (-127 ~ 127)
	int8_t right_y;      // 右摇杆 Y (-127 ~ 127)
	uint32_t buttons;    // 按钮状态（位掩码，Bit0-Bit31）
};

// 16位控制数据（17字节）
struct ControlData16 {
	uint8_t header;       // 0xAC
	int16_t left_x;       // 左摇杆 X (-32767 ~ 32767)，大端序
	int16_t left_y;       // 左摇杆 Y (-32767 ~ 32767)，大端序
	int16_t right_x;      // 右摇杆 X (-32767 ~ 32767)，大端序
	int16_t right_y;      // 右摇杆 Y (-32767 ~ 32767)，大端序
	uint32_t buttons;     // 按钮状态（位掩码，Bit0-Bit31）
};

// 多节点最简控制数据（10字节）
struct ControlDataMinMulti {
	uint8_t header;       // 0xB0
	uint8_t route_id;      // 路由 ID
	int8_t left_x;         // 左摇杆 X (-127 ~ 127)
	int8_t left_y;         // 左摇杆 Y (-127 ~ 127)
	int8_t right_x;        // 右摇杆 X (-127 ~ 127)
	int8_t right_y;        // 右摇杆 Y (-127 ~ 127)
	uint8_t buttons;       // 按钮状态（位掩码）
};

// 多节点标准控制数据（11字节）
struct ControlDataMulti {
	uint8_t header;       // 0xB2
	uint8_t route_id;      // 路由 ID
	int8_t left_x;         // 左摇杆 X (-127 ~ 127)
	int8_t left_y;         // 左摇杆 Y (-127 ~ 127)
	int8_t right_x;        // 右摇杆 X (-127 ~ 127)
	int8_t right_y;        // 右摇杆 Y (-127 ~ 127)
	uint32_t buttons;      // 按钮状态（位掩码）
};

// 多节点16位控制数据（19字节）
struct ControlData16Multi {
	uint8_t header;        // 0xB3
	uint8_t route_id;      // 路由 ID
	int16_t left_x;        // 左摇杆 X (-32767 ~ 32767)
	int16_t left_y;        // 左摇杆 Y (-32767 ~ 32767)
	int16_t right_x;       // 右摇杆 X (-32767 ~ 32767)
	int16_t right_y;       // 右摇杆 Y (-32767 ~ 32767)
	uint32_t buttons;       // 按钮状态（位掩码）
};

// ============================================================
// UART 控制数据结构
// ============================================================

// UART 最简控制数据（6字节）
struct UartControlDataMin {
	uint8_t header;      // 0xC0
	int8_t left_x;       // 左摇杆 X
	int8_t left_y;       // 左摇杆 Y
	int8_t right_x;      // 右摇杆 X
	int8_t right_y;      // 右摇杆 Y
	uint8_t buttons;     // 按钮状态
};

// UART 标准控制数据（9字节）
struct UartControlData {
	uint8_t header;      // 0xC1
	int8_t left_x;       // 左摇杆 X
	int8_t left_y;       // 左摇杆 Y
	int8_t right_x;      // 右摇杆 X
	int8_t right_y;      // 右摇杆 Y
	uint32_t buttons;    // 按钮状态
};

// UART 16位控制数据（17字节）
struct UartControlData16 {
	uint8_t header;       // 0xC2
	int16_t left_x;       // 左摇杆 X，大端序
	int16_t left_y;       // 左摇杆 Y，大端序
	int16_t right_x;      // 右摇杆 X，大端序
	int16_t right_y;      // 右摇杆 Y，大端序
	uint32_t buttons;     // 按钮状态
};

// ============================================================
// 工具函数
// ============================================================

/**
 * 计算 XOR 校验和
 * @param data 数据指针
 * @param length 数据长度
 * @return 校验和
 */
inline uint8_t CalculateXorChecksum(const uint8_t* data, uint16_t length) {
	uint8_t checksum = 0;
	for (uint16_t i = 0; i < length; i++) {
		checksum ^= data[i];
	}
	return checksum;
}

/**
 * 获取帧长度
 * @param frame_type 帧类型
 * @return 帧长度，不支持返回0
 */
inline size_t GetFrameLength(FrameType frame_type) {
	switch (frame_type) {
		case FrameType::kControlDataMin:      return kControlDataMinLen;
		case FrameType::kControlData:         return kControlDataLen;
		case FrameType::kControlData16:       return kControlData16Len;
		case FrameType::kControlDataMinMulti: return kControlDataMinMultiLen;
		case FrameType::kControlDataMulti:    return kControlDataMultiLen;
		case FrameType::kControlData16Multi:  return kControlData16MultiLen;
		case FrameType::kUserData:
		case FrameType::kUserDataMulti:
		case FrameType::kEmergency:            return 1;  // 急停只有帧头
		default:                              return 0;
	}
}

/**
 * 获取 UART 帧长度
 * @param uart_frame_type UART 帧类型
 * @return 帧长度
 */
inline size_t GetUartFrameLength(UartFrameType uart_frame_type) {
	switch (uart_frame_type) {
		case UartFrameType::kUartControlMin:        return kControlDataMinLen;
		case UartFrameType::kUartControl:           return kControlDataLen;
		case UartFrameType::kUartControl16:         return kControlData16Len;
		case UartFrameType::kUartEmergency:        return 1;
		case UartFrameType::kUartControlMinChecksum:  return kControlDataMinLen + 1;
		case UartFrameType::kUartControlChecksum:     return kControlDataLen + 1;
		case UartFrameType::kUartControl16Checksum:   return kControlData16Len + 1;
		case UartFrameType::kUartUserData:
		case UartFrameType::kUartUserDataChecksum:    return 0;  // 变长
		default:                                      return 0;
	}
}

/**
 * 判断是否为控制数据帧
 */
inline bool IsControlFrame(FrameType frame_type) {
	return frame_type == FrameType::kControlDataMin ||
	       frame_type == FrameType::kControlData ||
	       frame_type == FrameType::kControlData16 ||
	       frame_type == FrameType::kControlDataMinMulti ||
	       frame_type == FrameType::kControlDataMulti ||
	       frame_type == FrameType::kControlData16Multi;
}

/**
 * 判断是否为多节点帧
 */
inline bool IsMultiNodeFrame(FrameType frame_type) {
	return frame_type == FrameType::kControlDataMinMulti ||
	       frame_type == FrameType::kControlDataMulti ||
	       frame_type == FrameType::kControlData16Multi ||
	       frame_type == FrameType::kUserDataMulti;
}

/**
 * 判断急停帧
 */
inline bool IsEmergencyFrame(FrameType frame_type) {
	return frame_type == FrameType::kEmergency;
}

// 连接类型
enum class ConnectionType : uint8_t {
	BLE = 0,    // 蓝牙连接
	AP = 1,     // AP 模式连接
	LAN = 2,    // 局域网连接
	UART = 3,   // 串口连接
};

#endif  // CONTROL_DATA_H
