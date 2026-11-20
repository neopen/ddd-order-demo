# 08 · 运行手册与排障

本篇是“拿到仓库就能跑”的操作手册：启动顺序、端口、配置、接口清单、完整演练脚本、故障演练与排障表。

---

## 1. 环境要求

| 项 | 要求 |
| --- | --- |
| JDK | 17 及以上（当前以 Java 21 运行） |
| Maven | 3.8+ |
| 操作系统 | Windows / macOS / Linux 均可 |
| Kafka | 可选，默认关闭 |
| 数据库 | H2 内存库，无需安装 |

验证：

```bash
java -version
mvn -version
```

---

## 2. 服务与端口

| 服务 | 端口 | 启动命令 | 说明 |
| --- | --- | --- | --- |
| 订单服务 | 8080 | `mvn spring-boot:run` | 主应用，含写模型、读模型、Saga、Outbox |
| 库存模拟服务 | 9001 | `mvn -Pmock-inventory spring-boot:run` | 内存库存 + 预占聚合 + TTL 回收 |
| 支付模拟服务 | 9002 | `mvn -Pmock-payment spring-boot:run` | 内存支付，按订单号幂等 |
| Kafka | 9092 | 自行启动 | 默认不连接 |

启动顺序建议：先库存、再支付、最后订单。顺序并非强制，但订单服务启动后若立刻创建订单，下游未就绪会触发熔断降级。

三个服务需要三个终端分别运行。Maven Profile 通过覆盖 `start-class` 切换启动类：

```xml
<!-- pom.xml 片段 -->
<profile>
    <id>mock-inventory</id>
    <properties>
        <start-class>com.neopen.ddd.mocks.inventory.InventoryServiceApplication</start-class>
    </properties>
</profile>
<profile>
    <id>mock-payment</id>
    <properties>
        <start-class>com.neopen.ddd.mocks.payment.PaymentServiceApplication</start-class>
    </properties>
</profile>
```

模拟服务使用独立的 `mock-inventory.yml`、`mock-payment.yml`，只保留端口与日志配置，不加载数据源、JPA、Kafka。

---

## 3. 配置全量表

配置来源：`src/main/resources/application.yml`。

### 3.1 服务与数据源

| 配置键 | 默认值 | 说明 |
| --- | --- | --- |
| `server.port` | `8080` | 订单服务端口 |
| `spring.application.name` | `ddd-order-service` | 应用名 |
| `spring.datasource.url` | `jdbc:h2:mem:orderdb` | 内存库，重启即清空 |
| `spring.datasource.username` | `sa` | H2 用户名 |
| `spring.datasource.password` | 空 | H2 密码 |
| `spring.jpa.open-in-view` | `false` | 避免视图层持有 Session |
| `spring.jpa.hibernate.ddl-auto` | `create-drop` | 启动建表、关闭删表 |
| `spring.jpa.show-sql` | `true` | 打印 SQL，便于观察 |
| `spring.h2.console.enabled` | `true` | 开启 H2 控制台 |
| `spring.h2.console.path` | `/h2` | 控制台路径 |

H2 控制台：

```text
URL:      http://localhost:8080/h2
JDBC URL: jdbc:h2:mem:orderdb
User:     sa
Password: （留空）
```

### 3.2 Kafka

| 配置键 | 默认值 | 说明 |
| --- | --- | --- |
| `spring.kafka.bootstrap-servers` | `localhost:9092` | Broker 地址 |
| `spring.kafka.producer.acks` | `all` | 所有副本确认 |
| `spring.kafka.producer.enable-idempotence` | `true` | 生产者幂等 |
| `spring.kafka.consumer.group-id` | `order-ddd` | 默认消费组 |
| `app.messaging.kafka-enabled` | `false` | 关闭时中继只记日志 |
| `app.messaging.order-events-topic` | `order-events` | 订单事件主题 |
| `app.messaging.consumer-group` | `order-ddd-consumer` | `@KafkaListener` 覆盖的消费组 |
| `app.messaging.consumer-enabled` | `false` | 是否启动消费者 |

### 3.3 Outbox

| 配置键 | 默认值 | 说明 |
| --- | --- | --- |
| `app.outbox.relay-enabled` | `true` | 是否启用中继 |
| `app.outbox.relay-interval-ms` | `3000` | 扫描间隔 |
| `app.outbox.max-attempts` | `5` | 最大失败次数，达到后 `FAILED` |

