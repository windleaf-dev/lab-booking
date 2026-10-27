# 实验室设备预约与审批系统

面向高校实验室的全栈预约平台：设备台账、时段预约、教师审批、签到与违约、使用统计、用户与角色管理，前后端分离，带深色科技风管理界面。

当前进度：**第三阶段（Redis 缓存 + 抢约分布式锁 + WebSocket 实时通知 + Docker Compose）已完成**，前后端已联调通过，Redis 与 WebSocket 已做真实链路验证。

---

## 1. 项目亮点

- **完整业务闭环**：预约 → 审批 → 签到 → 完成 / 违约 / 取消，状态由定时任务自动流转
- **并发安全的占位设计**：Redis 分布式锁前置削峰 + `active_key` 唯一索引兜底，再配合 `DuplicateKeyException` 友好提示
- **热点缓存与自动失效**：设备分页 / 分类 / 统计走 Caffeine 或 Redis 缓存，预约变更后主动清理统计缓存
- **实时通知**：WebSocket 推送预约提交、审批结果、自动完成 / 违约，前端铃铛消息中心 + 页面无刷新更新
- **缓存可降级**：Redis 未启用或临时故障时自动回退到查库，业务不中断
- **双重权限边界**：JWT + 拦截器 + `AuthContext` 后端校验，前端路由守卫与按钮显隐只负责体验
- **深色科技风 UI**：玻璃面板、霓虹光效、雷达动画、ECharts 暗色主题、响应式侧边栏
- **真实演示数据**：首次启动自动生成账号、设备、预约趋势、待审批与违约记录，打开即可演示
- **一键部署**：Docker Compose 编排 MySQL + Redis + 后端 + Nginx 前端，支持健康检查与自动重启
- **工程细节**：统一响应、全局异常、分页、状态机、定时任务、密码自助修改、按需引入 ECharts

---

## 2. 技术栈

| 层 | 技术 |
| --- | --- |
| 前端 | Vue 3、Vite 5、Vue Router 4、Pinia 2、Element Plus、ECharts 5（按需引入）、Axios、原生 WebSocket |
| 后端 | Spring Boot 3.5、MyBatis 3、Spring Scheduling、Spring Security Crypto（BCrypt）、Spring WebSocket、Spring Cache |
| 缓存 / 协调 | Redis 7（`SET NX` 分布式锁 + Lua 解锁）、Caffeine（本地默认缓存） |
| 数据库 | MySQL 8 |
| 鉴权 | 手写 HS256 JWT + 拦截器 + ThreadLocal 用户上下文 + WebSocket 握手校验 |
| 部署 | Docker、Docker Compose、Nginx 反向代理与静态资源托管 |
| 构建 | Maven 3.10、Node.js 18+ |

后端只提供 `/api/**` JSON 接口，前端开发期通过 Vite 代理访问，避免跨域配置。

---

## 3. 功能清单

### 3.1 认证与权限
- 用户名 + 密码登录，BCrypt 校验，签发 12 小时 JWT
- 三角色：`ADMIN`（管理员）、`TEACHER`（教师）、`STUDENT`（学生）
- 路由守卫 + 后端拦截器 + Controller 角色校验
- 用户自助修改密码，修改后强制重新登录

### 3.2 设备与分类
- 设备分页查询：关键字（名称 / 编号 / 型号 / 位置）、分类、状态筛选
- 设备新增、编辑、删除；编号唯一校验
- 删除保护：有预约记录的设备不可删除，提示改为「停用」
- 分类列表带设备数量统计；编码 / 名称唯一；分类下有设备时禁止删除

### 3.3 预约看板
- 「设备 × 时段」矩阵，按日期切换（今天起 14 天内）
- 单元格状态：可预约、待审批、已通过、已完成、违约、维修中、已停用、已过期
- 点击空闲格直接发起预约，填写用途后提交
- 顶部实时统计可预约 / 待审批 / 已通过数量
- 自己预约的格子带「我」标记，悬浮显示预约人及用途

### 3.4 我的预约
- 按状态筛选：待审批 / 已通过 / 已完成 / 违约 / 已驳回 / 已取消
- 开始前可取消；已通过且进入签到窗口可签到
- 展示审批人、审批备注、签到时间

