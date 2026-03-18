#ifndef CONTROL_DATA_HANDLER_H
#define CONTROL_DATA_HANDLER_H

#include <zephyr/kernel.h>
#include <cstdint>
#include "control_data.h"

// 当前激活的帧格式
enum class ActiveFrameFormat : uint8_t {
	kMin = 0,     // 最简帧 6字节
	kStandard = 1, // 标准帧 9字节
	k16Bit = 2,    // 16位帧 17字节
};

// 控制数据处理器 - 处理控制数据并转发到 UART
class ControlDataHandler {
public:
	ControlDataHandler();
	~ControlDataHandler() = default;

	// 处理控制数据
	// data: 控制数据指针（包含帧头）
	// len: 数据长度
	// source: 接收数据的连接类型
	void ProcessData(const uint8_t* data, uint16_t len, ConnectionType source);

	// 获取当前激活的帧格式
	ActiveFrameFormat GetActiveFrameFormat() const { return active_frame_format_; }

	// 检查是否收到急停指令
	bool IsEmergencyStop() const { return is_emergency_; }

	// 清除急停标志
	void ClearEmergencyStop() { is_emergency_ = false; }

	// 获取最新数据的来源
	ConnectionType GetSource() const { return latest_source_; }

	// 获取最新控制数据的辅助方法
	// 返回摇杆值和按钮状态（自动适配当前帧格式）
	void GetControlValues(int16_t& left_x, int16_t& left_y, int16_t& right_x, int16_t& right_y, uint32_t& buttons) const;

private:
	// 根据帧头确定帧格式
	ActiveFrameFormat DetermineFrameFormat(uint8_t header) const;

	// 转换手机帧为 UART 帧并发送
	// 返回实际发送的字节数，0 表示失败
	size_t ConvertAndSendToUart(const uint8_t* data, size_t len, uint8_t route_id);

	ActiveFrameFormat active_frame_format_;
	ConnectionType latest_source_;
	bool is_emergency_;

	// 存储最新数据（使用最大尺寸）
	uint8_t latest_data_[kControlData16MultiLen];
	uint8_t latest_data_len_;
	uint8_t latest_route_id_;
};

#endif  // CONTROL_DATA_HANDLER_H