### 3.4 库存与 Saga

| 配置键 | 默认值 | 说明 |
| --- | --- | --- |
| `app.inventory.reservation-ttl-seconds` | `900` | 预占有效期（秒） |
| `app.saga.reserve-max-attempts` | `2` | 库存预占最大尝试次数 |
| `app.saga.timeout-enabled` | `true` | 是否启用超时看门狗 |
| `app.saga.timeout-seconds` | `300` | 超过该时长仍 `RUNNING` 视为超时 |
| `app.saga.timeout-scan-interval-ms` | `60000` | 超时扫描间隔 |
| `app.saga.fault-step` | 空 | 故障注入步骤：`PRICE` / `RESERVE_STOCK` / `PERSIST_ORDER` |

### 3.5 外部服务与熔断

| 配置键 | 默认值 | 说明 |
| --- | --- | --- |
| `inventory.service.url` | `http://localhost:9001` | 库存服务地址 |
| `payment.service.url` | `http://localhost:9002` | 支付服务地址 |
| `spring.cloud.openfeign.client.config.inventory-client.connectTimeout` | `2000` | 库存连接超时（毫秒） |
| `spring.cloud.openfeign.client.config.inventory-client.readTimeout` | `3000` | 库存读超时（毫秒） |
| `spring.cloud.openfeign.client.config.payment-client.connectTimeout` | `2000` | 支付连接超时（毫秒） |
| `spring.cloud.openfeign.client.config.payment-client.readTimeout` | `3000` | 支付读超时（毫秒） |
| `resilience4j.circuitbreaker.configs.default.slidingWindowSize` | `10` | 滑动窗口请求数 |
| `resilience4j.circuitbreaker.configs.default.minimumNumberOfCalls` | `5` | 计算失败率的最小请求数 |
| `resilience4j.circuitbreaker.configs.default.failureRateThreshold` | `50` | 失败率阈值（百分比） |
| `resilience4j.circuitbreaker.configs.default.waitDurationInOpenState` | `10000` | 打开状态持续毫秒 |

### 3.6 可观测

| 配置键 | 默认值 | 说明 |
| --- | --- | --- |
| `management.endpoints.web.exposure.include` | `health,info,metrics,circuitbreakers,circuitbreakerevents` | 暴露的 Actuator 端点 |
| `management.endpoint.health.show-details` | `always` | 健康检查显示细节 |

常用端点：

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/metrics
curl http://localhost:8080/actuator/circuitbreakers
curl http://localhost:8080/actuator/circuitbreakerevents
```

---

## 4. 接口清单

### 4.1 订单命令与查询

| 方法 | 路径 | 说明 | 幂等键 |
| --- | --- | --- | --- |
| POST | `/api/orders` | 创建订单 | 可选 `Idempotency-Key` |
| GET | `/api/orders/demo` | 创建随机演示订单 | 无 |
| GET | `/api/orders` | 分页查询 | 无 |
| GET | `/api/orders/{id}` | 订单详情 | 无 |
| GET | `/api/orders/{id}/history` | 状态流转历史 | 无 |
| GET | `/api/orders/{id}/saga` | 创建 Saga 轨迹 | 无 |
| POST | `/api/orders/{id}/pay` | 支付 | 可选 `Idempotency-Key` |
| POST | `/api/orders/{id}/ship` | 发货 | 无 |
| POST | `/api/orders/{id}/receive` | 确认收货 | 无 |
| POST | `/api/orders/{id}/complete` | 完成订单 | 无 |
| POST | `/api/orders/{id}/cancel` | 取消订单 | 无 |
| POST | `/api/orders/{id}/refund` | 申请退款 | 无 |
| POST | `/api/orders/{id}/refund/succeeded` | 退款成功回执 | 无 |
| POST | `/api/orders/{id}/refund/failed` | 退款失败回执 | 无 |
| POST | `/api/orders/{id}/close` | 关闭订单 | 无 |

分页参数：

| 参数 | 默认 | 约束 |
| --- | --- | --- |
| `page` | `0` | 小于 0 按 0 处理 |
| `size` | 由 `OrderQuery` 默认值决定 | 夹在 1..100 |
| `status` | 空 | 精确匹配枚举 |
| `customerId` | 空 | 精确匹配 |
| `orderNum` | 空 | 忽略大小写模糊匹配 |

排序固定为 `createdAt desc, id desc`。

### 4.2 读模型查询

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/read-models/orders` | 最近 100 条投影，可按 `status` 过滤 |
| GET | `/api/read-models/orders/{orderNum}` | 按订单号查询投影 |

