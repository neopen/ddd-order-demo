# DDD订单领域模型示例项目

## 1. 领域驱动设计(DDD)基础概念

领域驱动设计(Domain-Driven Design, DDD)是一种软件开发方法论，它强调将软件设计与业务领域紧密结合，通过构建丰富的领域模型来解决复杂的业务问题。以下是DDD的核心概念：

### 1.1 核心概念

- **领域(Domain)**: 业务所涉及的范围和边界
- **子域(Subdomain)**: 领域内可拆分的更小业务单元
  - 核心域: 业务的核心竞争力
  - 支撑域: 必要但非核心的业务能力
  - 通用域: 可标准化、复用的业务功能
- **限界上下文(Bounded Context)**: 领域模型的边界，在边界内术语和概念具有明确且一致的含义

### 1.2 领域模型构成

- **实体(Entity)**: 具有唯一标识且其状态会随时间变化的对象
- **值对象(Value Object)**: 没有唯一标识、不可变的对象，用于描述事物的某种特性
- **聚合(Aggregate)**: 一组相关实体和值对象的集合，有一个根实体(聚合根)
- **领域服务(Domain Service)**: 封装不属于特定实体或值对象的领域逻辑
- **领域事件(Domain Event)**: 领域中发生的重要事件
- **仓储(Repository)**: 提供对聚合根的持久化和检索能力
- **工厂(Factory)**: 负责创建复杂的领域对象

## 2. 项目结构

本项目采用典型的DDD分层架构，将业务逻辑、领域模型与技术实现清晰分离。整体结构如下：

```
src/main/java/org/example/order/
├── OrderApplication.java      # 应用入口
├── application/               # 应用层
│   ├── dto/                   # 数据传输对象
│   └── service/               # 应用服务
├── domain/                    # 领域层（核心）
│   ├── event/                 # 领域事件
│   ├── model/                 # 领域模型（实体、值对象、聚合）
│   ├── repository/            # 领域仓储接口
│   ├── service/               # 领域服务
│   └── statemachine/          # 状态机（管理聚合状态转换）
├── infrastructure/            # 基础设施层
│   ├── config/                # 配置类
│   ├── exception/             # 异常处理
│   ├── integration/           # 外部系统集成
│   ├── messaging/             # 消息发布/订阅
│   └── persistence/           # 仓储实现
└── interfaces/                # 接口层
    ├── app/                   # 应用接口
    └── web/                   # Web接口
```

### 2.1 分层职责

- **领域层(Domain Layer)**: 包含核心业务逻辑和领域规则，是系统的核心
- **应用层(Application Layer)**: 协调领域对象完成用例，不包含业务规则
- **基础设施层(Infrastructure Layer)**: 提供技术支持，实现领域层的接口
- **接口层(Interfaces Layer)**: 处理外部请求，转换为应用层的调用

## 3. 订单领域模型设计

### 3.1 聚合根

**Order (订单)**

```java
@Entity
@Table(name = "tab_order")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String orderNum;
    private Long customerId;
    @Enumerated(EnumType.STRING)
    private OrderStatusEnum status;
    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductItem> productItems = new ArrayList<>();
    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private OrderPayment orderPayment;
    // 领域方法...
}
```

Order作为订单领域的聚合根，封装了订单的核心业务规则和状态转换逻辑，确保订单数据的一致性和完整性。

### 3.2 实体

**ProductItem (订单项)**

```java
@Entity
@Table(name = "tab_order_items")
public class ProductItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "product_id", nullable = false)
    private Long productId;
    @Column(name = "product_name", nullable = false)
    private String productName;
    @Column(name = "quantity", nullable = false)
    private int quantity;
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;
    @Column(name = "subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;
    @ManyToOne(fetch = FetchType.LAZY)
    private Order order;
    // 领域方法...
}
```

**OrderPayment (订单支付信息)**

```java
@Entity
@Table(name = "tab_order_payment")
public class OrderPayment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String paymentId;
    private String transactionId;
    private String status;
    private LocalDateTime paidAt;
    private Double amount;
    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private Order order;
    // 领域方法...
}
```

### 3.3 值对象

**Money (金额)**

```java
@Getter
public class Money {
    private BigDecimal amount;
    private String currency;
    
    public Money(BigDecimal amount, String currency) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("金额不能为负");
        }
        this.amount = amount;
        this.currency = currency;
    }
    
    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("货币不一致");
        }
        return new Money(this.amount.add(other.amount), this.currency);
    }
    // equals, hashCode 基于 amount 和 currency
}
```

Money作为值对象，是不可变的，且通过amount和currency属性进行等价性比较，而不是通过唯一标识。

### 3.4 领域事件

项目定义了多个领域事件来捕获订单生命周期中的重要状态变化：

