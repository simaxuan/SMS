package com.exam.dto;

import java.math.BigDecimal;

/**
 * 成绩行投影（rankRows / rankRowsByClassIds / 学期组装行），替代 Object[] 魔法列序。
 * <p>历史列序约定：{@code [0]=studentId, [1]=studentNo, [2]=name, [3]=classId, [4]=score, [5]=fullScore}。
 * 重构后以具名 record 字段承载，杜绝列错位（编译期类型安全）。
 */
public record RankRow(
        Long studentId,
        String studentNo,
        String name,
        Long classId,
        Integer score,
        Integer fullScore) {

    /** 安全取满分：缺省/非正按 100 处理（与历史 {@code stat} 逻辑一致）。 */
    public int safeFullScore() {
        return fullScore == null || fullScore <= 0 ? 100 : fullScore;
    }
}