### 4.3 库存模拟服务（9001）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/inventory/reservations` | 创建预占，请求体见下 |
| POST | `/api/inventory/reservations/{id}/confirm` | 确认预占 |
| POST | `/api/inventory/reservations/{id}/release` | 释放预占 |
| GET | `/api/inventory/reservations/{id}` | 查询预占单 |
| GET | `/api/inventory/{productId}` | 查询商品可售库存 |

预占请求体：

```json
{
  "productId": 11,
  "quantity": 2,
  "orderNum": "ORD-DEMO-001",
  "ttlSeconds": 900
}
```

响应关键字段：`success`、`status`、`message`、`reservationId`、`data`。

### 4.4 支付模拟服务（9002）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/payments/process` | 支付，按订单号幂等 |
| POST | `/api/payments/cancel` | 撤销支付 |
| GET | `/api/payments/{orderNum}` | 查询支付回执 |

支付请求体：

```json
{
  "orderNum": "ORD-DEMO-001",
  "customerId": "CUSTOMER-1001",
  "amount": 190.00,
  "currency": "CNY",
  "paymentMethod": "ALIPAY"
}
```

### 4.5 错误响应

统一格式：

```json
{
  "timestamp": "2026-11-20T10:20:00Z",
  "status": 409,
  "code": "STATE_CONFLICT",
  "message": "只有待支付订单才能支付，当前状态：PAID"
}
```

| HTTP | code | 触发场景 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | 请求体/参数校验失败 |
| 400 | `BAD_REQUEST` | 订单不存在、读模型不存在等非法参数 |
| 409 | `STATE_CONFLICT` | 状态不允许、支付失败、库存释放失败 |
| 409 | `CONCURRENT_MODIFICATION` | JPA 乐观锁冲突 |
| 500 | `INTERNAL_ERROR` | 未预期异常 |

---

## 5. 完整演练脚本

以下命令假设三个服务已启动，使用 curl（Windows 下可用 `curl.exe`）。

### 5.1 创建演示订单

```bash
curl -i http://localhost:8080/api/orders/demo
```

响应示例（HTTP 201）：

```json
{
  "orderId": 1,
  "orderNum": "ORD-20261120-0001",
  "status": "CREATED",
  "totalAmount": {"amount": 100.00, "currency": "CNY"},
  "discountAmount": {"amount": 10.00, "currency": "CNY"},
  "payableAmount": {"amount": 90.00, "currency": "CNY"},
  "discountPolicy": "FULL_REDUCTION",
  "createdAt": "2026-11-20T10:15:30"
}
```

### 5.2 显式创建并验证幂等

```bash
curl -i -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: create-1001-001" \
  -d '{
    "customerId": 1001,
    "items": [
      {"productId": 11, "productName": "机械键盘", "quantity": 1, "price": 100.00}
    ]
  }'
```

用同一个 `Idempotency-Key` 再执行一次，应返回同一 `orderId` / `orderNum`，不会新增订单。

### 5.3 查询写模型

```bash
curl "http://localhost:8080/api/orders?page=0&size=10&customerId=1001"
curl "http://localhost:8080/api/orders?status=CREATED"
curl "http://localhost:8080/api/orders?orderNum=ord-2026"
curl http://localhost:8080/api/orders/1
curl http://localhost:8080/api/orders/1/history
```

### 5.4 查看创建 Saga

```bash
curl http://localhost:8080/api/orders/1/saga
```

成功时示例：

```json
{
  "sagaId": "SAGA-...",
  "sagaType": "ORDER_CREATE",
  "businessKey": "ORD-20261120-0001",
  "status": "COMPLETED",
  "currentStep": "PERSIST_ORDER",
  "errorMessage": null,
  "startedAt": "2026-11-20T10:15:30",
  "finishedAt": "2026-11-20T10:15:30",
  "steps": [
    {"stepName": "PRICE", "status": "SUCCEEDED", "attempts": 1, "errorMessage": null},
    {"stepName": "RESERVE_STOCK", "status": "SUCCEEDED", "attempts": 1, "errorMessage": null},
    {"stepName": "PERSIST_ORDER", "status": "SUCCEEDED", "attempts": 1, "errorMessage": null}
  ]
}
```

