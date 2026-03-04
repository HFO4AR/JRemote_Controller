// ==================== Web 服务器 ====================

#include "globals.h"
#include "webserver.h"
#include "wifi.h"

// 外部变量声明
extern int leftJoystickX, leftJoystickY;
extern int rightJoystickX, rightJoystickY;
extern WorkMode currentMode;

//函数
void handleRoot();
void handleNotFound();
void handleWebSocketData(char* payload);

void startWebServer() {
    // WebSocket
    webSocketServer.onEvent([](uint8_t num, WStype_t type, uint8_t* payload, size_t length) {
        switch (type) {
            case WStype_DISCONNECTED:
                Serial.printf("WebSocket %d 断开\n", num);
                break;
            case WStype_CONNECTED:
                Serial.printf("WebSocket %d 连接\n", num);
                break;
            case WStype_TEXT:
                Serial.printf("WebSocket 收到: %s\n", payload);
                // 处理收到的数据
                handleWebSocketData((char*)payload);
                break;
        }
    });
    webSocketServer.begin();

    // HTTP 服务器
    webServer.on("/", handleRoot);
    webServer.onNotFound(handleNotFound);
    webServer.begin();

    Serial.println("Web 服务器已启动");
}

void stopWebServer() {
    webServer.stop();
    webSocketServer.disconnect();
    Serial.println("Web 服务器已停止");
}

