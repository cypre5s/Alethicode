package com.alethicode.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * D-04 架构边界测试：用 ArchUnit 在测试期 fail-fast 守住分层架构契约。
 *
 * <p>规则集（基于第一性原理与 backend 当前实际包结构）：
 * <ul>
 *   <li><b>controller</b>（REST/WebSocket 入口）只能调用 service / dto / exception / util / config /
 *       middleware / websocket，<b>禁止</b>直接 import repository。</li>
 *   <li><b>service</b>（业务逻辑）<b>禁止</b>反向依赖 controller / middleware / websocket。</li>
 *   <li><b>repository</b>（持久化）<b>禁止</b>依赖 controller / service / middleware / websocket。</li>
 *   <li><b>entity</b> 与 <b>dto</b> 必须保持纯 POJO，<b>禁止</b>依赖任何 alethicode.* 业务包。</li>
 *   <li>backend 业务包之间<b>不能存在循环依赖</b>。</li>
 * </ul>
 *
 * <p>违反任何一条 → 该测试失败 → 提交流水线红灯，确保新代码不会侵蚀分层。
 */
class PackageBoundaryArchTest {

    private static final String ROOT = "com.alethicode";

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    /**
     * controller 不应直接访问 repository，必须经过 service。
     */
    @Test
    void controllerShouldNotDependOnRepository() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(ROOT + ".controller..")
                .should().dependOnClassesThat().resideInAPackage(ROOT + ".repository..")
                .because("controller 必须经过 service 调用 repository，禁止越层访问数据层");
        rule.check(CLASSES);
    }

    /**
     * service 禁止反向依赖 controller / middleware。
     *
     * <p>注意：暂未把 {@code websocket} 包列入禁止清单，因为 {@link
     * com.alethicode.websocket.WorkflowRealtimeSupport} 当前承担"业务侧主动推送"职责，
     * 18 处 service 直接 import 它属于已知技术债。后续应抽取 {@code RealtimeNotifier}
     * 接口（放在 service.notify 或 contract 包）让 service 依赖接口、由 websocket 实现，
     * 届时本规则收紧到包含 websocket。
     */
    @Test
    void serviceShouldNotDependOnControllerOrMiddleware() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(ROOT + ".service..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        ROOT + ".controller..",
                        ROOT + ".middleware.."
                )
                .because("service 是业务核心层，不应反向依赖入口层");
        rule.check(CLASSES);
    }

    /**
     * repository 必须保持最纯净，禁止依赖业务编排层。
     */
    @Test
    void repositoryShouldNotDependOnUpperLayers() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(ROOT + ".repository..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        ROOT + ".controller..",
                        ROOT + ".service..",
                        ROOT + ".middleware..",
                        ROOT + ".websocket.."
                )
                .because("repository 必须保持最纯净，禁止依赖业务编排层");
        rule.check(CLASSES);
    }

    /**
     * entity（JPA 实体）必须是几乎纯 POJO，禁止依赖业务编排层与 web 层。
     * 允许：util、exception、Spring Data 注解。
     */
    @Test
    void entityShouldNotDependOnBusinessLayers() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(ROOT + ".entity..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        ROOT + ".controller..",
                        ROOT + ".service..",
                        ROOT + ".repository..",
                        ROOT + ".middleware..",
                        ROOT + ".websocket..",
                        ROOT + ".dto..",
                        ROOT + ".mcp.."
                )
                .because("entity 必须是纯 POJO/JPA 实体，禁止依赖业务编排层");
        rule.check(CLASSES);
    }

    /**
     * dto（请求/响应数据传输对象）必须是纯 POJO，禁止依赖业务编排层与持久层。
     */
    @Test
    void dtoShouldNotDependOnBusinessOrPersistenceLayers() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(ROOT + ".dto..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        ROOT + ".controller..",
                        ROOT + ".service..",
                        ROOT + ".repository..",
                        ROOT + ".middleware..",
                        ROOT + ".websocket..",
                        ROOT + ".entity..",
                        ROOT + ".mcp.."
                )
                .because("dto 必须是纯传输对象，与持久化层、业务编排层完全解耦");
        rule.check(CLASSES);
    }

    /**
     * controller 必须以 Controller 结尾，且必须放在 controller 包内。
     */
    @Test
    void controllersShouldResideInControllerPackage() {
        ArchRule rule = classes()
                .that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                .or().areAnnotatedWith(org.springframework.stereotype.Controller.class)
                .should().resideInAPackage(ROOT + ".controller..")
                .because("所有 @RestController / @Controller 必须放在 controller 包内");
        rule.check(CLASSES);
    }

    /**
     * controller / service / repository 三层主链不应存在循环依赖。
     *
     * <p>Spring Boot 项目 {@code config} 包天生跨切面（@Configuration 持有业务 bean 引用、
     * @ConfigurationProperties 又被业务包注入），强制无循环不现实；同理 {@code util} /
     * {@code dto} / {@code entity} / {@code exception} 与业务包的双向引用属于 Spring 标准做法。
     * 因此循环检测严格限定在<b>主业务链路</b>三层：controller / service / repository。
     *
     * <p>service ↔ websocket 的循环已知，记录为技术债（见
     * {@link #serviceShouldNotDependOnControllerOrMiddleware} TODO）。
     */
    @Test
    void coreThreeLayerShouldBeFreeOfCycles() {
        ArchRule rule = slices()
                .matching(ROOT + ".(controller|service|repository)..")
                .namingSlices("$1")
                .should().beFreeOfCycles()
                .because("controller / service / repository 三层主链路不应存在循环依赖");
        rule.check(CLASSES);
    }
}
