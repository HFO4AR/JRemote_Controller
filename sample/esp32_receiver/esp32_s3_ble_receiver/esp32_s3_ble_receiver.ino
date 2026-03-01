/*
 * ESP32-S3 BLE 接收程序
 * 用于接收 JRemote Controller 遥控器的控制数据
 * 
 * 硬件: ESP32-S3
 * 协议: BLE (低功耗蓝牙)
 */

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

#define SERVICE_UUID           "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID_RX "beb5483e-36e1-4688-b7f5-ea07361b26a8"
#define CHARACTERISTIC_UUID_TX "6e400002-b5a3-f393-e0a9-e50e24dcca9e"

const char* DEVICE_NAME = "JRemote-BLE";

#define LED_PIN 2

struct JoystickData {
  int8_t x;
  int8_t y;
};

struct ControlPacket {
  uint8_t header;      // 头部字节 (0xAA)
  JoystickData leftJoystick;
  JoystickData rightJoystick;
  uint32_t buttons;
};

ControlPacket controlData;
bool deviceConnected = false;
bool oldDeviceConnected = false;
uint32_t packetCount = 0;
uint32_t lastPacketTime = 0;

BLEServer* pServer = NULL;
BLECharacteristic* pTxCharacteristic = NULL;
BLECharacteristic* pRxCharacteristic = NULL;

class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
      deviceConnected = true;
      Serial.println("\n>>> 手机已连接 <<<");
      digitalWrite(LED_PIN, HIGH);
    };
    
    void onDisconnect(BLEServer* pServer) {
      deviceConnected = false;
      Serial.println("\n>>> 手机已断开 <<<");
      digitalWrite(LED_PIN, LOW);
    }
};

class MyCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
      String rxValue = pCharacteristic->getValue();
      
      if (rxValue.length() > 0) {
        // 检查是否是 ping 请求
        if (rxValue.length() == 1 && rxValue[0] == 0x70) { // 'p'
          // 回复 pong
          if (pTxCharacteristic != NULL) {
            uint8_t pong = 0x50; // 'P'
            pTxCharacteristic->setValue(&pong, 1);
            pTxCharacteristic->notify();
          }
          return;
        }
        
        // 检查是否是控制数据包 (9字节)
        if (rxValue.length() == 9) {
          const uint8_t* data = (const uint8_t*)rxValue.c_str();
          uint8_t header = data[0];
          
          // 检查帧头
          if (header == 0xAA) {
            // 正常控制数据
            controlData.header = header;
            controlData.leftJoystick.x = data[1];
            controlData.leftJoystick.y = data[2];
            controlData.rightJoystick.x = data[3];
            controlData.rightJoystick.y = data[4];
            controlData.buttons = (data[5]) | 
                                 (data[6] << 8) | 
                                 (data[7] << 16) | 
                                 (data[8] << 24);
            
            lastPacketTime = millis();
            packetCount++;
          } else if (header == 0xEE) {
            // 急停信号
            controlData.header = header;
            controlData.leftJoystick.x = 0;
            controlData.leftJoystick.y = 0;
            controlData.rightJoystick.x = 0;
            controlData.rightJoystick.y = 0;
            controlData.buttons = 0;
            
            lastPacketTime = millis();
            packetCount++;
            
            Serial.println("\n>>> 急停触发! STOP <<<");
          }
        }
      }
    }
};

void setup() {
  Serial.begin(115200);
  delay(1000);
  
  Serial.println("\n========================================");
  Serial.println("  JBus ESP32-S3 BLE 接收器");
  Serial.println("========================================\n");
  
  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);
  
  BLEDevice::init(DEVICE_NAME);
  
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());
  
  BLEService *pService = pServer->createService(SERVICE_UUID);
  
  pRxCharacteristic = pService->createCharacteristic(
                      CHARACTERISTIC_UUID_RX,
                      BLECharacteristic::PROPERTY_WRITE_NR
                    );
  pRxCharacteristic->setCallbacks(new MyCallbacks());
  
  pTxCharacteristic = pService->createCharacteristic(
                       CHARACTERISTIC_UUID_TX,
                       BLECharacteristic::PROPERTY_NOTIFY
                     );
  pTxCharacteristic->addDescriptor(new BLE2902());
  
  pService->start();
  
  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06);
  pAdvertising->setMinInterval(0x20);
  pAdvertising->setMaxInterval(0x40);
  BLEDevice::startAdvertising();
  
  Serial.println("BLE 已启动");
  Serial.print("设备名称: ");
  Serial.println(DEVICE_NAME);
  Serial.println("等待手机连接...\n");
}

uint32_t counter = 0;
uint32_t lastSendTime = 0;

void loop() {
  if (!deviceConnected && oldDeviceConnected) {
    delay(500);
    pServer->startAdvertising();
    Serial.println("开始广播...");
    oldDeviceConnected = deviceConnected;
  }
  
  if (deviceConnected && !oldDeviceConnected) {
    oldDeviceConnected = deviceConnected;
  }
  
  if (deviceConnected && millis() - lastPacketTime < 100) {
    digitalWrite(LED_PIN, (millis() / 50) % 2);
  } else if (deviceConnected) {
    digitalWrite(LED_PIN, HIGH);
  }
  
  static uint32_t lastPrintTime = 0;
  if (millis() - lastPrintTime >= 100) {
    lastPrintTime = millis();
    if (deviceConnected) {
      printControlData();
    }
  }
  
  // 每秒发送递增数字到手机
  if (deviceConnected && pTxCharacteristic != NULL) {
    if (millis() - lastSendTime >= 1000) {
      lastSendTime = millis();
      counter++;
      
      String msg = "Count: " + String(counter);
      pTxCharacteristic->setValue(msg.c_str());
      pTxCharacteristic->notify();
      
      Serial.print("发送到手机: ");
      Serial.println(msg);
    }
  }
}

void printControlData() {
  Serial.print("\n");
  
  // 检查是否是急停状态
  if (controlData.header == 0xEE) {
    Serial.print("[急停] ");
  }
  
  Serial.print("L:");
  printJoystick(controlData.leftJoystick);
  
  Serial.print(" | R:");
  printJoystick(controlData.rightJoystick);
  
  Serial.print(" | BTN:");
  printButtons(controlData.buttons);
  
  Serial.print(" | PKT:");
  Serial.print(packetCount);
  
  uint32_t latency = millis() - lastPacketTime;
  if (latency > 500) {
    Serial.print(" [TIMEOUT]");
  } else {
    Serial.print(" OK");
  }
}

void printJoystick(const JoystickData& js) {
  float x = js.x / 127.0f;
  float y = js.y / 127.0f;
  Serial.printf("X%+.2f Y%+.2f", x, y);
}

void printButtons(uint32_t buttons) {
  Serial.printf("0x%08X ", buttons);
  
  if (buttons == 0) {
    Serial.print("-");
    return;
  }
  
  const char* buttonNames[] = {"LX", "LY", "LZ", "RX", "RY", "RZ", "L1", "L2", "L3", "L4", "R1", "R2", "R3", "R4"};
  bool first = true;
  
  for (int i = 0; i < 14; i++) {
    if (buttons & (1 << i)) {
      if (!first) Serial.print("+");
      Serial.print(buttonNames[i]);
      first = false;
    }
  }
}
