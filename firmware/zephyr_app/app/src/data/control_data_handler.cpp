#include "control_data_handler.h"
#include <zephyr/logging/log.h>
#include <cstring>

#include "data_handler.h"

LOG_MODULE_REGISTER(control_data_handler);

ControlDataHandler::ControlDataHandler()
	: active_frame_format_(ActiveFrameFormat::kStandard),
	  latest_source_(ConnectionType::BLE),
	  is_emergency_(false),
	  latest_data_len_(0),
	  latest_route_id_(0) {
	memset(latest_data_, 0, sizeof(latest_data_));
}

void ControlDataHandler::ProcessData(const uint8_t* data, uint16_t len, ConnectionType source) {
	if (data == nullptr || len < 1) {
		LOG_WRN("无效的控制数据: len=%u", len);
		return;
	}

	uint8_t header = data[0];
	latest_source_ = source;

	// 检查是否为急停指令
	if (header == static_cast<uint8_t>(FrameType::kEmergency)) {
		is_emergency_ = true;
		LOG_WRN("收到急停指令，来源: %u", static_cast<uint8_t>(source));

		// 急停直接发送 UART 急停帧 (0xC4)
		uint8_t uart_emergency = static_cast<uint8_t>(UartFrameType::kUartEmergency);
		g_serial.SendData(&uart_emergency, 1);
		return;
	}

	// 确定帧格式
	ActiveFrameFormat frame_format = DetermineFrameFormat(header);
	active_frame_format_ = frame_format;

	// 判断是否为多节点帧
	bool is_multi_node = IsMultiNodeFrame(static_cast<FrameType>(header));

	// 提取路由 ID（多节点帧的第 2 字节）
	uint8_t route_id = 0;
	const uint8_t* payload_data = data;
	size_t payload_len = len;

	if (is_multi_node) {
		if (len < 2) {
			LOG_WRN("多节点帧数据过短: %u", len);
			return;
		}
		route_id = data[1];
		latest_route_id_ = route_id;
		payload_data = &data[2];
		payload_len = len - 2;
		LOG_DBG("多节点帧: header=0x%02X, route_id=%u", header, route_id);
	} else {
		latest_route_id_ = 0;
		payload_data = &data[1];  // 跳过帧头
		payload_len = len - 1;
	}

	// 保存最新数据（包含帧头）
	if (len <= sizeof(latest_data_)) {
		memcpy(latest_data_, data, len);
		latest_data_len_ = len;
	}

	// 根据帧格式确定期望的 payload 长度
	size_t expected_payload_len = 0;
	switch (frame_format) {
		case ActiveFrameFormat::kMin:
			expected_payload_len = 5;  // 4 摇杆(1字节) + 1 按钮
			break;
		case ActiveFrameFormat::kStandard:
			expected_payload_len = 8;  // 4 摇杆(1字节) + 4 按钮
			break;
		case ActiveFrameFormat::k16Bit:
			expected_payload_len = 12;  // 4 摇杆(2字节*4=8) + 4 按钮
			break;
	}

	if (payload_len < expected_payload_len) {
		LOG_WRN("帧数据不完整: format=%u, expected=%zu, actual=%zu",
				static_cast<uint8_t>(frame_format), expected_payload_len, payload_len);
		return;
	}

	// 日志输出（根据帧格式显示）
	// 注意：Android 发送的是无符号值 (0~255 或 0~65535)，需要转换回有符号值显示
	switch (frame_format) {
		case ActiveFrameFormat::kMin: {
			// MIN: 4 摇杆(uint8 0~255) + 1 按钮
			// 转换：无符号 -> 有符号 (0->-128, 128->0, 255->127)
			int8_t lx = static_cast<int8_t>(payload_data[0] - 128);
			int8_t ly = static_cast<int8_t>(payload_data[1] - 128);
			int8_t rx = static_cast<int8_t>(payload_data[2] - 128);
			int8_t ry = static_cast<int8_t>(payload_data[3] - 128);
			LOG_INF("控制数据: MIN帧, route_id=%u, L=(%d,%d), R=(%d,%d), buttons=0x%02X",
					route_id, lx, ly, rx, ry,
					(unsigned int)payload_data[4]);
			break;
		}
		case ActiveFrameFormat::kStandard: {
			// 标准: 4 摇杆(uint8 0~255) + 4 按钮
			int8_t lx = static_cast<int8_t>(payload_data[0] - 128);
			int8_t ly = static_cast<int8_t>(payload_data[1] - 128);
			int8_t rx = static_cast<int8_t>(payload_data[2] - 128);
			int8_t ry = static_cast<int8_t>(payload_data[3] - 128);
			uint32_t bt = payload_data[4] | (payload_data[5] << 8) | (payload_data[6] << 16) | (payload_data[7] << 24);
			LOG_INF("控制数据: 标准帧, route_id=%u, L=(%d,%d), R=(%d,%d), buttons=0x%08X",
					route_id, lx, ly, rx, ry, bt);
			break;
		}
		case ActiveFrameFormat::k16Bit: {
			// 16位: 4 摇杆(uint16 big-endian 0~65535) + 4 按钮
			uint16_t lx_raw = (static_cast<uint16_t>(payload_data[0]) << 8) | payload_data[1];
			uint16_t ly_raw = (static_cast<uint16_t>(payload_data[2]) << 8) | payload_data[3];
			uint16_t rx_raw = (static_cast<uint16_t>(payload_data[4]) << 8) | payload_data[5];
			uint16_t ry_raw = (static_cast<uint16_t>(payload_data[6]) << 8) | payload_data[7];
			int16_t lx = static_cast<int16_t>(lx_raw) - 32768;
			int16_t ly = static_cast<int16_t>(ly_raw) - 32768;
			int16_t rx = static_cast<int16_t>(rx_raw) - 32768;
			int16_t ry = static_cast<int16_t>(ry_raw) - 32768;
			uint32_t bt = payload_data[8] | (payload_data[9] << 8) | (payload_data[10] << 16) | (payload_data[11] << 24);
			LOG_INF("控制数据: 16位帧, route_id=%u, L=(%d,%d), R=(%d,%d), buttons=0x%08X",
					route_id, lx, ly, rx, ry, bt);
			break;
		}
	}


	// 转换为 UART 帧并发送
	size_t sent = ConvertAndSendToUart(data, len, route_id);
	if (sent > 0) {
		LOG_DBG("已转发到 UART: %zu 字节", sent);
	} else {
		LOG_WRN("UART 转发失败");
	}
}

