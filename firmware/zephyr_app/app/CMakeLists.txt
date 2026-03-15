# JRemote Controller - Zephyr 固件
# CMakeLists.txt

cmake_minimum_required(VERSION 3.20.0)

find_package(Zephyr REQUIRED)

project(jremote_controller)

# 设置 C++ 标准
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 包含目录
target_include_directories(app PRIVATE
    ${CMAKE_SOURCE_DIR}/inc
    ${CMAKE_SOURCE_DIR}/inc/peripheral
    ${CMAKE_SOURCE_DIR}/inc/data
)

# 源文件 (C++)
target_sources(app PRIVATE
    # 主程序
    src/main.cpp

    # 外设模块
    src/peripheral/ble.cpp
    src/peripheral/ws2812_led.cpp

    # 数据处理模块
    src/data/data_handler.cpp
    src/data/control_data_handler.cpp
    src/data/user_data_handler.cpp
)
