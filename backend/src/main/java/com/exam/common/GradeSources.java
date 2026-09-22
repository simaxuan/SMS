package com.exam.common;

/**
 * 成绩/考试「来源」常量集中定义（消除散落字符串字面量，防拼写漂移）。
 * <ul>
 *   <li>成绩来源 {@link #TEACHER}（校内考试，老师录入）/{@link #PARENT}（家长自测/录入）</li>
 *   <li>考试来源 {@link #SCHOOL}（校园版校内考试）</li>
 * </ul>
 * <p>对应《统一数据库字段规范》：grade.source、exam.source。
 */
public final class GradeSources {

    private GradeSources() {
    }

    /** 成绩来源：校内考试（老师录入/或系统导入）。 */
    public static final String TEACHER = "teacher";

    /** 成绩来源：家长自测/录入。 */
    public static final String PARENT = "parent";

    /** 考试来源：校园版校内考试。 */
    public static final String SCHOOL = "school";
}