ActiveFrameFormat ControlDataHandler::DetermineFrameFormat(uint8_t header) const {
	switch (static_cast<FrameType>(header)) {
		case FrameType::kControlDataMin:
		case FrameType::kControlDataMinMulti:
			return ActiveFrameFormat::kMin;
		case FrameType::kControlData:
		case FrameType::kControlDataMulti:
			return ActiveFrameFormat::kStandard;
		case FrameType::kControlData16:
		case FrameType::kControlData16Multi:
			return ActiveFrameFormat::k16Bit;
		default:
			return ActiveFrameFormat::kStandard;  // 默认标准帧
	}
}

size_t ControlDataHandler::ConvertAndSendToUart(const uint8_t* data, size_t len, uint8_t route_id) {
	// 数据转换规则：
	// 0xA0 (MIN) -> 0xC0 (UART MIN)
	// 0xAA (标准) -> 0xC1 (UART 标准)
	// 0xAC (16位) -> 0xC2 (UART 16位)
	// 0xB0 (多节点MIN) -> 0xC0 + 路由 (通过对应 UART 发送)
	// 0xB2 (多节点标准) -> 0xC1 + 路由
	// 0xB3 (多节点16位) -> 0xC2 + 路由
	// 0xEE (急停) -> 0xC4

	uint8_t header = data[0];
	bool is_multi_node = IsMultiNodeFrame(static_cast<FrameType>(header));

	// 确定 UART 帧头
	UartFrameType uart_header;
	switch (static_cast<FrameType>(header)) {
		case FrameType::kControlDataMin:
		case FrameType::kControlDataMinMulti:
			uart_header = UartFrameType::kUartControlMin;
			break;
		case FrameType::kControlData:
		case FrameType::kControlDataMulti:
			uart_header = UartFrameType::kUartControl;
			break;
		case FrameType::kControlData16:
		case FrameType::kControlData16Multi:
			uart_header = UartFrameType::kUartControl16;
			break;
		default:
			return 0;
	}

	// 构建 UART 帧
	uint8_t uart_frame[sizeof(latest_data_) + 1];  // +1 for UART header
	size_t uart_frame_len = 0;

	if (is_multi_node) {
		// 多节点帧：0xC0/C1/C2 + 数据(去掉手机帧头，保留路由ID)
		// 格式: [UART帧头][路由ID][摇杆数据...][按钮数据...]
		if (len < 2) return 0;

		uart_frame[0] = static_cast<uint8_t>(uart_header);
		uart_frame[1] = route_id;  // 路由 ID
		memcpy(&uart_frame[2], &data[2], len - 2);  // 跳过手机帧头和路由ID
		uart_frame_len = len - 1;  // 原长度 - 手机帧头
	} else {
		// 普通帧：0xC0/C1/C2 + 数据(去掉手机帧头)
		// 格式: [UART帧头][摇杆数据...][按钮数据...]
		uart_frame[0] = static_cast<uint8_t>(uart_header);
		memcpy(&uart_frame[1], &data[1], len - 1);  // 跳过手机帧头
		uart_frame_len = len;  // 原长度 - 手机帧头(1) + UART帧头(1) = 原长度
	}

	// TODO: 路由逻辑 - 根据 route_id 选择对应的 UART 接口
	// 目前仅支持单个 UART (g_serial)
	// 未来扩展: UART0 -> 节点1, UART1 -> 节点2, etc.

	if (g_serial.SendData(uart_frame, uart_frame_len)) {
		return uart_frame_len;
	}

	return 0;
}