### 5.5 支付

```bash
curl -i -X POST http://localhost:8080/api/orders/1/pay \
  -H "Idempotency-Key: pay-1-001"
```

响应：

```json
{
  "orderId": 1,
  "orderNum": "ORD-20261120-0001",
  "status": "PAID",
  "message": "支付成功"
}
```

重复执行同一幂等键，返回相同结果。

### 5.6 查询读模型

```bash
curl "http://localhost:8080/api/read-models/orders?status=PAID"
curl http://localhost:8080/api/read-models/orders/ORD-20261120-0001
```

应看到 `status = PAID`、`lastEventType = OrderStatusChangedEvent`。

### 5.7 后续状态流转

```bash
curl -X POST http://localhost:8080/api/orders/1/ship
curl -X POST http://localhost:8080/api/orders/1/receive
curl -X POST http://localhost:8080/api/orders/1/complete
```

退款链路：

```bash
curl -X POST http://localhost:8080/api/orders/1/refund          # PAID -> REFUNDING
curl -X POST http://localhost:8080/api/orders/1/refund/succeeded # REFUNDING -> REFUNDED
# 或
curl -X POST http://localhost:8080/api/orders/1/refund/failed     # 恢复退款前状态
```

### 5.8 直接验证库存预占

```bash
# 查询库存
curl http://localhost:9001/api/inventory/11

# 创建预占（记住返回的 reservationId）
curl -X POST http://localhost:9001/api/inventory/reservations \
  -H "Content-Type: application/json" \
  -d '{"productId":11,"quantity":2,"orderNum":"ORD-MANUAL-001","ttlSeconds":900}'

# 查询预占单
curl http://localhost:9001/api/inventory/reservations/{reservationId}

# 确认或释放
curl -X POST http://localhost:9001/api/inventory/reservations/{reservationId}/confirm
curl -X POST http://localhost:9001/api/inventory/reservations/{reservationId}/release
```

### 5.9 直接验证支付幂等

```bash
curl -X POST http://localhost:9002/api/payments/process \
  -H "Content-Type: application/json" \
  -d '{"orderNum":"ORD-MANUAL-001","customerId":"CUSTOMER-1001","amount":190.00,"currency":"CNY","paymentMethod":"ALIPAY"}'

curl http://localhost:9002/api/payments/ORD-MANUAL-001
```

同一 `orderNum` 重复支付，返回首次交易号，不会重复扣款。

---

## 6. 故障演练

### 6.1 注入持久化失败，观察 Saga 补偿

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--app.saga.fault-step=PERSIST_ORDER
```

然后创建订单：

```bash
curl -i -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":3001,"items":[{"productId":21,"productName":"测试商品","quantity":2,"price":60.00}]}'
```

预期：

- HTTP 409，`code = STATE_CONFLICT`。
- 订单未落库（`GET /api/orders` 查不到）。
- 库存预占被释放（库存服务日志出现“库存预占已释放”）。
- Saga 状态 `COMPENSATED`：`RESERVE_STOCK = SUCCEEDED`，`PERSIST_ORDER = FAILED`。

查询 Saga 需要订单存在，但由于订单回滚，`/api/orders/{id}/saga` 查不到。此时应直接查 H2：

```sql
SELECT * FROM tab_saga_instance ORDER BY id DESC;
SELECT * FROM tab_saga_step ORDER BY id;
SELECT * FROM tab_order;
```

这正是 `SagaJournal` 使用 `REQUIRES_NEW` 的意义：业务回滚，轨迹仍在。

### 6.2 注入库存预占失败

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--app.saga.fault-step=RESERVE_STOCK
```

预期：`PRICE = SUCCEEDED`、`RESERVE_STOCK = FAILED`、Saga `COMPENSATED`，没有预占需要释放。

### 6.3 停掉库存服务

1. 不启动 9001，或启动后停止。
2. 创建订单。

预期：