### 3.5 审批中心
- 待审批队列 + 全部记录双 Tab
- 通过 / 驳回（驳回必须填写原因）
- 不能审批自己提交的预约
- 历史记录支持申请人 / 设备关键字、状态、日期筛选
- 侧边栏待审批角标，审批后即时刷新

### 3.6 统计分析
- 累计预约、完成率、违约率、峰值日期
- 最近 7 / 14 / 30 天预约趋势（缺失日期自动补 0）
- 状态分布、设备热度 TOP 5、设备分类构成
- 自动生成数据洞察文案

### 3.7 用户管理（管理员）
- 用户分页、关键字 / 角色筛选
- 新增用户、编辑姓名 / 角色 / 状态、重置密码
- 不能修改自己的角色，也不能禁用自己的账号

### 3.8 仪表盘
- 指挥中心横幅、今日预约雷达、四项核心指标
- 4 张 ECharts 图表 + 待审批队列 / 学生快捷入口
- 模块运行状态条，展示当前已启用能力

### 3.9 实时通知（WebSocket）
- 顶栏连接状态灯：`REALTIME` / `LINKING` / `OFFLINE`
- 铃铛消息中心：未读角标、消息列表、清空、最近 20 条
- 学生收到：预约提交、审批通过 / 驳回、自动取消、完成、违约
- 教师 / 管理员收到：新预约待审批、待审批队列变化
- 看板、我的预约、审批中心、仪表盘、统计分析收到事件后自动刷新
- 断线指数退避重连（最长 30 秒），25 秒心跳探测半开连接

### 3.10 缓存与并发控制
- 设备分页、分类列表、5 类统计接口接入 Spring Cache
- 本地默认 Caffeine；设置 `REDIS_ENABLED=true` 切换为 Redis，缓存跨实例共享
- 预约创建 / 审批 / 取消 / 自动流转后清理统计缓存，避免看板数字滞后
- 抢约时按「设备 + 日期 + 时段」加 Redis 分布式锁，事务提交后再释放
- 锁被占用返回「该时段正在被其他同学抢约，请稍后重试」
- Redis 不可用时自动降级，最终仍由数据库唯一索引保证不超卖

### 3.11 Docker 部署
- `docker compose up -d --build` 一键启动 MySQL、Redis、后端、Nginx 前端
- MySQL / Redis 带数据卷与健康检查；后端、前端按健康状态依次启动
- Nginx 托管前端静态资源，并反向代理 `/api` 与 `/ws`（含 WebSocket Upgrade）
- 数据库表结构与演示数据由后端首次启动自动初始化

---

## 4. 预约状态机

```text
                    ┌────────────── 取消（开始前） ──────────────┐
                    ▼                                          │
 提交预约 ──▶ PENDING ──教师通过──▶ APPROVED ──签到──▶（时段结束）COMPLETED
                │                     │
                │                     └── 未签到，时段结束 ──▶ VIOLATED
                ├──教师驳回──▶ REJECTED
                └──时段开始仍未审批──▶ CANCELLED（系统自动）
```

规则细节：

| 规则 | 说明 |
| --- | --- |
| 可预约日期 | 今天起 14 天内，且时段尚未开始 |
| 设备状态 | 只有 `AVAILABLE` 可预约 |
| 用户冲突 | 同一用户同一天同一时段只能有一条有效预约 |
| 设备冲突 | `active_key = deviceId_date_slotId` 唯一索引兜底 |
| 审批限制 | 只能审批 `PENDING`；不能审批自己的预约；时段已开始不可审批 |
| 签到窗口 | 时段开始前 30 分钟至时段结束 |
| 自动流转 | 每 60 秒扫描：已通过未签到且结束 → `VIOLATED`；已签到且结束 → `COMPLETED`；待审批但时段已开始 → `CANCELLED` |

---

## 5. 目录结构