void ControlDataHandler::GetControlValues(int16_t& left_x, int16_t& left_y, int16_t& right_x,
										  int16_t& right_y, uint32_t& buttons) const {
	// 从保存的数据中提取控制值（根据帧格式）
	// 注意：Android 发送的是无符号值 (0~255 或 0~65535)
	if (latest_data_len_ < 2) {
		left_x = left_y = right_x = right_y = 0;
		buttons = 0;
		return;
	}

	uint8_t header = latest_data_[0];
	bool is_multi_node = IsMultiNodeFrame(static_cast<FrameType>(header));
	size_t data_offset = is_multi_node ? 2 : 1;  // 跳过帧头和路由ID(如果是多节点)

	if (latest_data_len_ < data_offset + 5) {
		left_x = left_y = right_x = right_y = 0;
		buttons = 0;
		return;
	}

	const uint8_t* payload = &latest_data_[data_offset];

	switch (active_frame_format_) {
		case ActiveFrameFormat::kMin: {
			// 6字节: 4 摇杆(uint8 0~255) + 1 按钮
			// 转换：无符号 -> 有符号 (0->-128, 128->0, 255->127)
			left_x = static_cast<int8_t>(payload[0] - 128);
			left_y = static_cast<int8_t>(payload[1] - 128);
			right_x = static_cast<int8_t>(payload[2] - 128);
			right_y = static_cast<int8_t>(payload[3] - 128);
			buttons = payload[4];
			break;
		}
		case ActiveFrameFormat::kStandard: {
			// 9字节: 4 摇杆(uint8 0~255) + 4 按钮
			left_x = static_cast<int8_t>(payload[0] - 128);
			left_y = static_cast<int8_t>(payload[1] - 128);
			right_x = static_cast<int8_t>(payload[2] - 128);
			right_y = static_cast<int8_t>(payload[3] - 128);
			// 按钮是 4 字节小端序
			buttons = payload[4] | (payload[5] << 8) | (payload[6] << 16) | (payload[7] << 24);
			break;
		}
		case ActiveFrameFormat::k16Bit: {
			// 13字节: 4 摇杆(uint16 big-endian 0~65535) + 4 按钮
			uint16_t lx_raw = (static_cast<uint16_t>(payload[0]) << 8) | payload[1];
			uint16_t ly_raw = (static_cast<uint16_t>(payload[2]) << 8) | payload[3];
			uint16_t rx_raw = (static_cast<uint16_t>(payload[4]) << 8) | payload[5];
			uint16_t ry_raw = (static_cast<uint16_t>(payload[6]) << 8) | payload[7];
			left_x = static_cast<int16_t>(lx_raw) - 32768;
			left_y = static_cast<int16_t>(ly_raw) - 32768;
			right_x = static_cast<int16_t>(rx_raw) - 32768;
			right_y = static_cast<int16_t>(ry_raw) - 32768;
			// 按钮是 4 字节小端序
			buttons = payload[8] | (payload[9] << 8) | (payload[10] << 16) | (payload[11] << 24);
			break;
		}
		default:
			left_x = left_y = right_x = right_y = 0;
			buttons = 0;
			break;
	}
}