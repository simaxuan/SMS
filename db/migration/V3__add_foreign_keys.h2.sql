-- ============================================================================
-- V3__add_foreign_keys.h2.sql  （H2 文件库专用执行版）
-- ----------------------------------------------------------------------------
-- 与 V3__add_foreign_keys.sql（PostgreSQL 版）同源，仅语法适配 H2：
--   - 去除 PostgreSQL 专属的 DO $$ ... END $$ 匿名块；
--   - 改用 H2 2.x 支持的 ALTER TABLE ... ADD CONSTRAINT IF NOT EXISTS（幂等）；
--   - CREATE INDEX IF NOT EXISTS 两者通用，原样保留。
-- 执行方式（DBA / 运维在备份后执行一次）：
--   java -cp h2-2.2.224.jar org.h2.tools.RunScript ^
--        -url "jdbc:h2:file:<库基路径>/examdb;DB_CLOSE_ON_EXIT=FALSE" ^
--        -user sa -password "" -script V3__add_foreign_keys.h2.sql
-- 本脚本可重复执行（约束/索引已存在则跳过）。
-- ============================================================================


-- ----------------------------------------------------------------------------
-- 1) 建外键（IF NOT EXISTS 幂等）。ON DELETE 决策同 PG 版：
--      RESTRICT 强归属；SET NULL 可选关联；CASCADE 纯连接表。
-- ----------------------------------------------------------------------------
ALTER TABLE level          ADD CONSTRAINT IF NOT EXISTS fk_level_school        FOREIGN KEY (school_id)          REFERENCES school (id) ON DELETE RESTRICT;
ALTER TABLE class_entity   ADD CONSTRAINT IF NOT EXISTS fk_class_level         FOREIGN KEY (level_id)           REFERENCES level (id)  ON DELETE SET NULL;
ALTER TABLE class_entity   ADD CONSTRAINT IF NOT EXISTS fk_class_school        FOREIGN KEY (school_id)          REFERENCES school (id) ON DELETE RESTRICT;
ALTER TABLE teacher_class  ADD CONSTRAINT IF NOT EXISTS fk_tc_teacher          FOREIGN KEY (teacher_account_id) REFERENCES account (id) ON DELETE CASCADE;
ALTER TABLE teacher_class  ADD CONSTRAINT IF NOT EXISTS fk_tc_class            FOREIGN KEY (class_id)           REFERENCES class_entity (id) ON DELETE CASCADE;
ALTER TABLE account        ADD CONSTRAINT IF NOT EXISTS fk_account_school      FOREIGN KEY (school_id)          REFERENCES school (id) ON DELETE RESTRICT;
ALTER TABLE student        ADD CONSTRAINT IF NOT EXISTS fk_student_class      FOREIGN KEY (class_id)           REFERENCES class_entity (id) ON DELETE SET NULL;
ALTER TABLE student        ADD CONSTRAINT IF NOT EXISTS fk_student_parent_owner FOREIGN KEY (parent_owner_account_id) REFERENCES account (id) ON DELETE SET NULL;
ALTER TABLE student        ADD CONSTRAINT IF NOT EXISTS fk_student_school      FOREIGN KEY (school_id)          REFERENCES school (id) ON DELETE RESTRICT;
ALTER TABLE parent_student_bind ADD CONSTRAINT IF NOT EXISTS fk_psb_parent     FOREIGN KEY (parent_account_id)  REFERENCES account (id) ON DELETE CASCADE;
ALTER TABLE parent_student_bind ADD CONSTRAINT IF NOT EXISTS fk_psb_student    FOREIGN KEY (student_id)         REFERENCES student (id) ON DELETE CASCADE;
ALTER TABLE exam           ADD CONSTRAINT IF NOT EXISTS fk_exam_exam_type      FOREIGN KEY (exam_type_id)        REFERENCES exam_type (id) ON DELETE RESTRICT;
ALTER TABLE exam           ADD CONSTRAINT IF NOT EXISTS fk_exam_creator        FOREIGN KEY (creator_account_id) REFERENCES account (id) ON DELETE SET NULL;
ALTER TABLE exam           ADD CONSTRAINT IF NOT EXISTS fk_exam_school         FOREIGN KEY (school_id)          REFERENCES school (id) ON DELETE RESTRICT;
ALTER TABLE course         ADD CONSTRAINT IF NOT EXISTS fk_course_school       FOREIGN KEY (school_id)          REFERENCES school (id) ON DELETE RESTRICT;
ALTER TABLE exam_type      ADD CONSTRAINT IF NOT EXISTS fk_exam_type_school    FOREIGN KEY (school_id)          REFERENCES school (id) ON DELETE RESTRICT;
ALTER TABLE grade          ADD CONSTRAINT IF NOT EXISTS fk_grade_student       FOREIGN KEY (student_id)         REFERENCES student (id) ON DELETE RESTRICT;
ALTER TABLE grade          ADD CONSTRAINT IF NOT EXISTS fk_grade_school        FOREIGN KEY (school_id)          REFERENCES school (id) ON DELETE RESTRICT;
ALTER TABLE grade          ADD CONSTRAINT IF NOT EXISTS fk_grade_exam          FOREIGN KEY (exam_id)            REFERENCES exam (id) ON DELETE RESTRICT;
ALTER TABLE grade          ADD CONSTRAINT IF NOT EXISTS fk_grade_course        FOREIGN KEY (course_id)          REFERENCES course (id) ON DELETE RESTRICT;
ALTER TABLE grade          ADD CONSTRAINT IF NOT EXISTS fk_grade_creator       FOREIGN KEY (creator_account_id) REFERENCES account (id) ON DELETE SET NULL;
ALTER TABLE exam_course_group ADD CONSTRAINT IF NOT EXISTS fk_ecg_exam         FOREIGN KEY (exam_id)            REFERENCES exam (id) ON DELETE CASCADE;
ALTER TABLE exam_course_group ADD CONSTRAINT IF NOT EXISTS fk_ecg_course       FOREIGN KEY (course_id)          REFERENCES course (id) ON DELETE CASCADE;
ALTER TABLE exam_course_group ADD CONSTRAINT IF NOT EXISTS fk_ecg_school       FOREIGN KEY (school_id)          REFERENCES school (id) ON DELETE SET NULL;


