-- ============================================================================
-- V2__backfill_school_id.sql
-- 阶段 B（B2）：历史数据 school_id 补全迁移脚本
-- ----------------------------------------------------------------------------
-- 背景：多租户隔离要求读/写路径按 school_id 过滤。历史上由旧种子/旧版本创建的
--       student / exam / course / exam_type / grade / account 等可能 school_id 为 NULL，
--       导致这些行在"按校过滤"的读路径下"透出"（仅在校局超管可见），或无法被本校老师看到。
--
-- 本脚本将 school_id IS NULL 的历史行归属到指定学校：
--   1) student / exam / course / exam_type / account 直接归属到 <DEFAULT_SCHOOL_ID>；
--   2) grade 依据其 student 的 school_id 回填（若学生仍为 NULL 则归默认学校）。
--
-- 适用数据库：H2（文件库缺省）与 PostgreSQL（生产 profile）。
--            H2 与 PG 语法在该 UPSERT/UPDATE 场景下均兼容，可直接执行。
--
-- 执行：产品启动前由 DBA / 启动器在文件库或 PG 上执行一次即可（幂等）。
--       或启用内置迁移组件（app.migration.backfill-school-id.enabled=true）。
-- 注意：请将下方 <DEFAULT_SCHOOL_ID> 替换为实际的默认学校 id；若全库无任何学校，
--       先创建学校并取得其 id。
-- ============================================================================

-- 0) 若 use 的 DBA 版本无变量支持，请手写替换 <DEFAULT_SCHOOL_ID> 为实际数值。
--    H2 / PG 均支持直接常量替换。
-- 1) 学生
UPDATE student SET school_id = <DEFAULT_SCHOOL_ID> WHERE school_id IS NULL;
-- 2) 考试
UPDATE exam SET school_id = <DEFAULT_SCHOOL_ID> WHERE school_id IS NULL;
-- 3) 课程
UPDATE course SET school_id = <DEFAULT_SCHOOL_ID> WHERE school_id IS NULL;
-- 4) 考试类型
UPDATE exam_type SET school_id = <DEFAULT_SCHOOL_ID> WHERE school_id IS NULL;
-- 5) 账号（老师等归属）
UPDATE account SET school_id = <DEFAULT_SCHOOL_ID> WHERE school_id IS NULL;
-- 6) 成绩：依据其学生归属回填（学生已在步骤 1 全部置为 <DEFAULT_SCHOOL_ID>，故仅需 step 6b）
--    注：#9 修正：原「学生仍为 NULL 则归默认」分支（step6）因 step1 已先把所有学生置默认而成为死代码，
--    这里统一以学生的 school_id 为准回填，去除死代码分支。
UPDATE grade g SET school_id =
       (SELECT s.school_id FROM student s WHERE s.id = g.student_id)
WHERE g.school_id IS NULL
  AND g.student_id IS NOT NULL
  AND EXISTS (SELECT 1 FROM student s WHERE s.id = g.student_id AND s.school_id IS NOT NULL);

-- 校验：应无 school_id IS NULL 的归属数据
-- SELECT 'student', count(*) FROM student WHERE school_id IS NULL
-- UNION ALL SELECT 'exam', count(*) FROM exam WHERE school_id IS NULL
-- UNION ALL SELECT 'course', count(*) FROM course WHERE school_id IS NULL
-- UNION ALL SELECT 'grade', count(*) FROM grade WHERE school_id IS NULL
-- UNION ALL SELECT 'account', count(*) FROM account WHERE school_id IS NULL;
