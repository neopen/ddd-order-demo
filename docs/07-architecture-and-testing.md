# 07 · 架构边界与测试策略

本篇说明两件事：

1. 如何用 ArchUnit 把分层与限界上下文边界变成构建期断言，防止架构在迭代中被悄悄侵蚀。
2. 项目的测试如何分层，每个测试类负责什么，新增功能时应该补哪些用例。

---

## 1. 为什么需要架构测试

DDD 的边界写在文档里很容易，守住很难。常见的腐蚀路径：

- “只是查个库存” → 接口层直接注入 `InventoryClient`，绕过 ACL 和应用服务。
- “赶个需求” → 领域实体引入 `@RestController` 或 Kafka 注解。
- “复用一下” → 订单领域层 import 了模拟库存服务的类。
- “图省事” → 仓储实现类被领域服务直接 new 出来。

这些问题编译能过、测试能过，但依赖方向已经反了。ArchUnit 的价值是把“口头约定”变成可执行的断言：只要越界，`mvn test` 直接失败，评审时不需要靠人肉记忆。

本项目使用 `com.tngtech.archunit:archunit-junit5:1.2.1`（test 作用域），测试类为 `src/test/java/com/neopen/ddd/architecture/ArchitectureBoundaryTest.java`。

---

## 2. 目标依赖方向

```text
┌──────────────────────────────────────────────┐
│ interfaces/web                               │  HTTP 协议适配
│   ↓ 只依赖 application / domain 契约          │
├──────────────────────────────────────────────┤
│ application                                  │  用例编排、事务、Saga
│   ↓ 依赖 domain，使用 infrastructure 端口     │
├──────────────────────────────────────────────┤
│ domain                                       │  聚合、值对象、领域服务、事件、仓储接口
│   ↑ 不依赖任何外层                            │
├──────────────────────────────────────────────┤
│ infrastructure                               │  JPA、Outbox、Kafka、Feign、ACL、投影
│   ↓ 实现 domain 端口                          │
└──────────────────────────────────────────────┘

mocks（库存/支付模拟服务）是独立进程边界，不依赖订单服务的 application/domain/interfaces。
```

允许的依赖：

- `interfaces → application → domain`
- `infrastructure → domain`（实现仓储接口、发布事件、调用 ACL）
- `application → infrastructure` 仅限通过端口/接口注入（如 `DomainEventPublisher`、ACL 接口）

不允许的依赖：

- `domain → application / infrastructure / interfaces / mocks`
- `interfaces → infrastructure / mocks`（接口层不得绕过应用服务直接访问技术设施）
- `mocks → application / domain / interfaces`（模拟服务不是订单服务的一部分）

---

## 3. 五条 ArchUnit 规则

`ArchitectureBoundaryTest` 在类加载时导入主代码，排除测试类：

```java
private static final JavaClasses CLASSES = new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.neopen.ddd");
```

### 3.1 领域层不得依赖外层

```java
ArchRule rule = noClasses()
        .that().resideInAnyPackage(
                "..ddd.domain.model..",
                "..ddd.domain.event..",
                "..ddd.domain.service..",
                "..ddd.domain.repository..")
        .should().dependOnClassesThat().resideInAnyPackage(
                "..ddd.application..",
                "..ddd.infrastructure..",
                "..ddd.interfaces..",
                "..ddd.mocks..");
rule.check(CLASSES);
```

覆盖：聚合根、实体、值对象、领域事件、领域服务、仓储接口。

为什么重要：领域层是系统中最稳定、最有价值的部分。它一旦依赖 Spring Web、Kafka 或某个外部服务，就无法脱离框架单独测试，也无法在未来抽出独立模块。

### 3.2 领域模型与领域服务保持技术中立

```java
ArchRule rule = noClasses()
        .that().resideInAnyPackage(
                "..ddd.domain.model..",
                "..ddd.domain.service..")
        .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework.web..",
                "org.springframework.kafka..",
                "org.springframework.cloud..");
rule.check(CLASSES);
```

覆盖：只约束 `domain.model` 与 `domain.service`，检查是否 import 了 Web、Kafka、OpenFeign 相关包。

不在约束范围内的部分：

- `domain.statemachine`：状态机配置本质是技术实现（Spring StateMachine），放在领域包下是为了内聚，不能要求它不依赖框架。
- `domain.repository`：本示例的仓储接口继承 `JpaRepository`，属于有意的工程折衷，换取更少的样板代码。

