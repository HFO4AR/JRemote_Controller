#include "data_handler.h"
#include <zephyr/logging/log.h>
#include <cstring>

LOG_MODULE_REGISTER(data_handler);

// 线程栈
K_THREAD_STACK_DEFINE(data_handler_stack, 2048);

// 控制数据回调（由 DataHandler 调用）
void OnControlDataCallback(const uint8_t* data, uint16_t len, ConnectionType source) {
	uint8_t header = data[0];

	// 检查急停指令
	if (header == static_cast<uint8_t>(FrameType::kEmergency)) {
		LOG_WRN("收到急停指令，来源: %u!", static_cast<uint8_t>(source));
		// 急停处理已在 ControlDataHandler 中完成
		return;
	}

	// 获取帧长度
	size_t expected_len = GetFrameLength(static_cast<FrameType>(header));
	if (expected_len == 0) {
		LOG_WRN("未知帧头: 0x%02X", header);
		return;
	}

	if (len < expected_len) {
		LOG_WRN("控制数据过短: %u, expected %zu", len, expected_len);
		return;
	}

	// 判断是否为多节点帧（多节点帧有额外的路由字节）
	bool is_multi_node = IsMultiNodeFrame(static_cast<FrameType>(header));
	size_t data_offset = is_multi_node ? 2 : 1;

	if (len < data_offset + 5) {
		LOG_WRN("数据长度不足: len=%u, offset=%zu", len, data_offset);
		return;
	}

	// 根据帧格式解析并日志输出
	// 注意：Android 发送的是无符号值 (0~255 或 0~65535)，需要转换回有符号值显示
	switch (static_cast<FrameType>(header)) {
		case FrameType::kControlDataMin:
		case FrameType::kControlDataMinMulti: {
			// 6/10字节最简帧: 帧头(+路由) + 4摇杆(uint8) + 1按钮
			int8_t lx = static_cast<int8_t>(data[data_offset] - 128);
			int8_t ly = static_cast<int8_t>(data[data_offset + 1] - 128);
			int8_t rx = static_cast<int8_t>(data[data_offset + 2] - 128);
			int8_t ry = static_cast<int8_t>(data[data_offset + 3] - 128);
			uint8_t bt = data[data_offset + 4];
			LOG_DBG("MIN帧: L=(%d,%d) R=(%d,%d) buttons=0x%02X",
					lx, ly, rx, ry, bt);
			break;
		}
		case FrameType::kControlData:
		case FrameType::kControlDataMulti: {
			// 9/11字节标准帧: 帧头(+路由) + 4摇杆(uint8) + 4按钮
			int8_t lx = static_cast<int8_t>(data[data_offset] - 128);
			int8_t ly = static_cast<int8_t>(data[data_offset + 1] - 128);
			int8_t rx = static_cast<int8_t>(data[data_offset + 2] - 128);
			int8_t ry = static_cast<int8_t>(data[data_offset + 3] - 128);
			uint32_t bt = data[data_offset + 4] |
						  (data[data_offset + 5] << 8) |
						  (data[data_offset + 6] << 16) |
						  (data[data_offset + 7] << 24);
			LOG_DBG("标准帧: L=(%d,%d) R=(%d,%d) buttons=0x%08X",
					lx, ly, rx, ry, bt);
			break;
		}
		case FrameType::kControlData16:
		case FrameType::kControlData16Multi: {
			// 13/14字节16位帧: 帧头(+路由) + 4摇杆(uint16 big-endian) + 4按钮
			uint16_t lx_raw = (static_cast<uint16_t>(data[data_offset]) << 8) | data[data_offset + 1];
			uint16_t ly_raw = (static_cast<uint16_t>(data[data_offset + 2]) << 8) | data[data_offset + 3];
			uint16_t rx_raw = (static_cast<uint16_t>(data[data_offset + 4]) << 8) | data[data_offset + 5];
			uint16_t ry_raw = (static_cast<uint16_t>(data[data_offset + 6]) << 8) | data[data_offset + 7];
			int16_t lx = static_cast<int16_t>(lx_raw) - 32768;
			int16_t ly = static_cast<int16_t>(ly_raw) - 32768;
			int16_t rx = static_cast<int16_t>(rx_raw) - 32768;
			int16_t ry = static_cast<int16_t>(ry_raw) - 32768;
			uint32_t bt = data[data_offset + 8] |
						  (data[data_offset + 9] << 8) |
						  (data[data_offset + 10] << 16) |
						  (data[data_offset + 11] << 24);
			LOG_DBG("16位帧: L=(%d,%d) R=(%d,%d) buttons=0x%08X",
					lx, ly, rx, ry, bt);
			break;
		}
		default:
			break;
	}
}

