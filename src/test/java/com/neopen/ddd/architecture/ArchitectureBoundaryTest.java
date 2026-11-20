package com.neopen.ddd.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 架构边界测试：用可执行的规则代替口头约定，防止分层与限界上下文被逐步侵蚀。
 *
 * <p>为什么需要架构测试：DDD 的边界一旦只写在文档里，就会在“赶需求”时被悄悄破坏。
 * ArchUnit 把边界变成构建期断言，任何越界依赖都会让 {@code mvn test} 失败。</p>
 *
 * <p>本测试约束：</p>
 * <ul>
 *   <li>领域层不依赖应用层、基础设施、接口层与模拟服务；</li>
 *   <li>领域模型与服务保持技术中立，不依赖 Spring Web、Kafka、Feign 等外部技术；</li>
 *   <li>接口层只做协议适配，不直接访问基础设施与模拟服务；</li>
 *   <li>模拟服务作为独立进程边界，不反向依赖订单服务的应用层与领域层。</li>
 * </ul>
 *
 * <p>注意：状态机适配代码位于 {@code domain.statemachine}，本质是技术实现，
 * 因此不在“领域模型技术中立”规则的约束范围内。</p>
 */
class ArchitectureBoundaryTest {

    /** 导入主代码，排除测试类，避免测试夹具干扰规则判断。 */
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.neopen.ddd");

    /**
     * 领域层不得依赖外层：应用层、基础设施、接口层与模拟服务。
     */
    @Test
    void domain_should_not_depend_on_outer_layers() {
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
    }

    /**
     * 领域模型与领域服务不得依赖 Web、Kafka、Feign 等技术设施。
     * <p>仓储接口在本示例中继承 Spring Data 的 {@code JpaRepository}，
     * 属于有意的工程折衷，因此不纳入本条规则。</p>
     */
    @Test
    void domain_should_stay_technology_agnostic() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage(
                        "..ddd.domain.model..",
                        "..ddd.domain.service..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.web..",
                        "org.springframework.kafka..",
                        "org.springframework.cloud..");
        rule.check(CLASSES);
    }

    /**
     * 接口层只允许依赖应用层与领域契约，不得绕过应用服务直接访问基础设施或模拟服务。
     */
    @Test
    void interfaces_should_not_depend_on_infrastructure_or_mocks() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..ddd.interfaces..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..ddd.infrastructure..",
                        "..ddd.mocks..");
        rule.check(CLASSES);
    }

    /**
     * 模拟服务模拟独立部署的微服务，不得反向依赖订单服务的应用层、领域层与接口层。
     */
    @Test
    void mocks_should_be_independent_services() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..ddd.mocks..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..ddd.application..",
                        "..ddd.domain..",
                        "..ddd.interfaces..");
        rule.check(CLASSES);
    }

    /**
     * 仓储接口统一放在领域层，且必须是接口类型，体现“领域定义端口、基础设施实现适配”的依赖倒置。
     */
    @Test
    void repositories_should_be_interfaces_in_domain_layer() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Repository")
                .and().resideInAPackage("..ddd.domain..")
                .should().beInterfaces();
        rule.check(CLASSES);
    }
}