这两处折衷必须在文档中明确，否则后来者会误以为“领域层可以随便依赖框架”。

### 3.3 接口层不得直接访问基础设施与模拟服务

```java
ArchRule rule = noClasses()
        .that().resideInAPackage("..ddd.interfaces..")
        .should().dependOnClassesThat().resideInAnyPackage(
                "..ddd.infrastructure..",
                "..ddd.mocks..");
rule.check(CLASSES);
```

为什么重要：Controller 只做协议适配。如果它直接调用 Feign 客户端或 JPA 仓储，事务边界、幂等、ACL 转换、领域事件都会被绕过，接口层会逐渐变成“第二套业务逻辑”。

### 3.4 模拟服务保持独立进程边界

```java
ArchRule rule = noClasses()
        .that().resideInAPackage("..ddd.mocks..")
        .should().dependOnClassesThat().resideInAnyPackage(
                "..ddd.application..",
                "..ddd.domain..",
                "..ddd.interfaces..");
rule.check(CLASSES);
```

为什么重要：`mocks` 模拟独立部署的库存/支付微服务，只能通过 HTTP 契约与订单服务交互。如果它反向依赖订单领域模型，就退化成“同进程模块”，微服务边界失去意义。

### 3.5 仓储必须是领域层接口

```java
ArchRule rule = classes()
        .that().haveSimpleNameEndingWith("Repository")
        .and().resideInAPackage("..ddd.domain..")
        .should().beInterfaces();
rule.check(CLASSES);
```

为什么重要：这是依赖倒置的落点。领域定义“我需要什么持久化能力”，基础设施提供实现；领域不关心是 JPA、MyBatis 还是内存存储。

注意：该规则只约束 `domain` 包下以 `Repository` 结尾的类型，基础设施层的 `OrderSummaryProjectionRepository` 不在范围内。

---

## 4. 规则失败时怎么读

ArchUnit 失败信息通常形如：

```text
Architecture Violation [Priority: MEDIUM] - Rule 'no classes that reside in any package
['..ddd.domain.model..', ...] should not depend on classes that reside in any package
['..ddd.application..', ...]' was violated (1 times):
Method <com.neopen.ddd.domain.model.Order.foo()> calls method
<com.neopen.ddd.application.service.XxxService.bar()> in (Order.java:123)
```

读法：

1. 看 `Rule ...`：哪条规则失败。
2. 看 `was violated (N times)`：有几处越界。
3. 看 `Method <...> calls method <...> in (File.java:line)`：具体调用点。

处理方式：

- 如果是设计错误：调整依赖方向，把逻辑放回正确层次。
- 如果是合理折衷：更新规则并在测试类注释中说明原因，不要直接删规则。
- 如果是误报（如测试夹具）：确认是否应排除测试类或调整包范围。

---

## 5. 如何新增一条规则

以“应用层不得依赖接口层”为例：

```java
@Test
void application_should_not_depend_on_interfaces() {
    ArchRule rule = noClasses()
            .that().resideInAPackage("..ddd.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..ddd.interfaces..",
                    "..ddd.mocks..");
    rule.check(CLASSES);
}
```

建议：

- 一次只加一条规则，先跑通再提交，避免一次引入大量失败难以定位。
- 规则名用业务语言描述约束，不要用 `rule1`、`rule2`。
- 在测试方法上写清楚“为什么需要这条规则”，而不是只写“测试应用层依赖”。
- 新规则先在当前代码上验证是否已经满足；如果不满足，先修代码或明确折衷。

---

## 6. 测试分层

项目采用三层测试，越往下越慢、越接近真实：

| 层次 | 典型注解 | 覆盖内容 | 速度 |
| --- | --- | --- | --- |
| 单元测试 | 无 Spring 容器 | 聚合不变量、值对象、定价策略、预占状态机 | 毫秒级 |
| 集成测试 | `@SpringBootTest`、`@AutoConfigureMockMvc` | 状态机 + 数据库、HTTP 全链路、Saga 补偿 | 秒级 |
| 架构测试 | ArchUnit | 分层与上下文边界 | 毫秒级 |

### 6.1 单元测试

不启动 Spring，直接构造对象：

