package com.exam.dto;

import java.math.BigDecimal;

/**
 * 聚合统计投影（stats / statsByClass），替代 Object[] 魔法列序。
 * <p>历史列序约定：{@code [0]=min, [1]=max, [2]=avg}。
 * <p>用 Number 承接数据库聚合返回（H2/PG 对 min/max/avg 可能返回 Integer/Long/Double/BigDecimal），
 * 读取时经 {@link #toDecimal(Number)} 统一转 BigDecimal。
 */
public record StatsRow(
        Number min,
        Number max,
        Number avg) {

    /** 安全转 BigDecimal（null/非数返回 null）。 */
    public static BigDecimal toDecimal(Number n) {
        return n == null ? null : new BigDecimal(n.toString());
    }
}
