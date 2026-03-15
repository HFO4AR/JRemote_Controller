#include "user_data_handler.h"
#include <zephyr/logging/log.h>

LOG_MODULE_REGISTER(user_data_handler);

UserDataHandler::UserDataHandler()
	: send_data_func_(nullptr) {
}

void UserDataHandler::ProcessData(const uint8_t* data, uint16_t len, ConnectionType source) {
	LOG_INF("收到用户数据: %u 字节，来源: %u", len, static_cast<uint8_t>(source));

	// 调试日志输出数据
	if (len > 0) {
		LOG_HEXDUMP_DBG(data, len, "用户数据:");
	}

	// TODO: 在此添加特定的用户数据处理逻辑
	// 目前仅记录数据

	// 示例：如果设置了发送函数，则回传数据给 App
	// if (send_data_func_ && len > 0) {
	//     uint8_t response[2 + 255];
	//     response[0] = static_cast<uint8_t>(FrameType::kUserData);
	//     response[1] = static_cast<uint8_t>(len);
	//     memcpy(&response[2], data, len);
	//     send_data_func_(response, 2 + len);
	// }
}