- `OrderTest`：订单聚合的退款恢复、支付记录、订单项修改限制。
- `OrderPricingServiceTest`：满减、折扣、最优策略选择。
- `StockReservationTest`：预占确认、释放幂等、TTL 过期、非法参数。

适合：

- 业务规则复杂、分支多。
- 不需要验证数据库映射或 HTTP 协议。
- 希望快速反馈，失败时定位到具体规则。

### 6.2 集成测试

启动真实 Spring 容器与 H2：

- `OrderStateMachineIntegrationTest`：真实状态机 + 真实仓储，验证迁移落库。
- `OrderQueryTest`：通过 HTTP 查询列表与详情。
- `OrderCreatePayFlowIntegrationTest`：创建 → 支付 → 查询 → 读模型 → Saga 全链路。
- `OrderSagaCompensationTest`：故障注入下的事务回滚与补偿轨迹。

共同策略：

- 只 Mock 外部协作（ACL、Kafka 发送器），不 Mock 自己的领域逻辑。
- 使用独立 H2 库名，避免测试上下文之间建表冲突。
- `OrderCreatePayFlowIntegrationTest` 使用 `@Transactional`，用例结束回滚；`OrderSagaCompensationTest` 刻意不加事务，以便观察业务回滚后 Saga 轨迹仍存在。

### 6.3 架构测试

`ArchitectureBoundaryTest` 不启动容器，直接分析字节码，速度极快，适合作为每次构建的“护栏”。

---

## 7. 全部测试清单（8 个类，30 个用例）

| 测试类 | 用例数 | 关注点 |
| --- | --- | --- |
| `ArchitectureBoundaryTest` | 5 | 分层、技术中立、接口层、模拟服务、仓储接口 |
| `OrderTest` | 4 | 退款失败恢复、退款成功清理、支付记录、订单项修改限制 |
| `OrderPricingServiceTest` | 3 | 未达门槛不优惠、满减、最优策略 |
| `OrderStateMachineIntegrationTest` | 9 | 全生命周期、非法迁移、取消、退款成功/失败、参数校验 |
| `OrderCreatePayFlowIntegrationTest` | 1 | 创建到支付全链路（含幂等、Outbox、读模型、Saga） |
| `OrderQueryTest` | 2 | 列表分页、详情 |
| `StockReservationTest` | 5 | 确认、释放幂等、TTL 过期、确认后释放、非法参数 |
| `OrderSagaCompensationTest` | 1 | 持久化失败时释放预占并标记 `COMPENSATED` |

### 7.1 详细用例

**ArchitectureBoundaryTest**

- `domain_should_not_depend_on_outer_layers`
- `domain_should_stay_technology_agnostic`
- `interfaces_should_not_depend_on_infrastructure_or_mocks`
- `mocks_should_be_independent_services`
- `repositories_should_be_interfaces_in_domain_layer`

**OrderTest**

- `refundFailureShouldRestorePreviousStatus`
- `successfulRefundShouldClearPreviousStatus`
- `recordingPaymentShouldNotChangeOrderStatus`
- `onlyCreatedOrderCanModifyItems`

**OrderPricingServiceTest**

- `shouldNotDiscountBelowThreshold`
- `shouldApplyFullReduction`
- `shouldChooseBestPolicy`

**OrderStateMachineIntegrationTest**

- `shouldCompleteOrderLifecycle`
- `shouldRejectPayAfterOrderCancelled`
- `shouldCancelCreatedOrder`
- `shouldCompleteRefundSuccessfully`
- `shouldRestorePreviousStatusWhenRefundFails`
- `shouldRestoreDeliveredStatusWhenRefundFails`
- `shouldRejectInvalidTransitionWithoutChangingOrder`
- `shouldThrowWhenOrderDoesNotExist`
- `shouldRejectNullArguments`

**OrderCreatePayFlowIntegrationTest**

- `shouldCreateAndPayOrder`：一个用例内包含 9 组断言，覆盖原价/优惠/应付、创建幂等、支付状态、支付金额、详情与历史、ACL 调用次数、读模型同步、Saga 完成、Outbox 事件。

**OrderQueryTest**

- `shouldQueryOrderList`
- `shouldQueryOrderDetail`

**StockReservationTest**

- `shouldConfirmPendingReservation`
- `shouldReleaseOnlyOnce`
- `shouldExpireAfterTtl`
- `shouldRejectReleaseAfterConfirm`
- `shouldRejectInvalidArguments`

**OrderSagaCompensationTest**

