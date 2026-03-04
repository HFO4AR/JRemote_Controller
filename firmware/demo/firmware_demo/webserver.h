// ==================== Web 服务器 ====================

#ifndef WEBSERVER_H
#define WEBSERVER_H

#include <Arduino.h>
void startWebServer();
void stopWebServer();
void broadcastWebSocket(const char* json);
void sendWebSocketStatus();
void sendWebSocketControlData();
void handleRoot();
void handleNotFound();
void handleWebSocketData(char* payload);

#endif