- Feign 调用失败，Fallback 返回失败结果。
- Saga 按 `app.saga.reserve-max-attempts=2` 重试后失败。
- 接口返回 409，订单不落库。
- 连续失败达到熔断阈值后，`/actuator/circuitbreakers` 中库存客户端状态变为 `OPEN`，10 秒后进入半开。

### 6.4 停掉支付服务

1. 创建订单成功。
2. 停止 9002。
3. 支付订单。

预期：支付 ACL 返回失败，接口返回 409，订单保持 `CREATED`，不会写入支付记录或状态历史。

### 6.5 关闭 / 开启 Kafka

默认关闭：

```yaml
app:
  messaging:
    kafka-enabled: false
    consumer-enabled: false
```

行为：Outbox 照常落库；中继扫描后调用 `KafkaEventPublisher`，后者只打印 `[Kafka 未启用] 跳过事件发送` 并返回；中继仍把事件标记为 `PUBLISHED`。

开启：

```yaml
app:
  messaging:
    kafka-enabled: true
    consumer-enabled: true
```

或启动参数：

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--app.messaging.kafka-enabled=true --app.messaging.consumer-enabled=true"
```

前提：

- Kafka 已在 `localhost:9092` 运行。
- 主题 `order-events` 可自动创建（取决于 Broker 配置），或提前创建。

验证：

```bash
# 消费组位点
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --group order-ddd-consumer --describe

# 查看主题消息
kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic order-events --from-beginning
```

### 6.6 验证收件箱去重

重复投递同一事件（例如用 Kafka 工具重放），消费端日志应出现：

```text
重复事件已跳过: eventId=..., eventType=...
```

数据库中 `tab_inbox_event` 对 `consumer + event_id` 只有一条记录。

### 6.7 验证库存 TTL 回收

1. 直接向 9001 创建一条预占，`ttlSeconds` 设为 5。
2. 等待 5 秒以上（库存服务每 5 秒扫描一次）。
3. 查询该预占单，状态应为 `EXPIRED`。
4. 查询商品库存，数量应已归还。

```bash
curl -X POST http://localhost:9001/api/inventory/reservations \
  -H "Content-Type: application/json" \
  -d '{"productId":99,"quantity":3,"orderNum":"ORD-TTL-001","ttlSeconds":5}'
sleep 12
curl http://localhost:9001/api/inventory/reservations/{reservationId}
curl http://localhost:9001/api/inventory/99
```

---

## 7. 数据检查 SQL

通过 H2 控制台或客户端执行。

### 7.1 订单与状态历史

```sql
SELECT id, order_num, customer_id, status, pre_status,
       total_amount, discount_amount, payable_amount,
       stock_reserved, version, created_at, updated_at
FROM tab_order
ORDER BY id DESC;

SELECT order_id, from_status, to_status, event, operator_id, remark, created_at
FROM order_status_history
ORDER BY id DESC;
```

### 7.2 Outbox

```sql
SELECT id, event_id, event_type, aggregate_type, aggregate_id,
       status, attempts, last_error, created_at, published_at
FROM tab_outbox_event
ORDER BY id DESC;

-- 查看待投递 / 失败事件
SELECT * FROM tab_outbox_event WHERE status = 'PENDING' ORDER BY id;
SELECT * FROM tab_outbox_event WHERE status = 'FAILED' ORDER BY id;
```

### 7.3 幂等与收件箱

```sql
SELECT * FROM tab_idempotency_record ORDER BY id DESC;
SELECT * FROM tab_inbox_event ORDER BY id DESC;

-- 已抢占但未确认完成的消费记录
SELECT * FROM tab_inbox_event WHERE processed_at IS NULL;
```

### 7.4 Saga

```sql
SELECT * FROM tab_saga_instance ORDER BY id DESC;
SELECT * FROM tab_saga_step ORDER BY saga_id, id;

-- 长时间 RUNNING 的 Saga
SELECT * FROM tab_saga_instance
WHERE status = 'RUNNING' AND updated_at < DATEADD('MINUTE', -5, CURRENT_TIMESTAMP);
```

### 7.5 读模型

```sql
SELECT * FROM tab_order_summary_view ORDER BY updated_at DESC;

