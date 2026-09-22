package com.exam.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内（单节点）会话失效状态内存实现（兜底 / 单元测试参考）。
 * <p>说明：生产默认已切换为 {@link JpaRevocationStore}（重启保留、可多实例共享）。
 * 本类保留作为可选实现与对比基准；不再是 Spring 自动注册的 Bean，避免与 JPA 实现产生注入歧义。
 * 如需启用，需在配置中显式声明为 Bean（如 {@code @Bean} 或指定 Profile）。
 */
public class InMemoryRevocationStore implements RevocationStore {

    /** 登出令牌黑名单：jti -> 过期毫秒。 */
    private final Map<String, Long> denylist = new ConcurrentHashMap<>();

    /** 账号 token 版本：accountId -> 版本号。 */
    private final Map<Long, Long> tokenVersions = new ConcurrentHashMap<>();

    /** 失败锁定计数：key -> [失败次数, 最近失败时间戳]。 */
    private final Map<String, long[]> failCounter = new ConcurrentHashMap<>();

    @Override
    public void blacklist(String jti, long exp) {
        denylist.put(jti, exp);
        purgeExpiredDenylist();
    }

    @Override
    public boolean isBlacklisted(String jti) {
        return denylist.containsKey(jti);
    }

    @Override
    public Long tokenVersion(Long accountId) {
        return tokenVersions.get(accountId);
    }

    @Override
    public void bumpTokenVersion(Long accountId, long n) {
        tokenVersions.merge(accountId, n, Long::sum);
    }

    @Override
    public long[] failureCount(String key) {
        return failCounter.getOrDefault(key, new long[]{0, 0L});
    }

    @Override
    public void recordFailure(String key) {
        long[] c = failCounter.computeIfAbsent(key, k -> new long[]{0, 0L});
        // 原子性：在并发下取最新并自增
        synchronized (c) {
            c[0] = c[0] + 1;
            c[1] = System.currentTimeMillis();
        }
    }

    @Override
    public void clearFailure(String key) {
        failCounter.remove(key);
    }

    /** 周期清理已过期黑名单条目（内存容量有界）。 */
    private void purgeExpiredDenylist() {
        long now = System.currentTimeMillis();
        denylist.entrySet().removeIf(e -> e.getValue() < now);
    }
}