// 用户数据回调（由 DataHandler 调用）
void OnUserDataCallback(const uint8_t* data, uint16_t len, ConnectionType source) {
	LOG_INF("收到用户数据: %u 字节，来源: %u", len, static_cast<uint8_t>(source));

	if (len == 0) return;

	// 解析子类型（用户数据的第 1 字节是子类型）
	uint8_t subtype = data[0];
	LOG_INF("用户数据子类型: 0x%02X", subtype);

	// 回传用户数据到 App（通过 BLE TX）
	// 构造响应包：帧头 + 长度 + 数据
	uint8_t response[2 + 255];
	response[0] = static_cast<uint8_t>(FrameType::kUserData);
	response[1] = static_cast<uint8_t>(len);
	if (len <= 255) {
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
	FrameType frame_type = static_cast<FrameType>(frame_header);

	// 首先判断是否为控制数据帧
	if (IsControlFrame(frame_type)) {
		// 控制数据帧
		size_t expected_len = GetFrameLength(frame_type);
		if (expected_len == 0) {
			LOG_WRN("未知控制帧头: 0x%02X", frame_header);
			return;
		}

		if (msg.length >= expected_len) {
			LOG_DBG("路由控制数据: frame=0x%02X, len=%u, source=%u",
					frame_header, msg.length, msg.source);

			// 通过处理器处理（转换并转发到 UART）
			control_handler_.ProcessData(msg.data, msg.length, source);

			// 调用回调
			if (control_callback_) {
				control_callback_(msg.data, msg.length, source);
			}
		} else {
			LOG_WRN("控制数据过短: len=%u, expected=%zu", msg.length, expected_len);
		}
		return;
	}

	// 判断是否为急停帧
	if (IsEmergencyFrame(frame_type)) {
		LOG_WRN("路由急停指令，来源: %u", msg.source);

		// 通过处理器处理
		control_handler_.ProcessData(msg.data, msg.length, source);

		// 调用回调
		if (control_callback_) {
			control_callback_(msg.data, msg.length, source);
		}
		return;
	}

	// 判断是否为用户数据帧
	if (frame_type == FrameType::kUserData || frame_type == FrameType::kUserDataMulti) {
		// 用户数据：变长（帧头 + 长度 + 载荷）
		if (msg.length >= 2) {
			uint8_t payload_len = msg.data[1];
			size_t expected_len = 2 + payload_len;

			if (msg.length >= expected_len) {
				LOG_DBG("路由用户数据: frame=0x%02X, payload_len=%u, source=%u",
						frame_header, payload_len, msg.source);

				// 通过处理器处理
				user_handler_.ProcessData(&msg.data[2], payload_len, source);

				// 调用回调
				if (user_callback_) {
					user_callback_(&msg.data[2], payload_len, source);
				}
			} else {
				LOG_WRN("用户数据不完整: 期望 %zu 字节，实际 %u 字节", expected_len, msg.length);
			}
		} else {
			LOG_WRN("用户数据帧过短: %u", msg.length);
		}
		return;
	}

	// 未知帧头
	LOG_WRN("未知帧头: 0x%02X，来源: %u", frame_header, msg.source);
}
