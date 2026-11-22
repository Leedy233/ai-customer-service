# Nginx 前后端分离部署交付

日期：2026-11-22　范围：ai-customer-service 统一入口改造（方案 B：复用现有 Docker Compose）

## 1. 原有架构分析

- 前端：Vue 3 + Vite + TypeScript + Element Plus + Tailwind。开发态 `npm run dev`（5173），
  dev proxy 将 `/ai` 转发到 `VITE_PROXY_TARGET`（默认 `http://localhost:8082`）；
  生产构建 `npm run build` 产物在 `frontend/dist`。API Base 单点收敛于
  `frontend/src/api/http.ts`（axios `baseURL = import.meta.env.VITE_API_BASE || '/ai'`）
  与 `frontend/src/api/chat.ts`（SSE URL 同读该 env），业务代码无硬编码 host。
- 后端：Spring Boot 4（Java 25）+ Spring AI，容器内监听 8080，路由前缀 `/ai`（无 context-path）。
  认证为 Session（JSESSIONID，path=/）；流式为裸 SSE `GET /ai/chat/stream`（fetch 消费，非 WebSocket）；
  限流为进程内滑动窗口 60 次/分钟/IP，取 `request.getRemoteAddr()`；
  `/actuator` 无鉴权（health/info/prometheus/metrics）。
- 无文件上传接口、无 WebSocket → 不配置 `/uploads/` 与 Upgrade 头（如实说明，不强行添加）。
- 改造前部署：`docker-compose.yml`（mysql 3307、redis 6380、app 公网 published 8080）
  + `docker-compose.verify.yml`（app 8082 override）。`deploy/` 仅有 wsl 脚本，**无现存 nginx 配置**；
  旧设计文档（docs/前端架构设计.md S6）规划过 nginx 但从未实施。
- 改造前风险：app 8080/8082 对公网 published；前端生产 env 仍为 `/ai` 且无入口层；
  限流取 remoteAddr，上代理后会退化为全站共享一个桶（项目审查文档第 166 行早已指出）。

## 2. 改造后架构

```
用户浏览器
   |  http://<host>/            （唯一对外入口：80，后续 443）
   v
nginx 容器（nginx:1.27-alpine，docker 网络 ai-customer-service_default）
   |-- /            -> /usr/share/nginx/html（挂载 frontend/dist，只读）
   |                   try_files $uri $uri/ /index.html（history 路由刷新不 404）
   |                   /assets/ 30d immutable；index.html no-cache
   |-- /api/xxx     -> proxy_pass http://app:8080/ai/xxx（剥 /api 补 /ai）
   |-- /ai/xxx      -> 同上（过渡兼容别名，前端全量切 /api 后可删）
   |-- /actuator    -> 403（运维端点不对外）
   v
app 容器（Spring Boot，8080 仅 docker 内网 + 宿主机 loopback）
   v
mysql:3306 / redis:6379（docker 内网）
```

前端与后端独立构建、独立容器运行；浏览器只访问 nginx；Session Cookie 同域天然携带，零 CORS 代码。

## 3. 修改与新增文件清单

新增：
1. `deploy/nginx.conf` — 80 端口 server：静态托管 + history fallback + /api/、/ai/ 反代 + /actuator 403。
2. `deploy/nginx-proxy.inc` — 反代公共片段（SSE 关缓冲、300s 读超时、可信头透传）。
3. `deploy/nginx-https.conf.example` — 443/HTTPS 模板，**全注释、未挂载、不生效**（启用属外部操作，需另行确认）。
4. `frontend/scripts/nginx-smoke.mjs` — nginx 入口真浏览器冒烟（11 项）。
5. `docs/Nginx前后端分离部署.md` — 本文。

修改（均为加法或配置层，未重构业务、未删功能）：
1. `docker-compose.yml` — app ports 改 `127.0.0.1:8080:8080`；新增 nginx 服务（80:80，三卷只读挂载，depends_on app）。
2. `docker-compose.verify.yml` — app ports 改 `127.0.0.1:8082:8080`（验收/dev proxy 保留 loopback 可用）。
3. `src/main/resources/application.yml` — server 下加 `forward-headers-strategy: native`、
   `tomcat.remoteip.internal-proxies`（收窄为 docker 网段 172.16-31.x）、
   `tomcat.remoteip.remote-ip-header: X-Real-IP`（见 §6）。
