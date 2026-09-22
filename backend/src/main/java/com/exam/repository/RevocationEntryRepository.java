package com.exam.repository;

import com.exam.entity.RevocationEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RevocationEntryRepository extends JpaRepository<RevocationEntry, Long> {

    Optional<RevocationEntry> findByKindAndKey(String kind, String key);

    void deleteByKindAndKey(String kind, String key);

    /** 清理过期的登出黑名单条目（内存容量有界，避免无限增长）。 */
    @Modifying
    @Query("delete from RevocationEntry e where e.kind = 'DENY' and e.expireAt is not null and e.expireAt < :now")
    void deleteExpiredDeny(@Param("now") long now);
}