void handleRoot() {
    String html = R"rawliteral(
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>JRemote Controller</title>
    <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #1a1a2e; color: #eee; padding: 20px; }
        h1 { color: #00d4ff; margin-bottom: 20px; }
        .card { background: #16213e; border-radius: 12px; padding: 20px; margin-bottom: 20px; }
        .status { display: flex; gap: 20px; flex-wrap: wrap; }
        .status-item { background: #0f3460; padding: 10px 20px; border-radius: 8px; }
        .status-label { color: #888; font-size: 12px; }
        .status-value { color: #00d4ff; font-size: 18px; font-weight: bold; }
        .chart-container { height: 200px; margin-top: 10px; }
        .log-container { height: 150px; overflow-y: auto; background: #0f3460; padding: 10px; border-radius: 8px; font-family: monospace; font-size: 12px; }
        .log-entry { margin-bottom: 4px; }
        .log-info { color: #4fc3f7; }
        .log-warn { color: #ffb74d; }
        .log-error { color: #ef5350; }
        .controls { display: flex; gap: 10px; flex-wrap: wrap; }
        button { background: #00d4ff; color: #000; border: none; padding: 10px 20px; border-radius: 8px; cursor: pointer; font-weight: bold; }
        button:hover { background: #00b8e6; }
        .data-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; }
        .data-item { background: #0f3460; padding: 10px; border-radius: 8px; text-align: center; }
        .data-label { color: #888; font-size: 12px; }
        .data-value { color: #00d4ff; font-size: 20px; font-weight: bold; }
    </style>
</head>
<body>
    <h1>JRemote Controller</h1>

    <div class="card">
        <h2>状态</h2>
        <div class="status">
            <div class="status-item">
                <div class="status-label">模式</div>
                <div class="status-value" id="mode">WiFi UDP</div>
            </div>
            <div class="status-item">
                <div class="status-label">连接状态</div>
                <div class="status-value" id="connection">未连接</div>
            </div>
            <div class="status-item">
                <div class="status-label">IP地址</div>
                <div class="status-value" id="ip">-</div>
            </div>
        </div>
    </div>

    <div class="card">
        <h2>控制数据</h2>
        <div class="data-grid">
            <div class="data-item">
                <div class="data-label">左摇杆 X</div>
                <div class="data-value" id="lx">0</div>
            </div>
            <div class="data-item">
                <div class="data-label">左摇杆 Y</div>
                <div class="data-value" id="ly">0</div>
            </div>
            <div class="data-item">
                <div class="data-label">右摇杆 X</div>
                <div class="data-value" id="rx">0</div>
            </div>
            <div class="data-item">
                <div class="data-label">右摇杆 Y</div>
                <div class="data-value" id="ry">0</div>
            </div>
        </div>
    </div>

    <div class="card">
        <h2>传感器数据</h2>
        <div class="chart-container">
            <canvas id="sensorChart"></canvas>
        </div>
    </div>

    <div class="card">
        <h2>调试日志</h2>
        <div class="log-container" id="log"></div>
    </div>

    <div class="card">
        <div class="controls">
            <button onclick="clearLog()">清除日志</button>
            <button onclick="sendCommand('EMERGENCY_STOP')">急停</button>
        </div>
    </div>

    <script>
        let ws;
        const chartData = { labels: [], lx: [], ly: [], rx: [], ry: [] };

        function connect() {
            ws = new WebSocket('ws://' + location.hostname + ':81/');

            ws.onopen = () => {
                addLog('WebSocket 连接成功', 'info');
                document.getElementById('connection').textContent = '已连接';
            };

            ws.onclose = () => {
                addLog('WebSocket 断开', 'warn');
                document.getElementById('connection').textContent = '未连接';
                setTimeout(connect, 3000);
            };

            ws.onmessage = (event) => {
                try {
                    const data = JSON.parse(event.data);
                    updateData(data);
                } catch(e) {
                    console.log('收到:', event.data);
                }
            };
        }

        function updateData(data) {
            if (data.type === 'status') {
                document.getElementById('mode').textContent = data.mode || '-';
                document.getElementById('ip').textContent = data.ip || '-';
            } else if (data.type === 'control') {
                document.getElementById('lx').textContent = data.lx || 0;
                document.getElementById('ly').textContent = data.ly || 0;
                document.getElementById('rx').textContent = data.rx || 0;
                document.getElementById('ry').textContent = data.ry || 0;

                // 更新图表
                const now = new Date().toLocaleTimeString();
                chartData.labels.push(now);
                chartData.lx.push(data.lx || 0);
                chartData.ly.push(data.ly || 0);
                chartData.rx.push(data.rx || 0);
                chartData.ry.push(data.ry || 0);

                if (chartData.labels.length > 20) {
                    chartData.labels.shift();
                    chartData.lx.shift();
                    chartData.ly.shift();
                    chartData.rx.shift();
                    chartData.ry.shift();
                }

                updateChart();
            } else if (data.type === 'log') {
                addLog(data.message, data.level || 'info');
            }
        }

        function addLog(message, level) {
            const log = document.getElementById('log');
            const entry = document.createElement('div');
            entry.className = 'log-entry log-' + level;
            entry.textContent = '[' + new Date().toLocaleTimeString() + '] ' + message;
            log.appendChild(entry);
            log.scrollTop = log.scrollHeight;
        }

        function clearLog() {
            document.getElementById('log').innerHTML = '';
        }

        function sendCommand(cmd) {
            if (ws && ws.readyState === WebSocket.OPEN) {
                ws.send(JSON.stringify({ cmd: cmd }));
                addLog('发送命令: ' + cmd, 'info');
            }
        }

        // 图表
        const ctx = document.getElementById('sensorChart').getContext('2d');
        const chart = new Chart(ctx, {
            type: 'line',
            data: {
                labels: chartData.labels,
                datasets: [
                    { label: 'LX', data: chartData.lx, borderColor: '#ff6384', tension: 0.4 },
                    { label: 'LY', data: chartData.ly, borderColor: '#36a2eb', tension: 0.4 },
                    { label: 'RX', data: chartData.rx, borderColor: '#ffcd56', tension: 0.4 },
                    { label: 'RY', data: chartData.ry, borderColor: '#4bc0c0', tension: 0.4 }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                animation: false,
                scales: {
                    y: { min: -127, max: 127, grid: { color: '#333' } },
                    x: { grid: { color: '#333' } }
                },
                plugins: { legend: { labels: { color: '#eee' } } }
            }
        });

        function updateChart() {
            chart.data.labels = chartData.labels;
            chart.data.datasets[0].data = chartData.lx;
            chart.data.datasets[1].data = chartData.ly;
            chart.data.datasets[2].data = chartData.rx;
            chart.data.datasets[3].data = chartData.ry;
            chart.update();
        }

        connect();
    </script>
</body>
</html>
)rawliteral";

    webServer.send(200, "text/html", html);
}

void handleNotFound() {
    webServer.send(404, "text/plain", "Not Found");
}

void handleWebSocketData(char* payload) {
    // 解析 JSON 并处理
    // 这里可以扩展处理来自 Web 的命令
}

void broadcastWebSocket(const char* json) {
    webSocketServer.broadcastTXT(json);
}

void sendWebSocketStatus() {
    char json[256];
    snprintf(json, sizeof(json),
        "{\"type\":\"status\",\"mode\":\"%s\",\"ip\":\"%s\"}",
        currentMode == MODE_WIFI_UDP ? "WiFi UDP" : "BLE",
        WiFi.status() == WL_CONNECTED ? WiFi.localIP().toString().c_str() : "-"
    );
    broadcastWebSocket(json);
}

void sendWebSocketControlData() {
    char json[256];
    snprintf(json, sizeof(json),
        "{\"type\":\"control\",\"lx\":%d,\"ly\":%d,\"rx\":%d,\"ry\":%d}",
        leftJoystickX, leftJoystickY, rightJoystickX, rightJoystickY
    );
    broadcastWebSocket(json);
}
