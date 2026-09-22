package com.exam.service;

/**
 * 会话失效状态存储抽象（可扩展性）。
 * <p>维护三类「集中失效」状态，使 JWT 会话具备主动注销能力：
 * <ul>
 *   <li><b>jti 登出黑名单</b>：登出的单个令牌（jti -&gt; 过期毫秒）</li>
 *   <li><b>账号 token 版本</b>：改密/重置时自增，令该账号全部旧令牌失效（uid -&gt; 版本号）</li>
 *   <li><b>登录失败计数</b>：账号/登录串 -&gt; [失败次数, 最近失败时间戳]，用于失败锁定</li>
 * </ul>
 * <p>当前内置 {@link JpaRevocationStore} 为 JPA 持久化实现（重启保留、可多实例共享）；
 * 如需更高性能的集群方案，可实现本接口并注入 Redis 实现（如 RedisRevocationStore），无需改动业务调用方。
 */
public interface RevocationStore {

    // ---------- jti 登出黑名单 ----------

    /** 将令牌 jti 加入黑名单，exp 为该令牌过期毫秒（用于清理）。 */
    void blacklist(String jti, long exp);

    /** 判断令牌 jti 是否已登出。 */
    boolean isBlacklisted(String jti);

    // ---------- 账号 token 版本 ----------

    /** 读取账号 token 版本（改密/重置失效用）；无记录返回 null。 */
    Long tokenVersion(Long accountId);

    /** 将账号 token 版本自增 n（令其所有旧令牌失效）。 */
    void bumpTokenVersion(Long accountId, long n);

    // ---------- 登录失败计数（失败锁定） ----------

    /** 读取登录失败计数 [次数, 最近失败时间戳]；无记录返回 [0,0]。 */
    long[] failureCount(String key);

    /** 记录一次登录失败（次数+1，时间戳=now）。 */
    void recordFailure(String key);

    /** 登录成功后清除失败计数。 */
    void clearFailure(String key);
}