4. `frontend/.env.production` — `VITE_API_BASE=/api`（原 `/ai`）。
5. `frontend/.env.example` — 补 dev/prod 注释。

## 4. 完整 Nginx 配置

`deploy/nginx.conf`（挂载为 /etc/nginx/conf.d/default.conf）：

```nginx
server {
    listen 80;
    server_name _;

    root /usr/share/nginx/html;
    index index.html;

    # 当前无文件上传接口；预留 JSON/未来上传余量
    client_max_body_size 10m;

    gzip on;
    gzip_min_length 1024;
    gzip_types text/plain text/css application/json application/javascript image/svg+xml;

    # Vite 哈希资源：长缓存 + immutable
    location /assets/ {
        expires 30d;
        add_header Cache-Control "public, immutable";
        try_files $uri =404;
    }

    # 入口不缓存，保证发版即生效
    location = /index.html {
        add_header Cache-Control "no-cache";
    }

    # SPA history 路由：刷新 /chat、/admin/orders 等不 404
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 统一 API 入口：/api/xxx -> 后端 /ai/xxx（保留方法/头/Cookie/查询串）
    location /api/ {
        proxy_pass http://app:8080/ai/;
        include /etc/nginx/nginx-proxy.inc;
    }

    # 过渡兼容别名（旧同域路径 /ai/），前端全量切 /api 后可删除本段
    location /ai/ {
        proxy_pass http://app:8080/ai/;
        include /etc/nginx/nginx-proxy.inc;
    }

    # 运维端点不对外：仅容器内/loopback 可达
    location /actuator {
        return 403;
    }
}
```

`deploy/nginx-proxy.inc`（挂载为 /etc/nginx/nginx-proxy.inc）：

```nginx
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
```

要点：`proxy_pass` 带尾斜杠 URI（`/ai/`）实现剥 `/api` 补 `/ai`，与后端真实前缀精确匹配；
SSE 依赖 `proxy_buffering off` + `proxy_read_timeout 300s` + HTTP/1.1 空 Connection；
项目无 WebSocket，故不配 Upgrade（未来引入需另行评审）。

Compose nginx 服务（docker-compose.yml 摘录）：

```yaml
  nginx:
    image: nginx:1.27-alpine
    ports:
      - "80:80"
    volumes:
      - ./frontend/dist:/usr/share/nginx/html:ro
      - ./deploy/nginx.conf:/etc/nginx/conf.d/default.conf:ro
      - ./deploy/nginx-proxy.inc:/etc/nginx/nginx-proxy.inc:ro
    depends_on:
      - app
```

## 5. 前端 API 地址调整

- 唯一改动点：`frontend/.env.production` 的 `VITE_API_BASE` 由 `/ai` 改为 `/api`。
  axios 与 SSE URL 均单点读该 env，**业务代码零改动**。
- 开发态不变：`.env.development` 仍 `/ai` + vite dev proxy → 8082（loopback 保留，dev 不受影响）。
- 生产产物经 grep 校验：`dist/assets/*.js` 无 `localhost:808x`、`127.0.0.1:808x`、`:5173` 直连地址；
  冒烟 n09 断言运行期请求零直连后端端口。
- 旧 `/ai/` 同域路径由 nginx 别名过渡兼容，旧书签/旧包不立即失效。

## 6. 后端配置调整

`application.yml` 加法三项（业务代码零改动，RateLimitInterceptor 仍取 getRemoteAddr()）：

```yaml
server:
  forward-headers-strategy: native
  tomcat:
    remoteip:
      internal-proxies: "172\\.(1[6-9]|2[0-9]|3[01])\\.\\d{1,3}\\.\\d{1,3}"
      remote-ip-header: X-Real-IP
```

- `forward-headers-strategy: native`：启用 Tomcat RemoteIpValve，还原客户端 IP/协议/Host。
- `internal-proxies` 收窄为 docker 容器网段：只有来自 nginx 容器（172.x）的请求才允许改写代理头，
  宿主机 loopback 直连（dev/验收/运维）不受代理头影响。
