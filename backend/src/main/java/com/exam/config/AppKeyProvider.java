package com.exam.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Properties;

/**
 * 应用密钥提供器（P2-1：消除固定默认密钥的规律性风险）。
 * <p>统一管理 JWT 签名密钥与 AES 加密密钥的取值：
 * <ul>
 *   <li>若显式注入（环境变量 APP_SECURITY_TOKEN_SECRET / APP_CRYPTO_KEY），一律使用注入值，绝不回退；</li>
 *   <li>若未注入（开发/H2 本地演示）：首次启动用 {@link SecureRandom} 生成强随机密钥并持久化到本地密钥文件
 *        （默认 {@code ./data/app-secrets.properties}，可用 APP_SECRET_FILE 覆盖），之后每次启动复用同一份，
 *        保证 JWT 签名稳定、且 AES 加密的历史身份证数据跨重启可解密；不再使用内置固定演示密钥。</li>
 * </ul>
 * <p>生产 profile 仍由 {@link ProductionGuard} 强制 fail-fast（必须显式注入），本类不做前端门禁的绕过。
 */
@Component
public class AppKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(AppKeyProvider.class);

    public static final String PROP_JWT = "jwt.secret";
    public static final String PROP_AES = "aes.key";

    private final boolean jwtExplicit;   // JWT 是否显式注入
    private final boolean aesExplicit;   // AES 是否显式注入
    private final String jwtSecret;
    private final byte[] aesKey;

    public AppKeyProvider() {
        String envJwt = System.getenv("APP_SECURITY_TOKEN_SECRET");
        String envAes = System.getenv("APP_CRYPTO_KEY");

        boolean jwtInjected = envJwt != null && !envJwt.isBlank();
        boolean aesInjected = envAes != null && !envAes.isBlank();

        if (jwtInjected && aesInjected) {
            this.jwtExplicit = true;
            this.aesExplicit = true;
            this.jwtSecret = envJwt.trim();
            this.aesKey = normalizeAes(envAes);
            log.info("[APP-KEY] JWT 与 AES 密钥均由环境变量显式注入，不使用本地密钥文件。");
            return;
        }

        // 至少一方未注入：读取/初始化持久化密钥文件，未注入方取随机持久化密钥（已注入方仍用注入值）
        AppSecrets secrets = loadOrInitSecrets();
        this.jwtExplicit = jwtInjected;
        this.aesExplicit = aesInjected;
        this.jwtSecret = jwtInjected ? envJwt.trim() : secrets.jwt;
        this.aesKey = aesInjected ? normalizeAes(envAes) : secrets.aes;
        log.info("[APP-KEY] JWT={}，AES={}；未注入方已使用本地持久化随机密钥文件（首次自动生成）。",
                jwtInjected ? "显式注入" : "本地随机持久化", aesInjected ? "显式注入" : "本地随机持久化");
    }

    public String getJwtSecret() {
        return jwtSecret;
    }

    public byte[] getAesKey() {
        return aesKey;
    }

    /** JWT 是否显式注入（供 ProductionGuard 判定）。 */
    public boolean isJwtExplicit() {
        return jwtExplicit;
    }

    /** AES 是否显式注入（供 ProductionGuard 判定）。 */
    public boolean isAesExplicit() {
        return aesExplicit;
    }

    /** 是否仍有任一密钥未显式注入（非生产提示用）。 */
    public boolean hasAnyNonExplicit() {
        return !jwtExplicit || !aesExplicit;
    }

    private byte[] normalizeAes(String raw) {
        byte[] key = raw.getBytes(StandardCharsets.UTF_8);
        if (key.length != 16 && key.length != 24 && key.length != 32) {
            throw new IllegalStateException(
                    "APP_CRYPTO_KEY 长度必须为 16/24/32 字节（AES-128/192/256），当前=" + key.length + " 字节。");
        }
        return key;
    }

    private AppSecrets loadOrInitSecrets() {
        String file = System.getenv("APP_SECRET_FILE");
        Path path = Paths.get(file == null || file.isBlank() ? "./data/app-secrets.properties" : file);
        Properties props = new Properties();
        if (Files.exists(path)) {
            try (var in = Files.newInputStream(path)) {
                props.load(in);
            } catch (IOException e) {
                log.warn("[APP-KEY] 读取密钥文件失败，将重新生成: {}", e.getMessage());
            }
        }
        String jwt = props.getProperty(PROP_JWT);
        String aesB64 = props.getProperty(PROP_AES);
        if (jwt == null || jwt.isBlank()) {
            jwt = randomBase64(32); // 32 字节 → 44 字符
        }
        byte[] aes;
        if (aesB64 == null || aesB64.isBlank()) {
            aes = new byte[32];
            new SecureRandom().nextBytes(aes);
        } else {
            try {
                aes = Base64.getDecoder().decode(aesB64);
            } catch (IllegalArgumentException e) {
                aes = new byte[32];
                new SecureRandom().nextBytes(aes);
            }
        }
        // 只要初始化/变化即写回，保证持久化
        Properties toSave = new Properties();
        toSave.setProperty(PROP_JWT, jwt);
        toSave.setProperty(PROP_AES, Base64.getEncoder().encodeToString(aes));
        try {
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            try (var out = Files.newOutputStream(path)) {
                toSave.store(out, "auto-generated app secrets (P2-1); keep private, do not commit");
            }
        } catch (IOException e) {
            // 写失败不影响本次运行，仅记录（密钥本次有效，重启后会重新生成导致旧 AES 密文不可解，属降级）
            log.warn("[APP-KEY] 无法持久化密钥文件 {}（{}）；本次会话密钥仅内存有效。", path, e.getMessage());
        }
        return new AppSecrets(jwt, aes);
    }

    private String randomBase64(int bytes) {
        byte[] b = new byte[bytes];
        new SecureRandom().nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }

    /** 从密钥文件解析出的 JWT 与 AES。 */
    private record AppSecrets(String jwt, byte[] aes) {
    }
}
