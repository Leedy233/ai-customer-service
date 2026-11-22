# 反代公共片段：被 /api/ 与 /ai/ location include
# SSE（GET /ai/chat/stream 裸文本流）：关缓冲 + 长读超时 + HTTP/1.1 空 Connection
proxy_http_version 1.1;
proxy_set_header Connection "";
proxy_set_header Host $host;
proxy_set_header X-Real-IP $remote_addr;
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
proxy_set_header X-Forwarded-Proto $scheme;
proxy_set_header X-Forwarded-Host $host;

proxy_buffering off;
proxy_cache off;
proxy_connect_timeout 10s;
proxy_send_timeout 300s;
proxy_read_timeout 300s;
chunked_transfer_encoding on;

# 项目无 WebSocket（流式为 SSE），故不配置 Upgrade 头；
# 若未来引入 WS：proxy_set_header Upgrade $http_upgrade; Connection "upgrade"; 另行评审。