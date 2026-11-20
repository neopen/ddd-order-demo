# DDD 订单示例 · 文档中心

本目录系统化说明项目中的 **DDD 战术设计** 与 **微服务协作能力**。文档以代码为准，关键结论都对应到具体类、表与配置项，可直接用于评审、分享和二次开发。

## 1. 项目定位

`ddd-order-demo` 是一个可运行的教学型工程，用订单场景串联：

- 领域建模：聚合、实体、值对象、领域服务、领域事件、仓储、状态机。
- 微服务协作：独立库存/支付服务、OpenFeign、熔断降级、接口幂等、Outbox + Kafka。
- 分布式一致性：持久化 Saga、库存预占与 TTL、收件箱去重、事件驱动读模型。
- 架构治理：ArchUnit 在构建期强制分层与限界上下文边界。

技术栈：Spring Boot 3.2、Java 17+（当前以 Java 21 运行）、Spring Data JPA、Spring StateMachine、Spring Kafka、Resilience4j、H2、ArchUnit。

## 2. 文档地图

| 文档 | 内容 | 适合谁 |
| --- | --- | --- |
| [01-ddd-tactical-design.md](./01-ddd-tactical-design.md) | 限界上下文、分层、聚合/值对象/领域服务/事件/仓储的设计原则与落点 | 架构师、后端开发 |
| [02-order-aggregate-and-statemachine.md](./02-order-aggregate-and-statemachine.md) | 订单聚合不变量、金额模型、状态机全量迁移与并发控制 | 后端开发、测试 |
| [03-microservice-collaboration.md](./03-microservice-collaboration.md) | ACL、Feign、熔断、幂等、Outbox、Kafka 的协作方式 | 后端开发、运维 |
| [04-persistent-saga.md](./04-persistent-saga.md) | 持久化 Saga：步骤、独立事务、重试、超时、补偿、故障注入 | 后端开发、架构师 |
| [05-inventory-reservation.md](./05-inventory-reservation.md) | 库存预占聚合、TTL 回收、与订单生命周期的联动 | 后端开发、测试 |
| [06-event-driven-cqrs.md](./06-event-driven-cqrs.md) | 集成事件契约、CQRS 读模型、投影、收件箱去重 | 架构师、后端开发 |
| [07-architecture-and-testing.md](./07-architecture-and-testing.md) | ArchUnit 规则、测试分层、如何新增用例 | 开发、评审者 |
| [08-runbook-and-troubleshooting.md](./08-runbook-and-troubleshooting.md) | 启动、配置、接口清单、演练脚本、排障手册 | 所有人 |

## 3. 推荐阅读路径

- **初次了解**：01 → 02 → 08 的「快速启动」。
- **准备面试/分享**：01 → 02 → 04 → 06，重点理解“为什么这样设计”。
- **排查线上问题**：08 → 04 → 05 → 06。
- **扩展功能**：03 → 04 → 07，先看边界与扩展点，再动手。

## 4. 一分钟跑起来

```bash
# 1. 启动模拟库存服务（9001）
mvn -Pmock-inventory spring-boot:run

# 2. 启动模拟支付服务（9002）
mvn -Pmock-payment spring-boot:run

# 3. 启动订单服务（8080）
mvn spring-boot:run

# 4. 创建一个演示订单
curl http://localhost:8080/api/orders/demo

# 5. 查看 Saga 轨迹
curl http://localhost:8080/api/orders/1/saga
```

默认关闭 Kafka，Outbox 只落库并记录日志；完整链路与开关说明见 08。

## 5. 术语对照

| 术语 | 本项目实现 | 说明 |
| --- | --- | --- |
| 限界上下文 (Bounded Context) | 订单、库存、支付 | 每个上下文有自己的模型与语言 |
| 聚合根 (Aggregate Root) | `Order`、`StockReservation` | 外部修改聚合的唯一入口 |
| 实体 (Entity) | `ProductItem`、`OrderPayment`、`OrderStatusHistory` | 有标识、生命周期随聚合 |
| 值对象 (Value Object) | `Money`、`OrderPricing` | 不可变、以值判等 |
| 领域服务 (Domain Service) | `OrderPricingService` | 跨聚合/无状态业务规则 |
| 领域事件 (Domain Event) | `OrderCreatedEvent`、`OrderStatusChangedEvent` | 聚合内登记、应用层发布 |
| 仓储 (Repository) | `OrderRepository` 等 | 领域定义端口，基础设施适配 |
| 防腐层 (ACL) | `InventoryServiceAcl`、`PaymentServiceAcl` | 隔离外部协议与异常 |
| 发布语言 (Published Language) | `contracts.IntegrationEvent` | 跨服务稳定事件契约 |
| Saga | `OrderCreationSaga` | 跨服务流程编排与补偿 |
| CQRS | 命令服务 / 写模型查询 / 读模型查询 | 读写职责分离 |
| 收件箱 (Inbox) | `tab_inbox_event` | 消费端幂等去重 |

## 6. 文档约定

- 代码路径均相对于仓库根目录。
- 时序/状态图使用 Mermaid，GitHub、IDEA Markdown 插件均可渲染。
- 配置项以 `application.yml` 为准；示例中的端口与库名与默认配置一致。
- 标注「生产化增强」的内容表示示例未实现，需要按真实环境补齐。