- `remote-ip-header: X-Real-IP`：**实测踩坑后的修正**。默认 X-Forwarded-For 逐跳回退在单层代理下
  不可靠：nginx 眼中的"真实客户端"本地是 docker 网关（172.x，落在可信段），valve 会多跳一跳，
  把客户端伪造的 XFF 左段当成真实 IP（实测伪造 65 个 XFF → trackedIps=66，限流被绕过）。
  改为直接取 nginx **覆写**的 X-Real-IP（单值、不拼接、客户端伪造会被 nginx 覆盖）后，
  伪造 XFF+X-Real-IP 65 连发全部归入同一桶（见 §9）。
- 端口收敛：app 仅 `127.0.0.1:8080/8082` published（保留 dev proxy 与验收脚本），公网不可达；
  生产可整段删除 ports，nginx 走 docker 内网 `app:8080`。
- Session/Cookie：同域 nginx 天然携带 JSESSIONID，认证逻辑零改动；容器重建=全员登出（已知语义）。
- CORS：同源部署后浏览器不再发跨域请求，无需任何 CORS 配置。

## 7. 部署启动命令

```powershell
# 0) 前端生产构建（dist 供 nginx 挂载）
cd frontend; npm run build; cd ..

# 1) 后端打包（application.yml 变更需进 jar）
$env:JAVA_HOME="D:\JavaDevTools\jdk\jdk-25"
& "D:\JavaDevTools\apache-maven\apache-maven-3.9.16-bin\apache-maven-3.9.16\bin\mvn.cmd" `
  -s "D:\JavaDevTools\apache-maven\apache-maven-3.9.16-bin\apache-maven-3.9.16\conf\settings.xml" package

# 2) 构建并启动（mysql/redis/app/nginx；app 重建会登出所有会话）
docker compose -f docker-compose.yml -f docker-compose.verify.yml up -d --build

# 仅重建后端镜像时
docker compose -f docker-compose.yml -f docker-compose.verify.yml up -d --build app
```

Linux/WSL 服务器同命令（compose 文件跨平台；ONNX 缓存卷 `${USERPROFILE:-/tmp}` 自动落 /tmp）。

## 8. Nginx 重载命令

```powershell
# 语法检查（改配置后必跑）
docker exec ai-customer-service-nginx-1 nginx -t
# 热重载（不断连接）
docker exec ai-customer-service-nginx-1 nginx -s reload
```

配置为只读 bind mount：改 `deploy/nginx.conf` / `nginx-proxy.inc` 后无需重建容器，reload 即生效；
改 `frontend/dist` 内容无需 reload（静态目录实时可见），但 index.html no-cache 保证发版即生效。

## 9. 验证测试结果（全部真实执行）

环境：Windows + Docker Desktop；入口 `http://localhost`（80）。

A. 配置与部署
- `nginx -t`：syntax is ok / test is successful。
- `docker ps`：nginx `0.0.0.0:80->80`；app `127.0.0.1:8080->8080, 127.0.0.1:8082->8080`（公网不可达）。

B. curl 矩阵 17/17 PASS（scripts 见会话执行记录；关键项）
- c01/c02 首页与 `/chat` history 刷新 200 text/html。
- c03 `/assets/index-*.js` → `Cache-Control: public, max-age=2592000, immutable`；c04 index.html no-cache。
- c05/c06 POST 登录 + Cookie 会话 GET /api/me=zhangsan。
- c07 GET 订单 199.00；c08/c09 POST 建工单 GD20261122004 + POST 取消 → CANCELLED。
- c10/c11/c12 知识库 POST/PUT/DELETE（/api/admin/knowledge）闭环。
- c13 监控 rateLimit.trackedIps≥1；c14 Token 统计 200。
- c15 `/actuator/health` 经 nginx → 403；c16 loopback `127.0.0.1:8080/actuator/health` → 200（运维保留）。
- c17 SSE `curl -N /api/chat/stream` 首帧 `data:` 增量到达（关缓冲生效）。

