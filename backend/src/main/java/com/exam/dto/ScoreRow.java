package com.exam.dto;

/**
 * 成绩分值投影（scores / scoresByClass），替代 Object[] 魔法列序。
 * <p>历史列序约定：{@code [0]=score, [1]=fullScore}。用于横截面统计的分值聚合/分布。
 */
public record ScoreRow(
        Integer score,
        Integer fullScore) {

    /** 安全取满分：缺省/非正按 100 处理。 */
    public int safeFullScore() {
        return fullScore == null || fullScore <= 0 ? 100 : fullScore;
    }
}
