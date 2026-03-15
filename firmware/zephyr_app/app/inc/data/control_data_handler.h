#ifndef CONTROL_DATA_HANDLER_H
#define CONTROL_DATA_HANDLER_H

#include <zephyr/kernel.h>
#include <cstdint>
#include "control_data.h"

// 控制数据处理器 - 处理控制数据（9字节）并转发到 UART
class ControlDataHandler {
public:
	ControlDataHandler();
	~ControlDataHandler() = default;

	// 处理控制数据
	// data: 控制数据指针（9字节）
	// len: 数据长度
	// source: 接收数据的连接类型
	void ProcessData(const uint8_t* data, uint16_t len, ConnectionType source);

	// 获取最新的控制数据
	const ControlData& GetLatestData() const { return latest_data_; }

	// 检查是否收到急停指令
	bool IsEmergencyStop() const { return is_emergency_; }

	// 清除急停标志
	void ClearEmergencyStop() { is_emergency_ = false; }

	// 获取最新数据的来源
	ConnectionType GetSource() const { return latest_source_; }

private:
	ControlData latest_data_;
	ConnectionType latest_source_;
	bool is_emergency_;
};

#endif  // CONTROL_DATA_HANDLER_H