-- ----------------------------------------------------------------------------
-- 2) 外键列补索引（两者通用，IF NOT EXISTS 幂等）
-- ----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_level_school        ON level (school_id);
CREATE INDEX IF NOT EXISTS idx_class_level         ON class_entity (level_id);
CREATE INDEX IF NOT EXISTS idx_class_school        ON class_entity (school_id);
CREATE INDEX IF NOT EXISTS idx_tc_teacher          ON teacher_class (teacher_account_id);
CREATE INDEX IF NOT EXISTS idx_tc_class            ON teacher_class (class_id);
CREATE INDEX IF NOT EXISTS idx_account_school      ON account (school_id);
CREATE INDEX IF NOT EXISTS idx_student_class       ON student (class_id);
CREATE INDEX IF NOT EXISTS idx_student_parent_owner ON student (parent_owner_account_id);
CREATE INDEX IF NOT EXISTS idx_student_school      ON student (school_id);
CREATE INDEX IF NOT EXISTS idx_psb_parent          ON parent_student_bind (parent_account_id);
CREATE INDEX IF NOT EXISTS idx_psb_student         ON parent_student_bind (student_id);
CREATE INDEX IF NOT EXISTS idx_exam_exam_type      ON exam (exam_type_id);
CREATE INDEX IF NOT EXISTS idx_exam_creator        ON exam (creator_account_id);
CREATE INDEX IF NOT EXISTS idx_exam_school         ON exam (school_id);
CREATE INDEX IF NOT EXISTS idx_course_school       ON course (school_id);
CREATE INDEX IF NOT EXISTS idx_exam_type_school    ON exam_type (school_id);
CREATE INDEX IF NOT EXISTS idx_grade_student       ON grade (student_id);
CREATE INDEX IF NOT EXISTS idx_grade_school        ON grade (school_id);
CREATE INDEX IF NOT EXISTS idx_grade_exam          ON grade (exam_id);
CREATE INDEX IF NOT EXISTS idx_grade_course        ON grade (course_id);
CREATE INDEX IF NOT EXISTS idx_grade_creator       ON grade (creator_account_id);
CREATE INDEX IF NOT EXISTS idx_ecg_exam            ON exam_course_group (exam_id);
CREATE INDEX IF NOT EXISTS idx_ecg_course          ON exam_course_group (course_id);
CREATE INDEX IF NOT EXISTS idx_ecg_school          ON exam_course_group (school_id);