-- 对比写模型与读模型状态
SELECT o.order_num, o.status AS write_status, v.status AS read_status
FROM tab_order o
LEFT JOIN tab_order_summary_view v ON v.order_num = o.order_num
WHERE v.order_num IS NULL OR o.status <> v.status;
```

### 7.6 库存预占（模拟服务）

库存与预占只存在 9001 进程内存，不在订单库中，无法用 SQL 查询。请使用：

```bash
curl http://localhost:9001/api/inventory/reservations/{reservationId}
```

订单侧只保存预占单号，可通过详情接口或直接查 `tab_order_items`：

```sql
SELECT id, order_id, product_id, product_name, quantity,
       unit_price, unit_currency, subtotal, subtotal_currency, reservation_id
FROM tab_order_items
ORDER BY id DESC;
```

---

## 8. 排障表

| 症状 | 可能原因 | 排查与处理 |
| --- | --- | --- |
| 启动报端口占用 | 8080/9001/9002 已被占用 | `netstat -ano \| findstr :8080` 找到进程并停止，或改端口 |
| 创建订单返回 409 | 库存服务未启动、库存不足、Saga 故障注入 | 看订单日志与 `tab_saga_step`；确认 `fault-step` 为空；确认 9001 已启动 |
| 支付返回 409 | 订单不是 `CREATED`、支付服务未启动 | 查订单状态；确认 9002 已启动；看支付 ACL 日志 |
| 读模型查不到订单 | 投影未更新、查询了错误的订单号 | 用 `GET /api/orders/{id}` 对照；查 `tab_order_summary_view` |
| 读模型状态落后 | 跨进程 Kafka 延迟或消费者未启动 | 查 Outbox 状态；确认 `consumer-enabled=true`；看消费组位点 |
| Outbox 一直 `PENDING` | 中继未启用、Kafka 不可达 | 检查 `app.outbox.relay-enabled`；看中继日志；检查 9092 |
| Outbox 变 `FAILED` | 重试达到上限 | 查 `last_error`；修复后手工把 `status` 重置为 `PENDING` |
| Saga 一直 `RUNNING` | 步骤卡住、超时任务未启用 | 检查 `app.saga.timeout-enabled`；查 `tab_saga_step` 当前步骤 |
| Saga 查不到 | 订单创建失败已回滚 | 直接查 `tab_saga_instance`；这是正常现象 |
| 熔断器一直 `OPEN` | 下游持续失败 | `/actuator/circuitbreakers`；修复下游后等待半开或重启 |
| `/actuator/health` 不健康 | 数据库、磁盘或熔断器异常 | `show-details=always` 会显示具体组件 |
| H2 控制台打不开 | 路径或配置被覆盖 | 默认 `/h2`；确认 `spring.h2.console.enabled=true` |
| 重启后数据全没了 | H2 内存库 + `create-drop` | 预期行为；需要持久化请改用文件库或外部数据库 |
| 测试报 Kafka 连接失败 | 消费者自动启动 | 加 `spring.kafka.listener.auto-startup=false` 或 Mock 发送器 |
| 中文乱码 | 控制台编码非 UTF-8 | 设置 `chcp 65001` 或 IDE 文件编码为 UTF-8 |

---

## 9. 重置环境

最简单的方式是重启订单服务：H2 是内存库且 `ddl-auto=create-drop`，重启即重建空库。

模拟服务同理：重启 9001/9002 后库存恢复为每个商品 1000，支付回执全部清空。

如果只想清数据而不重启：

```sql
DELETE FROM tab_saga_step;
DELETE FROM tab_saga_instance;
DELETE FROM tab_inbox_event;
DELETE FROM tab_outbox_event;
DELETE FROM tab_idempotency_record;
DELETE FROM order_status_history;
DELETE FROM tab_order_items;
DELETE FROM tab_order_summary_view;
DELETE FROM tab_order;
```

注意外键/顺序：先删子表再删主表。

---

## 10. 本篇小结

- 三个进程：订单 8080、库存 9001、支付 9002；Kafka 可选。
- 默认配置即可跑通创建、支付、取消、退款、读模型与 Saga 查询。
- 故障注入是验证补偿链路的最快方式：`--app.saga.fault-step=PERSIST_ORDER`。
- Outbox、Saga、收件箱、预占都有对应的表或接口可查，排障时优先看数据而不是猜。
- H2 内存库重启即清空，适合演示，不适合保存数据。

返回文档中心：[README.md](./README.md)
