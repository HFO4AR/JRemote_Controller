#ifndef DATA_HANDLER_H
#define DATA_HANDLER_H

#include <zephyr/kernel.h>
#include <cstdint>
#include "control_data.h"
#include "control_data_handler.h"
#include "user_data_handler.h"

// 最大消息大小
constexpr size_t kMaxMessageSize = 255;

// 消息队列最大消息数
constexpr size_t kMessageQueueSize = 16;

// 传输消息结构
struct TransportMessage {
	uint8_t source;      // 来源: ConnectionType 枚举值
	uint16_t length;     // 数据长度
	uint8_t data[kMaxMessageSize];  // 数据内容
};

// 数据回调类型
using ControlDataCallback = void (*)(const uint8_t* data, uint16_t len, ConnectionType source);
using UserDataCallback = void (*)(const uint8_t* data, uint16_t len, ConnectionType source);

// DataHandler 类 - 将传输层数据路由到相应的处理器
class DataHandler {
public:
	DataHandler();
	~DataHandler() = default;

	// 初始化处理器（创建处理线程）
	int Init();

	// 提交数据（线程安全，由传输层调用）
	void SubmitData(ConnectionType source, const uint8_t* data, size_t len);

	// 设置不同数据类型的回调
	void SetControlDataCallback(ControlDataCallback callback);
	void SetUserDataCallback(UserDataCallback callback);

	// 获取处理器（可选的直接访问）
	ControlDataHandler& GetControlDataHandler() { return control_handler_; }
	UserDataHandler& GetUserDataHandler() { return user_handler_; }

private:
	// 消息队列（接收传输层数据）
	struct k_msgq msgq_;

	// 消息队列缓冲区
	char __aligned(4) msgq_buffer_[sizeof(TransportMessage) * kMessageQueueSize];

	// 处理线程
	k_tid_t thread_tid_;
	struct k_thread thread_data_;

	// 处理器（静态分配）
	ControlDataHandler control_handler_;
	UserDataHandler user_handler_;

	// 回调函数
	ControlDataCallback control_callback_;
	UserDataCallback user_callback_;

	// 线程入口（静态成员函数）
	static void ThreadEntry(void* p1, void* p2, void* p3);

	// 主处理循环
	void ProcessLoop();

	// 路由消息到相应的处理器
	void RouteMessage(const TransportMessage& msg);
};

#endif  // DATA_HANDLER_H
