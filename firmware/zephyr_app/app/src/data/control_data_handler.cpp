#include "control_data_handler.h"
#include <zephyr/logging/log.h>
#include <cstring>

LOG_MODULE_REGISTER(control_data_handler);

ControlDataHandler::ControlDataHandler()
	: latest_source_(ConnectionType::BLE), is_emergency_(false) {
	memset(&latest_data_, 0, sizeof(latest_data_));
}

void ControlDataHandler::ProcessData(const uint8_t* data, uint16_t len, ConnectionType source) {
	if (data == nullptr || len < kControlDataLen) {
		LOG_WRN("无效的控制数据: len=%u", len);
		return;
	}

	// 复制数据到最新数据
	memcpy(&latest_data_, data, sizeof(ControlData));
	latest_source_ = source;

	// 检查是否为急停指令
	if (latest_data_.header == static_cast<uint8_t>(FrameType::kEmergency)) {
		is_emergency_ = true;
		LOG_WRN("收到急停指令，来源: %u", static_cast<uint8_t>(source));
	} else {
		LOG_DBG("控制数据: L=(%d,%d) R=(%d,%d) buttons=0x%08X",
				(int)latest_data_.left_x, (int)latest_data_.left_y,
				(int)latest_data_.right_x, (int)latest_data_.right_y,
				(unsigned int)latest_data_.buttons);
	}
}