- `OrderCreatedEvent`：订单创建事件
- `OrderPaidEvent`：订单支付事件
- `OrderShippedEvent`：订单发货事件
- `OrderCancelledEvent`：订单取消事件

所有领域事件都实现了`DomainEvent`接口，通过Kafka消息队列发布到系统中。

### 3.5 订单状态流转

订单的状态流转通过`OrderStatusEnum`和`OrderEventEnum`定义：

```java
public enum OrderStatusEnum {
    // 待支付
    WAIT_PAYMENT,
    // 待发货
    WAIT_DELIVER,
    // 待收货
    WAIT_RECEIVE,
    // 完成
    FINISH
}

public enum OrderEventEnum {
    //支付
    PAYED,
    //发货
    DELIVERY,
    //收货
    RECEIVED,
    //订单取消
    CANCELLED
}
```

状态流转图：
```
WAIT_PAYMENT →(PAYED)→ WAIT_DELIVER →(DELIVERY)→ WAIT_RECEIVE →(RECEIVED)→ FINISH
         ↓(CANCELLED)                                  ↓(CANCELLED)
         └───────────────────────────────────────────────┘
```

## 4. 核心业务流程

### 4.1 订单创建流程

```java
@Transactional
public Long createOrder(OrderCreateCommand command) {
    // 1. 调用库存服务预留库存
    if (!inventoryAcl.reserveStock(command.getProductId(), command.getQuantity())) {
        throw new RuntimeException("库存不足或服务不可用");
    }
    
    // 2. 通过工厂方法创建订单聚合根
    Order order = Order.create(command.getCustomerId(), Arrays.asList(
            ProductItem.create(command.getProductId(), command.getProductName(), command.getQuantity(), 
                              new Money(new BigDecimal(command.getPrice()), "CNY"))
            ));
    
    // 3. 持久化订单
    orderRepository.save(order);
    
    // 4. 发布领域事件
    order.getEvents().forEach(eventPublisher::publish);
    order.clearEvents();
    
    return order.getId();
}
```

### 4.2 订单支付流程

```java
@Transactional
public String payOrder(Long orderId) {
    // 1. 查找订单
    Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new RuntimeException("订单不存在"));
    
    // 2. 调用支付服务处理支付
    boolean paymentSuccess = paymentServiceAcl.processPayment(order);
    if (!paymentSuccess) {
        throw new RuntimeException("支付失败，请稍后重试");
    }
    
    // 3. 设置支付信息
    OrderPayment paymentInfo = new OrderPayment(
            "PAY-" + orderId,
            "TXN-" + orderId,
            "COMPLETED", order
    );
    
    order.setOrderPayment(paymentInfo);
    
    // 4. 触发状态机转换
    if (!sendEvent(OrderEventEnum.PAYED, order)) {
        throw new RuntimeException("支付失败, 状态异常，订单号：" + order.getOrderNum());
    }
    
    // 5. 持久化并发布事件
    orderRepository.save(order);
    order.getEvents().forEach(eventPublisher::publish);
    order.clearEvents();
    
    return "支付成功，订单号：" + order.getOrderNum();
}
```

## 5. 技术栈与实现细节

### 5.1 主要技术栈

- **Spring Boot 3.x**: 应用框架
- **Spring Data JPA**: 数据访问层
- **Spring StateMachine**: 状态管理
- **Spring Kafka**: 消息队列
- **OpenFeign**: 服务间调用
- **Resilience4j**: 熔断器
- **H2 Database**: 内存数据库（示例用）
- **Lombok**: 减少样板代码
- **FastJSON2**: JSON序列化/反序列化

### 5.2 跨域集成设计

项目通过防腐层(ACL)模式实现与外部系统的集成，避免外部系统的变化影响核心领域模型：

- **PaymentServiceAcl**: 支付服务集成
- **InventoryServiceAcl**: 库存服务集成

每个防腐层内部封装了与外部系统交互的细节，包括请求/响应转换、异常处理和容错机制。

### 5.3 状态管理实现

项目使用Spring StateMachine实现订单状态的流转管理，通过状态机配置和监听器来处理状态转换的逻辑和副作用。这种方式将状态转换逻辑与业务逻辑分离，提高了代码的可维护性。

## 6. 总结

本项目是一个完整的DDD实践示例，通过订单领域展示了DDD的核心概念和设计模式。项目的主要特点包括：

1. **清晰的分层架构**: 严格按照DDD的分层原则组织代码
2. **丰富的领域模型**: 包含完整的聚合、实体、值对象和领域事件
3. **封装的业务规则**: 核心业务逻辑封装在领域对象中
4. **事件驱动设计**: 通过领域事件实现系统解耦
5. **状态机管理**: 使用状态机管理复杂的状态转换
6. **防腐层设计**: 有效隔离外部系统的影响

通过这样的设计，系统不仅能够灵活应对业务变化，还保持了代码的高内聚低耦合特性，提高了系统的可维护性和可扩展性。