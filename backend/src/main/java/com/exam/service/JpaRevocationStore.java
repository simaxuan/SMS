package com.exam.service;

import com.exam.entity.RevocationEntry;
import com.exam.repository.RevocationEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 JPA 的会话失效状态持久化实现（取代进程内内存实现）。
 * <p>解决的问题：原 {@link InMemoryRevocationStore} 重启即丢、无法多实例共享——已登出 / 已改密的令牌在重启后重新生效。
 * <p>持久化范围：仅持久化真正需跨重启 / 跨实例的部分——
 * <ul>
 *   <li><b>登出黑名单(DENY)</b>：jti -&gt; 过期毫秒；</li>
 *   <li><b>账号 token 版本(TV)</b>：改密 / 重置后自增，令该账号全部旧令牌失效。</li>
 * </ul>
 * 登录失败锁定计数(failureCount) 带瞬态语义（固定 5 次 / 15 分钟窗），重启清零可接受且需原子自增，故保留进程内。
 * <p>性能：黑名单与版本校验为按 (kind, key_str) 唯一查找，开销极低，适用于校园系统低 QPS 场景。
 */
@Service
public class JpaRevocationStore implements RevocationStore {

    private final RevocationEntryRepository repo;

    /** 失败锁定计数：进程内（瞬态，与旧实现一致）。 */
    private final Map<String, long[]> failCounter = new ConcurrentHashMap<>();

    public JpaRevocationStore(RevocationEntryRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public void blacklist(String jti, long exp) {
        repo.deleteByKindAndKey("DENY", jti);
        repo.deleteExpiredDeny(System.currentTimeMillis());
        RevocationEntry e = new RevocationEntry();
        e.setKind("DENY");
        e.setKey(jti);
        e.setVal(1L);
        e.setExpireAt(exp);
        repo.save(e);
    }

    @Override
    public boolean isBlacklisted(String jti) {
        return repo.findByKindAndKey("DENY", jti)
                .map(e -> e.getExpireAt() == null || e.getExpireAt() >= System.currentTimeMillis())
                .orElse(false);
    }

    @Override
    public Long tokenVersion(Long accountId) {
        return repo.findByKindAndKey("TV", String.valueOf(accountId))
                .map(RevocationEntry::getVal)
                .orElse(null);
    }

    @Override
    @Transactional
    public void bumpTokenVersion(Long accountId, long n) {
        RevocationEntry e = repo.findByKindAndKey("TV", String.valueOf(accountId)).orElse(null);
        if (e == null) {
            e = new RevocationEntry();
            e.setKind("TV");
            e.setKey(String.valueOf(accountId));
            e.setVal(n);
        } else {
            e.setVal(e.getVal() + n);
        }
        repo.save(e);
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
}
