package com.exam.config;

import com.exam.common.AesCrypto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import java.util.Arrays;

/**
 * 生产环境启动门禁（fail-fast）。
 * <p>当激活 profile 包含 {@code prod} 时，在 Spring 上下文刷新完成后强制校验上线硬约束，
 * 任何一项不满足即抛异常拒绝启动，杜绝「用默认配置裸奔上线」：
 * <ul>
 *   <li>JWT 签名密钥与 AES 加密密钥必须是显式注入的外部值（禁止默认值）；</li>
 *   <li>数据播种必须显式关闭（{@code APP_SEED_ENABLED=false}），避免演示数据污染真实库；</li>
 *   <li>数据源必须是 PostgreSQL（{@code org.postgresql.Driver}），禁止生产用 H2 内存库。</li>
 * </ul>
 */
@Configuration
public class ProductionGuard {

    private static final Logger log = LoggerFactory.getLogger(ProductionGuard.class);

    private final AesCrypto aesCrypto;
    private final AppKeyProvider keyProvider;
    private final String activeProfiles;
    private final String datasourceDriver;
    private final boolean seedEnabled;

    public ProductionGuard(AesCrypto aesCrypto,
                           AppKeyProvider keyProvider,
                           @Value("${spring.profiles.active:}") String activeProfiles,
                           @Value("${spring.datasource.driver-class-name:}") String datasourceDriver,
                           @Value("${app.seed.enabled:true}") boolean seedEnabled) {
        this.aesCrypto = aesCrypto;
        this.keyProvider = keyProvider;
        this.activeProfiles = activeProfiles;
        this.datasourceDriver = datasourceDriver;
        this.seedEnabled = seedEnabled;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void guard() {
        boolean prod = Arrays.stream(activeProfiles.split(","))
                .map(String::trim)
                .anyMatch(p -> "prod".equalsIgnoreCase(p) || "pg".equalsIgnoreCase(p));
        if (!prod) {
            // #2 渐进收紧（P2-1 修订）：非 prod/pg 时，若任一密钥未显式注入（JWT/AES 用了本地持久化随机密钥），
            // 输出强提示（保留默认单机/启动器可用，但明确告知这是本地随机密钥，公网部署必须注入 APP_* 密钥）。
            if (keyProvider.hasAnyNonExplicit()) {
                log.warn("⚠ 当前非 prod 配置存在未显式注入的密钥（JWT={}, AES={}），已使用本地持久化随机密钥文件。"
                                + "仅限本机/H2 演示。若将该部署暴露到公网，必须通过环境变量注入 "
                                + "APP_SECURITY_TOKEN_SECRET 与 APP_CRYPTO_KEY，否则密钥可被读取/伪造令牌/解密身份证。",
                        keyProvider.isJwtExplicit() ? "已注入" : "本地随机", keyProvider.isAesExplicit() ? "已注入" : "本地随机");
            }
            return; // 非 prod/pg：不阻塞开发/测试
        }

        // ① AES 加密密钥必须显式注入（非本地随机）
        aesCrypto.failFastIfDemoKey();

        // ② JWT 签名密钥必须显式注入（非本地随机）
        if (!keyProvider.isJwtExplicit()) {
            throw new IllegalStateException(
                    "生产启动门禁失败：app.security.token-secret 未显式注入，必须通过环境变量 APP_SECURITY_TOKEN_SECRET 提供强随机密钥。");
        }

        // ③ 数据源必须是 PostgreSQL（禁止生产用 H2 内存库）
        if (datasourceDriver == null || !datasourceDriver.contains("postgresql")) {
            throw new IllegalStateException(
                    "生产启动门禁失败：数据源驱动必须为 PostgreSQL（org.postgresql.Driver），当前=" + datasourceDriver
                            + "；禁止生产使用 H2 内存库（重启即丢数据）。");
        }

        // ④ 数据播种必须显式关闭
        if (seedEnabled) {
            throw new IllegalStateException(
                    "生产启动门禁失败：app.seed.enabled 必须为 false（APP_SEED_ENABLED=false），避免演示数据污染真实库。");
        }

        log.info("生产启动门禁通过：密钥已显式注入、数据源为 PostgreSQL、数据播种已关闭。");
    }
}
