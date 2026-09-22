package com.exam.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 无第三方依赖的 JWT（HS256）签发与校验。
 * <p>Payload：{"uid": 账号id, "ver": 账号token版本, "jti": 令牌唯一id, "exp": 过期毫秒时间戳}。
 * <p>无状态验签：签名合法且未过期即通过；配合 {@link com.exam.service.AuthService} 的
 * <ul>
 *   <li>ver（账号级 token 版本，改密/重置密码时自增，令该账号所有旧 token 失效）</li>
 *   <li>jti 黑名单（登出时加入，令单个 token 失效）</li>
 * </ul>
 * 实现集中失效。
 * <p>默认密钥仅用于本地/H2 演示，上线必须通过环境变量/配置中心显式注入 app.security.token-secret。
 */
@Component
public class JwtUtil {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static final String HEADER = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");

    /** 解析 JWT payload JSON 用（无状态、线程安全，单例复用）。 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final byte[] secret;
    private final long ttlMillis;

    public JwtUtil(AppKeyProvider keyProvider,
                   @Value("${app.security.token-secret:}") String cfgSecret,
                   @Value("${app.security.token-ttl-minutes:480}") long ttlMinutes) {
        // P2-1：优先使用配置/环境显式注入的密钥；未注入时回退 AppKeyProvider 的本地持久化随机密钥
        //（消除固定默认演示密钥的规律性风险，同时保证跨重启决策稳定）。
        String effective = (cfgSecret == null || cfgSecret.isBlank())
                ? keyProvider.getJwtSecret()
                : cfgSecret.trim();
        this.secret = effective.getBytes(StandardCharsets.UTF_8);
        this.ttlMillis = ttlMinutes * 60_000L;
    }

    private static String b64(String s) {
        return B64.encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    /** 生成 JWT。 */
    public String sign(long uid, long ver, String jti) {
        long exp = System.currentTimeMillis() + ttlMillis;
        String payload = b64("{\"uid\":" + uid + ",\"ver\":" + ver + ",\"jti\":\"" + jti + "\",\"exp\":" + exp + "}");
        String data = HEADER + "." + payload;
        return data + "." + hmac(data);
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return B64.encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("JWT 签名失败", e);
        }
    }

    /** 校验并解析；签名非法、结构错误或已过期返回 null。 */
    public Payload verify(String token) {
        if (token == null) return null;
        String[] parts = token.split("\\.");
        if (parts.length != 3) return null;
        String data = parts[0] + "." + parts[1];
        String expected = hmac(data);
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8))) {
            return null; // 签名不匹配
        }
        String json;
        try {
            json = new String(B64D.decode(parts[1]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
        JsonNode node;
        try {
            node = OBJECT_MAPPER.readTree(json);
        } catch (Exception e) {
            return null; // payload 非合法 JSON
        }
        if (node == null || !node.isObject()) {
            return null;
        }
        long uid = node.path("uid").asLong(-1);
        long ver = node.path("ver").asLong(-1);
        long exp = node.path("exp").asLong(-1);
        String jti = node.path("jti").asText(null);
        if (uid < 0 || ver < 0 || exp < 0 || jti == null || jti.isBlank() || exp < System.currentTimeMillis()) {
            return null; // 非法字段或已过期
        }
        Payload p = new Payload();
        p.uid = uid;
        p.ver = ver;
        p.jti = jti;
        p.exp = exp;
        return p;
    }

    /** 从 payload 取 jti（登出用）。 */
    public String parseJti(String token) {
        Payload p = verify(token);
        return p == null ? null : p.jti;
    }

    /** JWT payload 载体。 */
    public static class Payload {
        public long uid;
        public long ver;
        public String jti;
        public long exp;
    }
}
