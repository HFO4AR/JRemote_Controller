# JRemote Controller - Zephyr Firmware
# CMakeLists.txt

cmake_minimum_required(VERSION 3.20.0)

find_package(Zephyr REQUIRED)

project(jremote_controller)

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Source files (C++)
target_sources(app PRIVATE
    src/main.cpp
)
