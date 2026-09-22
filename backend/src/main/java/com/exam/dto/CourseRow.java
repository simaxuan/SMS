package com.exam.dto;

/**
 * 跨考试得分率行投影（courseRowsByExam / courseRowsByExamByClassIds），替代 Object[] 魔法列序。
 * <p>历史列序约定：{@code [0]=examId, [1]=studentId, [2]=classId, [3]=score, [4]=fullScore}。
 * 重构后以具名 record 字段承载，杜绝列错位。
 */
public record CourseRow(
        Long examId,
        Long studentId,
        Long classId,
        Integer score,
        Integer fullScore) {

    /** 安全取满分：缺省/非正按 100 处理。 */
    public int safeFullScore() {
        return fullScore == null || fullScore <= 0 ? 100 : fullScore;
    }
}
