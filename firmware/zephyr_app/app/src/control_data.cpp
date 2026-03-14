#include "control_data.h"

ControlDataHandler::ControlDataHandler()
	: callback_(nullptr),
	  connection_type_(ConnectionType::BLE),
	  has_new_data_(false) {
	data_.header = kFrameHeaderNormal;
	data_.left_x = 0;
	data_.left_y = 0;
	data_.right_x = 0;
	data_.right_y = 0;
	data_.buttons = 0;
}

void ControlDataHandler::SetCallback(ControlDataCallback callback) {
	callback_ = callback;
}

bool ControlDataHandler::ProcessData(const uint8_t* data, uint16_t len) {
	// 检查数据长度
	if (len < sizeof(ControlData)) {
		return false;
	}

	// 复制数据
	const ControlData* raw = reinterpret_cast<const ControlData*>(data);

	// 验证帧头
	if (raw->header != kFrameHeaderNormal && raw->header != kFrameHeaderEmergency) {
		return false;
	}

	// 更新数据
	data_ = *raw;
	has_new_data_ = true;

	// 调用回调
	if (callback_) {
		callback_(data_);
	}

	return true;
}

ControlData ControlDataHandler::GetData() const {
	return data_;
}

ConnectionType ControlDataHandler::GetConnectionType() const {
	return connection_type_;
}

void ControlDataHandler::SetConnectionType(ConnectionType type) {
	connection_type_ = type;
}

bool ControlDataHandler::IsEmergencyStop() const {
	return data_.header == kFrameHeaderEmergency;
}

bool ControlDataHandler::HasNewData() const {
	return has_new_data_;
}

void ControlDataHandler::ClearNewDataFlag() {
	has_new_data_ = false;
}
