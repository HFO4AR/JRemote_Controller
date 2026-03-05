# Zephyr RTOS 线程封装规范

> 参考项目: `/home/yuki/project/mammoth`

## 线程创建模式

### 1. 线程栈定义

在类的头文件中声明线程栈和线程控制块：

```cpp
private:
    k_thread_stack_t* stack_;      // 线程栈指针
    size_t stack_size_;             // 线程栈大小
    struct k_thread thread_data_;   // 线程控制块
```

在 cpp 文件中定义线程栈（通常在 main.cpp 或全局作用域）：

```cpp
// 线程栈大小根据任务复杂度定义
K_THREAD_STACK_DEFINE(thread_name_stack_area, 4096);  // 4KB
```

### 2. 构造函数

构造函数接收线程栈和栈大小参数：

```cpp
class MyClass {
public:
    MyClass(param1, param2, k_thread_stack_t* stack, size_t stack_size)
        : param1_(param1), param2_(param2),
          stack_(stack), stack_size_(stack_size) {}
private:
    k_thread_stack_t* stack_;
    size_t stack_size_;
    struct k_thread thread_data_;
};
```

### 3. 初始化函数中创建线程

```cpp
int MyClass::Init() {
    // 其他初始化...

    // 创建线程
    k_thread_create(&thread_data_,
                    stack_,
                    stack_size_,
                    ThreadEntry,      // 静态成员函数
                    this,             // 作为参数传入
                    nullptr,
                    nullptr,
                    5,               // 优先级 (数值越小优先级越高)
                    0,               // 选项
                    K_NO_WAIT);      // 启动选项

    return 0;
}
```

### 4. 线程入口函数

使用静态成员函数作为入口，并在循环中运行：

```cpp
void MyClass::ThreadEntry(void* p1, void* p2, void* p3) {
    MyClass* self = static_cast<MyClass*>(p1);

    while (true) {
        self->Process();
        k_msleep(10);  // 线程周期
    }
}
```

## 完整示例

### 头文件 (my_class.h)

```cpp
#ifndef MY_CLASS_H
#define MY_CLASS_H

#include <zephyr/kernel.h>

class MyClass {
public:
    // 构造函数接收线程栈
    MyClass(param_type param, k_thread_stack_t* stack, size_t stack_size);

    // 初始化函数，创建线程
    int Init();

private:
    // 线程入口 (静态成员函数)
    static void ThreadEntry(void* p1, void* p2, void* p3);

    // 处理函数
    void Process();

    // 成员变量
    param_type param_;
    k_thread_stack_t* stack_;
    size_t stack_size_;
    struct k_thread thread_data_;
};

#endif
```

### 源文件 (my_class.cpp)

```cpp
#include "my_class.h"

MyClass::MyClass(param_type param, k_thread_stack_t* stack, size_t stack_size)
    : param_(param), stack_(stack), stack_size_(stack_size) {
}

int MyClass::Init() {
    // 初始化其他硬件/软件资源...

    // 创建线程
    k_thread_create(&thread_data_,
                    stack_,
                    stack_size_,
                    ThreadEntry,
                    this, nullptr, nullptr,
                    5,  // 优先级
                    0,  // 选项
                    K_NO_WAIT);

    return 0;
}

void MyClass::ThreadEntry(void* p1, void* p2, void* p3) {
    MyClass* self = static_cast<MyClass*>(p1);

    while (true) {
        self->Process();
        k_msleep(10);  // 10ms 周期
    }
}

void MyClass::Process() {
    // 实际的处理逻辑
}
```

### main.cpp 中使用

```cpp
// 定义线程栈
K_THREAD_STACK_DEFINE(my_class_stack_area, 2048);

// 创建全局实例
MyClass my_class(param, my_class_stack_area,
                 K_THREAD_STACK_SIZEOF(my_class_stack_area));

int main() {
    // 初始化
    my_class.Init();

    while (true) {
        k_sleep(K_FOREVER);
    }
}
```

## 线程优先级参考

| 优先级 | 用途 |
|--------|------|
| 0-5 | 关键任务 (如电机控制) |
| 5-10 | 实时通信 (UART, CAN) |
| 10-20 | 普通任务 |
| 20+ | 低优先级任务 |

## 注意事项

1. **静态成员函数**: 线程入口必须是静态成员函数，因为 C 风格的线程 API 不支持 non-static 成员函数
2. **栈大小**: 根据任务复杂度选择合适的栈大小，建议 1024-4096
3. **周期选择**: 根据任务实时性要求选择合适的休眠时间
4. **线程安全**: 如需在多线程间共享数据，考虑使用 mutex 或其他同步机制

## 参考来源

- `/home/yuki/project/mammoth/src/main.cpp`
- `/home/yuki/project/mammoth/inc/chassis.h`
- `/home/yuki/project/mammoth/src/chassis.cpp`
- `/home/yuki/project/mammoth/inc/remote.h`
- `/home/yuki/project/mammoth/inc/upper_computer_communication.h`
