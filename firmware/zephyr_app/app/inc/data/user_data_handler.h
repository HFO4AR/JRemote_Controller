#ifndef USER_DATA_HANDLER_H
#define USER_DATA_HANDLER_H

#include <zephyr/kernel.h>
#include <cstdint>
#include "control_data.h"

// 发送数据函数指针类型（用于回传数据给 App）
using SendDataFunc = int (*)(const uint8_t* data, uint16_t len);

// 用户数据处理器 - 处理用户数据并发送响应给 App
class UserDataHandler {
public:
	UserDataHandler();
	~UserDataHandler() = default;

	// 处理用户数据
	// data: 用户数据载荷指针（不含帧头和长度）
	// len: 载荷长度
	// source: 接收数据的连接类型
	void ProcessData(const uint8_t* data, uint16_t len, ConnectionType source);

	// 设置发送数据函数（用于回传数据给 App）
	void SetSendDataFunc(SendDataFunc func) { send_data_func_ = func; }

private:
	SendDataFunc send_data_func_;
};

#endif  // USER_DATA_HANDLER_H
