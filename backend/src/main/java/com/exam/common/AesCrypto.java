package com.exam.common;

import com.exam.config.AppKeyProvider;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM 加解密工具（身份证号等敏感字段存储加密）。
 * <p>用 GCM 认证加密（AEAD）+ 随机 12 字节 IV：相比旧版 ECB，相同明文不会产生相同密文，
 * 且带完整性校验（防篡改密文）。密文 = Base64( iv(12) + ciphertext )。
 * <p>密钥来源（P2-1）：显式注入 APP_CRYPTO_KEY（16/24/32 字节 → AES-128/192/256）则用之；
 * 未注入时回退 {@link com.exam.config.AppKeyProvider} 的本地持久化随机密钥（首次自动生成并落盘），
 * 不再使用内置固定演示密钥，消除规律性风险、保证跨重启可解密历史数据。生产仍由 ProductionGuard fail-fast。
 */
@Component
public class AesCrypto {

    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private final SecretKeySpec keySpec;
    private final boolean usingExplicitKey;
    private final SecureRandom random = new SecureRandom();

    public AesCrypto(AppKeyProvider keyProvider) {
        // 显式注入判定：保持与 AppKeyProvider 一致的「是否显式注入」语义
        String env = System.getenv("APP_CRYPTO_KEY");
        if (env != null && !env.isBlank()) {
            byte[] key = env.getBytes(StandardCharsets.UTF_8);
            // #21 安全：显式注入的密钥必须是合法 AES-128/192/256 长度（16/24/32 字节）；
            // 否则直接拒启，禁止「0 填充弱化短密钥」——短密钥会让加密形同虚设。
            if (key.length != 16 && key.length != 24 && key.length != 32) {
                throw new IllegalStateException(
                        "APP_CRYPTO_KEY 长度必须为 16/24/32 字节（对应 AES-128/192/256）；当前="
                                + key.length + " 字节。请提供强随机密钥（可用 32 字节）。");
            }
            this.keySpec = new SecretKeySpec(key, "AES");
            this.usingExplicitKey = true;
        } else {
            this.keySpec = new SecretKeySpec(keyProvider.getAesKey(), "AES");
            this.usingExplicitKey = false;
        }
    }

    /** 生产模式（未自定义密钥）下拒启，避免默认密钥加密真实数据。 */
    public void failFastIfDemoKey() {
        if (!usingExplicitKey) {
            throw new IllegalStateException(
                    "检测到未显式注入加密密钥：生产环境必须通过环境变量 APP_CRYPTO_KEY 注入强随机密钥，禁止使用本地演示/随机密钥。");
        }
    }

    public boolean isUsingDemoKey() {
        return !usingExplicitKey;
    }

    public String encrypt(String plain) {
        if (plain == null || plain.isBlank()) return null;
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("AES 加密失败", e);
        }
    }

    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isBlank()) return null;
        try {
            byte[] all = Base64.getDecoder().decode(cipherText);
            if (all.length < IV_BYTES + 1) return null;
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(all, 0, iv, 0, IV_BYTES);
            byte[] ct = new byte[all.length - IV_BYTES];
            System.arraycopy(all, IV_BYTES, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("AES 解密失败", e);
        }
    }

    /**
     * 安全解密：与 {@link #decrypt} 行为一致，但在密文损坏/被篡改等任何异常时
     * 返回 {@code null} 而非抛出，避免单条损坏数据导致整批流程 500。
     * 适用于「解密失败可降级为无值」的场景（如默认密码推导）。
     */
    public String decryptSafe(String cipherText) {
        if (cipherText == null || cipherText.isBlank()) return null;
        try {
            return decrypt(cipherText);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(AesCrypto.class)
                    .warn("[SEC][降级] 单条密文解密失败，已降级为 null（可能损坏或被篡改）: {}", e.getMessage());
            return null;
        }
    }
}