- `shouldCompensateReservationWhenPersistFails`

---

## 8. 运行方式

### 8.1 全量测试

```bash
mvn test
```

### 8.2 只跑某个测试类

```bash
mvn test -Dtest=ArchitectureBoundaryTest
mvn test -Dtest=OrderCreatePayFlowIntegrationTest
mvn test -Dtest=OrderSagaCompensationTest
```

### 8.3 只跑某个方法

```bash
mvn test -Dtest=OrderSagaCompensationTest#shouldCompensateReservationWhenPersistFails
```

### 8.4 统计用例数

Surefire 报告位于 `target/surefire-reports/`。PowerShell 统计：

```powershell
$files = Get-ChildItem target\surefire-reports\TEST-*.xml
$total = 0; $failures = 0; $errors = 0
foreach ($f in $files) {
    [xml]$x = Get-Content $f.FullName
    $total += [int]$x.testsuite.tests
    $failures += [int]$x.testsuite.failures
    $errors += [int]$x.testsuite.errors
}
"suites=$($files.Count) tests=$total failures=$failures errors=$errors"
```

预期输出：

```text
suites=8 tests=30 failures=0 errors=0
```

---

## 9. 常见失败与处理

| 现象 | 可能原因 | 处理 |
| --- | --- | --- |
| ArchUnit 报 `was violated` | 新增代码越界依赖 | 按第 4 节读调用点，调整依赖方向 |
| 测试上下文启动失败，提示表已存在 | 多个测试共用同名 H2 库 | 给测试单独配置 `spring.datasource.url`，如 `jdbc:h2:mem:sagadb` |
| Saga 补偿测试查不到 Saga 记录 | 测试方法加了 `@Transactional`，业务回滚连带回滚了 Saga | 该测试不要加 `@Transactional`，Saga 使用 `REQUIRES_NEW` |
| 测试尝试连接 Kafka | 消费者或发送器未关闭 | 配置 `spring.kafka.listener.auto-startup=false`，或 Mock `KafkaEventPublisher` |
| `InvalidUseOfMatchersException` | Mockito 参数匹配器与原始值混用 | 全部参数改用 `eq(...)`、`anyString()` 等匹配器 |
| 测试之间互相影响 | 数据未清理 | 在 `@BeforeEach` 中清空相关表，或使用 `@Transactional` 回滚 |
| 端口占用 | 本地已启动 8080/9001/9002 | 停止旧进程，或 `--server.port=xxxx` 覆盖端口 |

---

## 10. 新增功能时的测试清单

新增一个命令用例（如“修改收货地址”）：

1. **聚合单元测试**：合法状态可改、非法状态抛 `IllegalStateException`、事件被登记。
2. **状态机测试**：如果涉及状态迁移，补充合法/非法边。
3. **HTTP 集成测试**：请求校验、成功响应、冲突响应、幂等行为。
4. **架构测试**：确认新代码没有越界；必要时新增规则。
5. **Saga 测试**：如果新增跨服务步骤，补成功路径与失败补偿路径。
6. **读模型测试**：如果新增事件，补投影更新与重复/乱序处理。

新增一个查询用例：

1. **查询服务测试**：分页边界、过滤条件、空结果。
2. **HTTP 集成测试**：参数绑定、响应字段、异常映射。
3. **读模型测试**：投影字段是否满足展示需求，是否需要回填历史数据。

新增一条 ArchUnit 规则：

1. 先确认当前代码满足规则，避免一提交就红。
2. 规则名与注释说明“为什么”。
3. 如果确实需要折衷，写清楚范围与原因，不要静默排除整个包。

---

## 11. 本篇小结

- ArchUnit 把架构约定变成构建期断言，越界即失败，避免文档与代码脱节。
- 五条规则分别守住：领域层独立性、技术中立、接口层职责、模拟服务边界、仓储依赖倒置。
- 测试分三层：单元测试验证业务规则，集成测试验证协作与持久化，架构测试验证依赖方向。
- 新增功能时按“聚合 → 状态机 → 接口 → 架构 → Saga → 读模型”的顺序补测试，避免只测 happy path。
- 当前 8 个测试类、30 个用例全部通过，是后续扩展的安全基线。

下一篇：[08-runbook-and-troubleshooting.md](./08-runbook-and-troubleshooting.md) 提供启动、配置、接口演练与排障手册。
