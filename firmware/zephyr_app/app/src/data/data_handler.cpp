#include "data_handler.h"
#include <zephyr/logging/log.h>
#include <cstring>

LOG_MODULE_REGISTER(data_handler);

// 线程栈
K_THREAD_STACK_DEFINE(data_handler_stack, 2048);

// 控制数据回调（由 DataHandler 调用）
void OnControlDataCallback(const uint8_t* data, uint16_t len, ConnectionType source) {
	if (len < kControlDataLen) {
		LOG_WRN("控制数据过短: %u", len);
		return;
	}

	// 解析控制数据
	ControlData control_data;
	memcpy(&control_data, data, sizeof(ControlData));

	if (control_data.header == static_cast<uint8_t>(FrameType::kEmergency)) {
		LOG_WRN("收到急停指令，来源: %u!", static_cast<uint8_t>(source));
		// TODO: 执行急停逻辑
	} else {
		LOG_DBG("控制数据: L=(%d,%d) R=(%d,%d) buttons=0x%08X",
				(int)control_data.left_x, (int)control_data.left_y,
				(int)control_data.right_x, (int)control_data.right_y,
				(unsigned int)control_data.buttons);
	}

	// 转发控制数据到 MCU（通过 UART）
	g_serial.SendData(data, len);
}

// 用户数据回调（由 DataHandler 调用）
void OnUserDataCallback(const uint8_t* data, uint16_t len, ConnectionType source) {
	LOG_INF("收到用户数据: %u 字节，来源: %u", len, static_cast<uint8_t>(source));

	// 回传用户数据到 App（通过 BLE TX）
	// 构造响应包：帧头 + 长度 + 数据
	uint8_t response[2 + 255];
	response[0] = static_cast<uint8_t>(FrameType::kUserData);
	response[1] = static_cast<uint8_t>(len);
	if (len > 0 && len <= 255) {
		memcpy(&response[2], data, len);
		g_ble.SendData(response, 2 + len);
	}

	// 可选：转发用户数据到 MCU（通过 UART）
	// g_serial.SendData(data, len);
}

DataHandler::DataHandler()
	: control_callback_(nullptr), user_callback_(nullptr) {
	// 初始化消息队列
	k_msgq_init(&msgq_, msgq_buffer_, sizeof(TransportMessage), kMessageQueueSize);
}

int DataHandler::Init() {
	// 创建处理线程
	thread_tid_ = k_thread_create(&thread_data_, data_handler_stack,
								  K_THREAD_STACK_SIZEOF(data_handler_stack),
								  ThreadEntry, this, nullptr, nullptr,
								  5,  // 优先级
								  0,  // 选项
								  K_NO_WAIT);

	if (thread_tid_ == nullptr) {
		LOG_ERR("创建数据处理线程失败");
		return -1;
	}

	LOG_INF("DataHandler 已初始化");
	return 0;
}

void DataHandler::SubmitData(ConnectionType source, const uint8_t* data, size_t len) {
	if (data == nullptr || len == 0 || len > kMaxMessageSize) {
		LOG_WRN("无效的数据提交: len=%zu", len);
		return;
	}

	// 创建消息
	TransportMessage msg;
	msg.source = static_cast<uint8_t>(source);
	msg.length = static_cast<uint16_t>(len);
	memcpy(msg.data, data, len);

	// 发送到消息队列（非阻塞，避免阻塞传输层）
	int ret = k_msgq_put(&msgq_, &msg, K_NO_WAIT);
	if (ret != 0) {
		LOG_WRN("消息队列已满，数据丢弃");
	}
}

void DataHandler::SetControlDataCallback(ControlDataCallback callback) {
	control_callback_ = callback;
}

void DataHandler::SetUserDataCallback(UserDataCallback callback) {
	user_callback_ = callback;
}

void DataHandler::ThreadEntry(void* p1, void* p2, void* p3) {
	auto* self = static_cast<DataHandler*>(p1);
	self->ProcessLoop();
}

void DataHandler::ProcessLoop() {
	TransportMessage msg;

	while (true) {
		// 等待消息
		int ret = k_msgq_get(&msgq_, &msg, K_FOREVER);
		if (ret != 0) {
			continue;
		}

		// 路由消息
		RouteMessage(msg);
	}
}

void DataHandler::RouteMessage(const TransportMessage& msg) {
	ConnectionType source = static_cast<ConnectionType>(msg.source);

	if (msg.length == 0) {
		return;
	}

	// 检查帧头
	uint8_t frame_header = msg.data[0];

	switch (static_cast<FrameType>(frame_header)) {
		case FrameType::kControlData:
		case FrameType::kEmergency:
			// 控制数据：固定 9 字节
			if (msg.length >= kControlDataLen) {
				LOG_DBG("路由控制数据，来源: %u", msg.source);

				// 通过处理器处理
				control_handler_.ProcessData(msg.data, msg.length, source);

				// 调用回调
				if (control_callback_) {
					control_callback_(msg.data, msg.length, source);
				}
			} else {
				LOG_WRN("控制数据过短: %u", msg.length);
			}
			break;

		case FrameType::kUserData:
			// 用户数据：变长（帧头 + 长度 + 载荷）
			if (msg.length >= 2) {
				uint8_t payload_len = msg.data[1];
				size_t expected_len = 2 + payload_len;

				if (msg.length >= expected_len) {
					LOG_DBG("路由用户数据，来源: %u，长度: %u", msg.source, payload_len);

					// 通过处理器处理
					user_handler_.ProcessData(&msg.data[2], payload_len, source);

					// 调用回调
					if (user_callback_) {
						user_callback_(&msg.data[2], payload_len, source);
					}
				} else {
					LOG_WRN("用户数据不完整: 期望 %zu 字节，实际 %u 字节", expected_len, msg.length);
				}
			}
			break;

		default:
			LOG_WRN("未知帧头: 0x%02X", frame_header);
			break;
	}
}
