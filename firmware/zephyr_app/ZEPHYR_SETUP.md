# Zephyr 开发环境配置

> 本目录用于 Zephyr 固件开发，目标硬件为 **ESP32**
>
> **开发语言：C++17**

## 项目结构

```
firmware/zephyr_app/
├── west.yml           # West manifest 配置
├── build.py           # 构建脚本
├── flash.py           # 烧录脚本
├── zephyr/            # Zephyr 核心库（west update 后生成）
├── modules/           # HAL 模块（west update 后生成）
├── .venv/            # Python 虚拟环境
└── app/              # 应用程序代码
    ├── CMakeLists.txt
    ├── prj.conf
    ├── Kconfig
    └── src/
        └── main.cpp
```

## 环境要求

- Python 3.10+
- CMake 3.20+
- Ninja Build 1.10+
- Zephyr SDK (zephyr-sdk)

## 快速开始

### 1. 创建虚拟环境并安装依赖

```bash
cd firmware/zephyr_app
python3 -m venv .venv
source .venv/bin/activate

# 安装 west 和其他 Python 依赖
pip install west pyelftools intelhex pyserial

# 安装 Zephyr 官方 Python 依赖 (重要！)
pip install -r JRemote_Controller/firmware/zephyr/scripts/requirements.txt
```

### 2. 初始化 Zephyr 项目

```bash
cd /home/yuki/project/JRemote_Controller/firmware
west init -l zephyr_app
```

### 3. 获取 Zephyr 源码

```bash
west update
```

### 4. 获取 ESP32 芯片二进制 blobs (重要！)

```bash
# 获取 hal_espressif 的二进制文件
west blobs fetch hal_espressif
```

## 构建和烧录

> **统一使用 build.py 和 flash.py 脚本**

### 构建固件

```bash
# 增量构建
python3 firmware/zephyr_app/build.py

# 清理后重新构建
python3 firmware/zephyr_app/build.py --clean
```

### 烧录固件

```bash
# 烧录固件
python3 firmware/zephyr_app/flash.py

# 擦除 Flash 后烧录
python3 firmware/zephyr_app/flash.py --erase
```

## 注意事项

- 开发语言为 **C++17**
- 板型名称：`esp32s3_devkitm/esp32s3/procpu`
- 固件输出位置：`firmware/build/zephyr/zephyr.bin`