```text
lab-booking/
├─ backend/
│  ├─ pom.xml
│  ├─ Dockerfile                          # 多阶段构建：Maven → JRE
│  └─ src/main/
│     ├─ java/com/lab/
│     │  ├─ LabBookingApplication.java   # @EnableScheduling
│     │  ├─ common/                      # Result、异常、JWT、登录上下文、拦截器、缓存名
│     │  ├─ config/                      # Web、BCrypt、缓存、演示数据、状态定时任务
│     │  ├─ controller/                  # Auth / Device / Category / Booking / User / Stats
│     │  ├─ mapper/                      # MyBatis 注解 SQL
│     │  ├─ model/                       # User / Device / Category / TimeSlot / Booking
│     │  ├─ service/                     # 业务规则、状态机、RedisLock、通知
│     │  └─ ws/                          # WebSocket 握手鉴权、会话管理、推送
│     └─ resources/
│        ├─ application.yml
│        ├─ application-local.yml.example
│        └─ db/schema.sql
├─ frontend/
│  ├─ Dockerfile                          # 多阶段构建：Node → Nginx
│  ├─ nginx.conf                          # SPA 回退 + /api、/ws 反向代理
│  ├─ vite.config.js                     # 5174 端口、/api 与 /ws 代理、分包配置
│  └─ src/
│     ├─ api/                            # Axios 封装与接口模块
│     ├─ layout/MainLayout.vue           # 侧边栏、顶栏、实时状态、消息中心、改密
│     ├─ router/                         # 路由与角色守卫
│     ├─ stores/auth.js                  # Pinia 登录态
│     ├─ styles/global.css               # 深空科技主题 + 消息浮层
│     ├─ utils/echarts.js                # ECharts 按需注册
│     ├─ utils/socket.js                 # WebSocket 连接 / 重连 / 心跳 / 订阅
│     └─ views/
│        ├─ Login.vue                    # 科技感登录页
│        ├─ Dashboard.vue                # 指挥中心仪表盘
│        ├─ BookingBoard.vue             # 设备 × 时段预约矩阵
│        ├─ MyBookings.vue               # 我的预约 / 签到 / 取消
│        ├─ Approvals.vue                # 审批中心
│        ├─ Analytics.vue                # 统计分析
│        ├─ DeviceList.vue               # 设备台账
│        ├─ CategoryList.vue             # 分类管理
│        ├─ UserList.vue                 # 用户管理
│        └─ NotFound.vue
├─ docs/
│  ├─ 学习路线.md                         # 代码导读 + 面试题 + 练习
│  ├─ 接口说明.md                         # 全量接口、WebSocket 协议
│  └─ 部署说明.md                         # Docker / Nginx / 生产清单
├─ docker-compose.yml                     # MySQL + Redis + 后端 + 前端
├─ .env.example                           # 部署环境变量模板
└─ .gitignore
```

---

## 6. 快速开始

### 6.1 环境要求

- JDK 17
- Maven 3.9+
- Node.js 18+
- MySQL 8（默认 `127.0.0.1:3306`，账号 `root`）
- Redis 7（可选，本地默认使用 Caffeine；体验缓存 / 分布式锁时再启动）
- Docker Desktop（可选，用于 Compose 一键部署）

数据库不需要手动创建，JDBC 参数带 `createDatabaseIfNotExist=true`，首次启动自动建库并执行 `db/schema.sql`。

### 6.2 配置本地敏感信息

```powershell
cd backend\src\main\resources
Copy-Item application-local.yml.example application-local.yml
```

编辑 `application-local.yml`：

```yaml
spring:
  datasource:
    password: "你的 MySQL 密码"

app:
  jwt:
    secret: "至少 32 字符的随机字符串"
```

> `application-local.yml` 已被 `.gitignore` 忽略，不会提交到仓库。
> 也可以用环境变量 `DB_PASSWORD`、`JWT_SECRET` 覆盖。

### 6.3 启动后端（8091）

```powershell
cd backend
mvn spring-boot:run
```

或打包后运行：

```powershell
mvn -DskipTests package
java -jar target/lab-booking-backend-1.0.0.jar
```

验证：

```powershell
curl.exe http://127.0.0.1:8091/api/health
# {"code":200,"message":"ok","data":{"status":"UP","service":"lab-booking-backend","redisEnabled":false,"websocketOnline":0}}
```

`redisEnabled` 表示是否启用了 Redis 缓存 / 分布式锁，`websocketOnline` 是当前在线用户数。

### 6.4 可选：启用 Redis

不配置 Redis 也能完整运行，缓存会自动使用 Caffeine。要体验第三阶段的缓存与分布式锁：