C. 真浏览器冒烟 `frontend/scripts/nginx-smoke.mjs`：11/11 PASS，CONSOLE_ERRORS 0
- n01/n02 SPA 与 history 刷新；n03 登录；n04 SSE 增量（mid=979→end=1173 字符）；
- n05 流式 URL 走 /api/chat/stream；n06 订单卡 199.00；n07 刷新后会话保持；
- n08 immutable 缓存头；n09 运行期零直连 8080/8082/5173；n10 /actuator 403；n11 管理端经 nginx 出图。
- 截图：docs/screenshots/nginx-chat.png、nginx-admin.png。

D. 限流代理语义（伪造头攻击测试）
- 修正前（默认 XFF 逐跳）：65 个伪造 XFF 连发 → 200:65、trackedIps=66（**被绕过，已修**）。
- 修正后（X-Real-IP 覆写取值）：65 连发同时伪造 XFF 与 X-Real-IP → 200:59、429:6、trackedIps=1
  （登录本身占 1 次配额，59+1=60 精确触发阈值；nginx 覆写生效，伪造头全部无效，归入真实客户端同一桶）。
- 末次镜像复核：登录/me/订单/SSE 首帧/history 深刷新（/admin/orders）全部 200 正常。
- 本地语义说明：本地所有用户经 Docker 网关进入，共享一个桶（与改造前 localhost 语义一致）；
  生产公网客户端 IP 各不相同，按客户端独立计桶。

E. 数据基线：验证后重跑 `eval/seed-ui-demo.sql`，核对计数
  chat=20、ticket=2、knowledge=3、pending refund=0、request_log=8（与基线一致）。

F. 未执行/不适用项（如实说明）
- 文件上传/下载、WebSocket：项目无此功能，未配置未测试。
- HTTPS/域名/证书/防火墙/DNS：未触碰（模板仅注释存在），属外部操作需另行确认。
- 既有验收脚本（admin 34/ui 30/mobile 12）BASE 硬编码 5173 dev 入口，与 nginx 入口互不影响；
  本轮未复跑（nginx 入口已由 B/C/D 覆盖同类断言）。

## 10. 生产上线注意事项

1. **端口收敛**：生产删除 app 的 ports 段（nginx 走 docker 内网）；mysql 3307/redis 6380  likewise
   改 loopback 或删 ports；仅保留 nginx 80/443 对外。云安全组/防火墙只放行 80/443（外部操作，需确认）。
2. **HTTPS**：启用 `deploy/nginx-https.conf.example`（443 ssl + http2 + TLS1.2/1.3 + 80 跳转），
   挂载证书后 reload；同时设 `SERVER_SERVLET_SESSION_COOKIE_SECURE=true` 使 Cookie 仅 HTTPS 传输。
   证书申请/域名解析属外部操作，先确认再执行。
3. **前置 LB/CDN 时的真实 IP**：nginx 加 real_ip 模块
   （`set_real_ip_from <LB 内网段>; real_ip_header X-Forwarded-For; real_ip_recursive on;`），
   使 `$remote_addr` 还原真实客户端后再覆写 X-Real-IP；否则限流会把 LB IP 当客户端（全站一桶）。
   若 LB 网段不在 172.16-31.x，需同步扩 `internal-proxies` 正则。
4. **发版顺序**：先 `npm run build`（dist 原子替换建议：构建到临时目录后整体切换挂载源），
   后端改 yml/代码才需 mvn package + `up -d --build app`；app 重建=全员登出，避开高峰。
5. **限流仍是进程内单实例**：多副本部署需换 Redis 集中式限流（既有工程债务，本轮未动）。
6. **运维端点**：/actuator 对外 403；运维走 loopback 或 docker exec；prometheus 抓取建议走内网采集器。
7. **SSE 长连接**：300s 读超时覆盖当前模型响应时长；若未来加长流式任务需同步上调；
   云 LB 空闲超时需 ≥ 该值（外部配置，需确认）。
8. **回滚**：nginx 服务独立，`docker compose stop nginx` 即回到旧直连形态（app loopback 端口仍在）；
   配置回滚=还原 deploy 两文件 + reload。