```powershell
# 本机启动 Redis（默认 6379）
redis-server

cd backend
$env:REDIS_ENABLED='true'
mvn spring-boot:run
```

再次访问 `/api/health`，应看到 `redisEnabled: true`。此时设备分页、分类、统计接口的结果会写入 Redis：

```powershell
redis-cli --scan --pattern "lab:cache:*"
redis-cli ttl "lab:cache:statsOverview:SimpleKey []"
```

抢约锁的 key 形如 `lock:booking:8_2026-10-28_1`，事务提交后由 Lua 脚本比对 token 再删除。

### 6.5 启动前端（5174）

```powershell
cd frontend
npm install
npm run dev
```

打开 <http://127.0.0.1:5174>。

### 6.6 演示账号

| 用户名 | 密码 | 角色 | 权限 |
| --- | --- | --- | --- |
| `admin` | `123456` | 管理员 | 全部功能 + 用户管理 |
| `teacher` | `123456` | 教师 | 设备维护 + 审批 |
| `student` | `123456` | 学生 | 预约 / 签到 / 查看统计 |

首次启动会自动初始化：

- 3 个账号、4 个分类、4 个时段、8 台设备
- 最近 6 天的已完成预约（用于趋势图）
- 1 条违约记录、1 条待审批、1 条未来已通过预约、今日已完成预约

### 6.7 Docker Compose 一键启动

```powershell
cd lab-booking
Copy-Item .env.example .env
# 修改 .env 中的 DB_PASSWORD、JWT_SECRET
docker compose up -d --build
```

打开 <http://127.0.0.1:8080>。常用命令：

```powershell
docker compose ps            # 查看服务与健康状态
docker compose logs -f backend
docker compose down          # 停止（保留数据卷）
docker compose down -v       # 停止并清空数据库 / Redis 数据
```

Compose 会启动 MySQL（宿主机 3307）、Redis（宿主机 6380）、后端（8091）、Nginx 前端（8080）。
详细说明见 [docs/部署说明.md](docs/部署说明.md)。

> 本机当前未安装 Docker，Compose 文件已按标准多阶段构建编写，并做了静态检查；Redis / WebSocket / 缓存 / 锁均已在本机以非 Docker 方式完成真实联调。

---

## 7. 接口一览

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| GET | `/api/health` | 健康检查（含 Redis / WS 状态） | 公开 |
| POST | `/api/auth/login` | 登录，返回 token + 用户信息 | 公开 |
| GET | `/api/auth/me` | 当前登录用户 | 登录 |
| POST | `/api/auth/password` | 修改自己的密码 | 登录 |
| POST | `/api/auth/logout` | 退出（前端清理 token） | 登录 |
| GET | `/api/categories` | 分类列表（含设备数） | 登录 |
| POST | `/api/categories` | 新增分类 | 管理员 / 教师 |
| PUT | `/api/categories/{id}` | 修改分类 | 管理员 / 教师 |
| DELETE | `/api/categories/{id}` | 删除分类 | 管理员 / 教师 |
| GET | `/api/devices/page` | 设备分页查询 | 登录 |
| GET | `/api/devices/{id}` | 设备详情 | 登录 |
| POST | `/api/devices` | 新增设备 | 管理员 / 教师 |
| PUT | `/api/devices/{id}` | 修改设备 | 管理员 / 教师 |
| DELETE | `/api/devices/{id}` | 删除设备 | 管理员 / 教师 |
| GET | `/api/slots` | 预约时段列表 | 登录 |
| GET | `/api/bookings/day?date=` | 某天全部有效预约（矩阵） | 登录 |
| POST | `/api/bookings` | 创建预约 | 登录 |
| GET | `/api/bookings/my` | 我的预约分页 | 登录 |
| POST | `/api/bookings/{id}/cancel` | 取消预约 | 本人 |
| POST | `/api/bookings/{id}/checkin` | 签到 | 本人 |
| GET | `/api/bookings/pending` | 待审批分页 | 管理员 / 教师 |
| POST | `/api/bookings/{id}/approve` | 通过 / 驳回 | 管理员 / 教师 |
| GET | `/api/bookings/page` | 预约管理 / 审批历史 | 管理员 / 教师 |
| GET | `/api/stats/overview` | 看板指标 | 登录 |
| GET | `/api/stats/device-by-category` | 分类分布 | 登录 |
| GET | `/api/stats/status-distribution` | 状态分布 | 登录 |
| GET | `/api/stats/booking-trend?days=7` | 预约趋势 | 登录 |
| GET | `/api/stats/top-devices` | 设备热度 TOP 5 | 登录 |
| GET | `/api/users/page` | 用户分页 | 管理员 |
| POST | `/api/users` | 新增用户 | 管理员 |
| PUT | `/api/users/{id}` | 编辑用户 | 管理员 |
| POST | `/api/users/{id}/reset-password` | 重置密码 | 管理员 |
| WS | `/ws/notifications?token=` | 实时通知（JSON 文本） | 登录 |

请求头统一携带：

```http
Authorization: Bearer <token>
```

WebSocket 例外：浏览器原生 WebSocket 不能设置请求头，token 通过查询参数传递，由握手拦截器校验。详细协议见 [docs/接口说明.md](docs/接口说明.md#10-websocket-实时通知)。

---

## 8. 关键设计

### 8.1 手写 JWT 而不是引入完整 Spring Security

- 只依赖 JDK `HmacSHA256` + Jackson，便于理解 token 组成
- `JwtUtil` 负责签发 / 校验 / 过期判断
- `AuthInterceptor` 拦截 `/api/**`（登录、健康检查除外），解析后写入 `AuthContext`（ThreadLocal）
- Controller 通过 `AuthContext.requireStaff()` / `requireAdmin()` 做角色校验

### 8.2 `active_key` 唯一索引防并发重复预约

```text
active_key = deviceId_bookingDate_slotId
```

- 有效预约（`PENDING` / `APPROVED`）持有 `active_key`
- 取消、驳回、完成、违约时置为 `NULL`，释放占位
- MySQL 唯一索引允许多个 `NULL`，因此历史记录不会互相冲突
- 并发插入时数据库保证只有一条成功，Service 捕获 `DuplicateKeyException` 转为友好提示

### 8.3 签到与违约

- 签到窗口：开始前 30 分钟至结束
- 定时任务每 60 秒扫描：
  - `APPROVED` + 已签到 + 时段结束 → `COMPLETED`
  - `APPROVED` + 未签到 + 时段结束 → `VIOLATED`
  - `PENDING` + 时段已开始 → `CANCELLED`（避免占用设备）
- 状态刷新在查询列表前也会调用一次，保证用户打开页面看到的是最新状态

### 8.4 统计 SQL 与趋势补零

- 状态分布、设备热度、趋势均用 `GROUP BY` 在数据库聚合，避免把全量数据拉到内存
- 趋势查询只返回有数据的日期，Service 在 Java 侧按日期补齐 0，保证折线连续
- `LocalDate` / `java.sql.Date` 做兼容转换，避免 JDBC 驱动差异

### 8.5 前端主题与性能

- 全站深色主题：CSS 变量 + Element Plus dark css-vars 覆盖
- 登录页 / 仪表盘使用网格背景、光晕、雷达扫描、扫描线等纯 CSS 动效
- ECharts 通过 `echarts/core` 按需注册折线 / 饼图 / 柱状图，体积从完整包降到约 531 KB
- Vite 手动分包：`vue`、`element-plus`、`element-icons`、`echarts` 独立缓存
- 图标只注册项目实际使用的 20 个，图标 chunk 从 170 KB 降到约 26 KB
- 图表统一在 `onBeforeUnmount` 中 `dispose`，并监听窗口 `resize`

### 8.6 两级缓存：Caffeine 默认，Redis 可选

```text
未开启 Redis：Controller → Service → Caffeine（进程内，60 秒）
开启 Redis：  Controller → Service → Redis（跨实例共享，按业务设置 TTL）
```

- 通过 `app.redis.enabled` + `@ConditionalOnProperty` 决定是否注册 `RedisCacheManager`
- 缓存名集中在 `CacheNames`，避免注解里散落字符串
- TTL：设备分页 60 秒、分类 5 分钟、统计 30~300 秒
- `CacheErrorHandler` 对 Redis 故障做限流日志并降级查库，不让缓存拖垮主流程
- 统计缓存由 `StatsCacheEvictor` 在预约变更后主动清理，而不是等 TTL 过期
- 开启 `transactionAware()`，缓存清理延迟到事务提交后执行，避免回滚后误删
- Redis 值使用 `GenericJackson2JsonRedisSerializer`，并显式开启 `defaultTyping`，否则 `PageResult` 反序列化会变成 `LinkedHashMap` 导致 `ClassCastException`（本次联调真实踩坑并修复）

### 8.7 Redis 分布式锁 + 数据库唯一索引双保险

```text
抢约请求
  │
  ├─ SET lock:booking:{deviceId}_{date}_{slotId} {token} NX EX 5
  │     失败 → 「该时段正在被其他同学抢约，请稍后重试」
  │     成功 ↓
  ├─ 业务校验 + insert（active_key 唯一索引）
  ├─ 事务提交
  └─ Lua：GET key == token ? DEL key : 0
```

- 锁粒度为「设备 + 日期 + 时段」，不同设备 / 时段互不阻塞
- 加锁用 `SET NX PX`，保证「判断 + 设置」原子性
- 解锁用 Lua 比对 token，避免误删其他请求的锁
- **事务提交后**才释放锁；提前释放会让下一个请求在本事务提交前拿到锁，仍然撞唯一索引
- 锁 TTL 5 秒，进程崩溃也能自动释放
- Redis 不可用时返回 `NOOP_TOKEN` 直接放行，最终由数据库唯一索引兜底
- 结论：**Redis 锁优化体验（减少无效写冲突），数据库约束保证正确性（绝不超卖）**

### 8.8 WebSocket 实时通知

```text
业务事务 ──提交成功──▶ NotificationService.afterCommit
                              │
                              ├─ 指定用户：BOOKING_CREATED / APPROVED / ...
                              └─ 角色广播：PENDING_CREATED / PENDING_CHANGED
                                        │
                                  NotificationWebSocketHandler
                                  用户 → 多个会话（多标签页）
                                        │
                                  前端 socket.js
                                  消息中心 + 自定义事件刷新页面
```

- 握手阶段用 `JwtHandshakeInterceptor` 校验 `?token=`，解析出 `uid` / `role` 放入会话属性
- 一个用户可同时有多个标签页，会话用 `ConcurrentHashMap` 管理
- 推送统一放在 `afterCommit`：事务回滚不会发「预约成功」的假消息
- 前端 25 秒 ping / 服务端 pong，用于发现半开连接；断线指数退避重连
- 前端把消息分成两类：
  - 消息中心：预约 / 审批 / 违约等需要阅读的通知
  - 静默事件：`PENDING_CHANGED` 只刷新角标；`lab:booking-changed` 让看板、列表、图表自动刷新
- WebSocket 发送对 session 加锁，避免多线程同时写同一个连接

### 8.9 Docker 镜像分层

- 后端多阶段构建：Maven 构建层与 JRE 运行层分离，运行镜像更小
- 先 `COPY pom.xml` 再 `dependency:go-offline`，依赖不变时复用缓存层
- 前端多阶段构建：Node 构建产物交给 Nginx，运行镜像不含 Node
- Compose 用健康检查控制启动顺序：MySQL / Redis 就绪 → 后端 → 前端
- 敏感配置通过 `.env` 注入，`.env` 不提交；镜像中不包含 `application-local.yml`

---

## 9. 已验证场景

| 场景 | 结果 |
| --- | --- |
| 三角色登录、禁用账号登录 | ✅ |
| 学生访问用户管理被拦截 | ✅ 路由守卫 + 后端 403 |
| 设备 / 分类 CRUD、重复编号、删除保护 | ✅ |
| 预约创建、重复时段冲突、过去日期拦截 | ✅ |
| 教师审批、管理员审批、自己审批自己被拒 | ✅ |
| 签到窗口校验、重复签到 | ✅ |
| 取消预约、释放占位 | ✅ |
| 定时任务自动完成 / 违约 / 取消 | ✅ |
| 预约趋势补零、状态分布、设备热度 | ✅ |
| 用户新增 / 编辑 / 重置密码 / 禁用自己 | ✅ |
| 修改密码后旧密码失效、新密码可登录 | ✅ |
| 前端生产构建、浏览器控制台无报错 | ✅ |
| Redis 缓存命中（设备分页第二次请求走缓存） | ✅ 真实 Redis 8.10 |
| 缓存 TTL 与统计缓存主动清理 | ✅ `redis-cli monitor` 捕获 DEL |
| Redis 锁被占用时返回友好提示 | ✅ 400 + 中文提示 |
| 锁在事务提交后释放（`EXISTS=0`） | ✅ |
| WebSocket 学生收到提交 / 通过 / 取消 | ✅ |
| WebSocket 教师收到新待审批 / 队列变化 | ✅ |
| 多标签页 / 在线用户统计（`websocketOnline`） | ✅ |
| Docker Compose 文件编写与静态检查 | ✅（本机无 Docker，未实际构建） |

---

## 10. 下一阶段规划

1. **消息持久化**：站内信落库、未读数、离线消息补偿（当前通知只推在线用户）
2. **导出与审计**：预约记录 Excel 导出、操作日志、登录日志
3. **可观测性**：Actuator + Prometheus 指标、慢 SQL、Redis 命中率看板
4. **工程化**：单元测试 / Testcontainers、Swagger / OpenAPI、GitHub Actions 自动构建镜像
5. **部署增强**：HTTPS、多环境配置、Kubernetes / 云服务器部署、备份与恢复
6. **业务增强**：违约次数限制、候补队列、邮件 / 企业微信通知

学习顺序和每阶段重点见 [docs/学习路线.md](docs/学习路线.md)。

---

## 11. 常见问题

**端口被占用**

```powershell
Get-NetTCPConnection -LocalPort 8091 -State Listen
Stop-Process -Id <PID> -Force
```

**启动报数据库连接失败**

确认 MySQL 已启动、`application-local.yml` 中的密码正确。IDE 运行时注意工作目录是 `backend`。

**接口返回 401**

token 过期或未携带。前端会自动清理登录态并跳回登录页；调试时重新登录获取新 token。

**页面能打开但接口 404**

确认后端在 8091、前端代理指向 `http://127.0.0.1:8091`，并确认修改后端代码后已重新打包 / 重启。

**Redis 没启动 / 想临时关掉**

默认 `REDIS_ENABLED=false`，不启动 Redis 也能跑。若设置了 `true` 但 Redis 未启动：

- 缓存会通过 `CacheErrorHandler` 降级查库，接口仍可用
- 控制台会打印限流后的 WARN 日志
- 想彻底关闭：去掉 `REDIS_ENABLED` 环境变量，重启后端

**顶栏显示 OFFLINE / 收不到实时通知**

1. 确认后端 `/api/health` 的 `websocketOnline` 是否变化
2. 开发期确认 Vite 已配置 `/ws` 代理且 `ws: true`
3. 生产环境确认 Nginx `/ws/` 转发 `Upgrade` / `Connection` 头
4. token 过期时握手会返回 401，重新登录即可

**Docker 构建很慢 / 失败**

- 首次构建需要下载 Maven / npm 依赖，属正常现象
- 确认 Docker Desktop 已启动，且能访问 Docker Hub
- 后端镜像构建需要联网下载依赖；国内可配置 Maven / npm 镜像
- 若 MySQL 健康检查一直失败，查看 `docker compose logs mysql`，确认 `.env` 中密码没有特殊字符导致解析问题

**看板没有数据**

首次启动会自动写入演示数据；如果 `booking` 表已有数据则不会重复插入。可以清空 `booking` 表后重启后端重新生成。

---

## 12. 安全说明

- 数据库密码、JWT 密钥只放在本地 `application-local.yml` 或部署环境变量
- 示例文件只保留占位符，`.env` 已被 `.gitignore` 忽略
- 演示账号密码仅用于本地学习，部署前必须修改
- 生产环境建议：HTTPS、更短 token 有效期 + 刷新机制、登录失败限流、操作审计、密码复杂度策略
- 当前 JWT 无黑名单，禁用账号后已签发 token 在过期前仍有效；生产环境可结合 Redis 做主动失效
- WebSocket 使用查询参数传 token，可能出现在 Nginx 访问日志中；生产环境建议改为 HttpOnly Cookie 或一次性短期 ticket
- WebSocket 当前允许所有来源（`setAllowedOriginPatterns("*")`），上线时应限制为实际域名
