-- ============================================================================
-- V3__add_foreign_keys.sql
-- 三遗留处理（2026-09-21）— D1-01：物理外键落地
-- ----------------------------------------------------------------------------
-- 背景：此前所有 *_id 仅为语义裸 LONG 列，库层未强制外键约束（见《第六轮DDL基线》）。
--       本脚本在数据库层补建外键，把"引用完整性"从应用层下沉到库层，杜绝孤儿行。
--
-- 适用数据库：PostgreSQL（生产 profile）与 H2（文件库/本地）。两者 ADD CONSTRAINT
--       语法一致；下方均为标准 SQL，可直接执行。
--
-- 执行方式（重要）：本项目未接入 Flyway（ddl-auto=update 由 JPA 自动建表，实体保持裸
--       LONG，零启动风险）。本脚本为【手工迁移】，由 DBA / 运维在【备份后】执行一次：
--       ① 先跑"0) 孤儿预检"，确认所有计数均为 0；
--       ② 若有非零，先清理孤儿（或评估业务）再执行 FK 段；
--       ③ FK 段可重复执行（已存在则跳过分支见下）。
--
-- 约束说明：ON DELETE 按"强归属=RESTRICT、可选关联=SET NULL、纯连接表=CASCADE"原则，
--       与现有删除守卫（deleteCourse/deleteExamType/deleteClass 等已拦引用）保持一致。
-- ============================================================================


-- ----------------------------------------------------------------------------
-- 0) 孤儿预检：下列任一计数 > 0 时，绝不能建对应外键，须先处理孤儿。
--    每条输出即"子表存在、但父表无对应主键"的悬空引用数量。
-- ----------------------------------------------------------------------------
-- level.school_id
SELECT 'level.school_id' AS fk, COUNT(*) AS orphans
FROM level l WHERE l.school_id IS NOT NULL AND l.school_id NOT IN (SELECT id FROM school);
-- class_entity.level_id
SELECT 'class_entity.level_id' AS fk, COUNT(*) AS orphans
FROM class_entity c WHERE c.level_id IS NOT NULL AND c.level_id NOT IN (SELECT id FROM level);
-- class_entity.school_id
SELECT 'class_entity.school_id' AS fk, COUNT(*) AS orphans
FROM class_entity c WHERE c.school_id IS NOT NULL AND c.school_id NOT IN (SELECT id FROM school);
-- teacher_class.teacher_account_id
SELECT 'teacher_class.teacher_account_id' AS fk, COUNT(*) AS orphans
FROM teacher_class t WHERE t.teacher_account_id IS NOT NULL AND t.teacher_account_id NOT IN (SELECT id FROM account);
-- teacher_class.class_id
SELECT 'teacher_class.class_id' AS fk, COUNT(*) AS orphans
FROM teacher_class t WHERE t.class_id IS NOT NULL AND t.class_id NOT IN (SELECT id FROM class_entity);
-- account.school_id
SELECT 'account.school_id' AS fk, COUNT(*) AS orphans
FROM account a WHERE a.school_id IS NOT NULL AND a.school_id NOT IN (SELECT id FROM school);
-- student.class_id
SELECT 'student.class_id' AS fk, COUNT(*) AS orphans
FROM student s WHERE s.class_id IS NOT NULL AND s.class_id NOT IN (SELECT id FROM class_entity);
-- student.parent_owner_account_id
SELECT 'student.parent_owner_account_id' AS fk, COUNT(*) AS orphans
FROM student s WHERE s.parent_owner_account_id IS NOT NULL AND s.parent_owner_account_id NOT IN (SELECT id FROM account);
-- student.school_id
SELECT 'student.school_id' AS fk, COUNT(*) AS orphans
FROM student s WHERE s.school_id IS NOT NULL AND s.school_id NOT IN (SELECT id FROM school);
-- parent_student_bind.parent_account_id
SELECT 'parent_student_bind.parent_account_id' AS fk, COUNT(*) AS orphans
FROM parent_student_bind p WHERE p.parent_account_id IS NOT NULL AND p.parent_account_id NOT IN (SELECT id FROM account);
-- parent_student_bind.student_id
SELECT 'parent_student_bind.student_id' AS fk, COUNT(*) AS orphans
FROM parent_student_bind p WHERE p.student_id IS NOT NULL AND p.student_id NOT IN (SELECT id FROM student);
-- exam.exam_type_id
SELECT 'exam.exam_type_id' AS fk, COUNT(*) AS orphans
FROM exam e WHERE e.exam_type_id IS NOT NULL AND e.exam_type_id NOT IN (SELECT id FROM exam_type);
-- exam.creator_account_id
SELECT 'exam.creator_account_id' AS fk, COUNT(*) AS orphans
FROM exam e WHERE e.creator_account_id IS NOT NULL AND e.creator_account_id NOT IN (SELECT id FROM account);
-- exam.school_id
SELECT 'exam.school_id' AS fk, COUNT(*) AS orphans
FROM exam e WHERE e.school_id IS NOT NULL AND e.school_id NOT IN (SELECT id FROM school);
-- course.school_id
SELECT 'course.school_id' AS fk, COUNT(*) AS orphans
FROM course c WHERE c.school_id IS NOT NULL AND c.school_id NOT IN (SELECT id FROM school);
-- exam_type.school_id
SELECT 'exam_type.school_id' AS fk, COUNT(*) AS orphans
FROM exam_type t WHERE t.school_id IS NOT NULL AND t.school_id NOT IN (SELECT id FROM school);
-- grade.student_id
SELECT 'grade.student_id' AS fk, COUNT(*) AS orphans
FROM grade g WHERE g.student_id IS NOT NULL AND g.student_id NOT IN (SELECT id FROM student);
-- grade.school_id
SELECT 'grade.school_id' AS fk, COUNT(*) AS orphans
FROM grade g WHERE g.school_id IS NOT NULL AND g.school_id NOT IN (SELECT id FROM school);
-- grade.exam_id
SELECT 'grade.exam_id' AS fk, COUNT(*) AS orphans
FROM grade g WHERE g.exam_id IS NOT NULL AND g.exam_id NOT IN (SELECT id FROM exam);
-- grade.course_id
SELECT 'grade.course_id' AS fk, COUNT(*) AS orphans
FROM grade g WHERE g.course_id IS NOT NULL AND g.course_id NOT IN (SELECT id FROM course);
-- grade.creator_account_id
SELECT 'grade.creator_account_id' AS fk, COUNT(*) AS orphans
FROM grade g WHERE g.creator_account_id IS NOT NULL AND g.creator_account_id NOT IN (SELECT id FROM account);
-- exam_course_group.exam_id
SELECT 'exam_course_group.exam_id' AS fk, COUNT(*) AS orphans
FROM exam_course_group x WHERE x.exam_id IS NOT NULL AND x.exam_id NOT IN (SELECT id FROM exam);
-- exam_course_group.course_id
SELECT 'exam_course_group.course_id' AS fk, COUNT(*) AS orphans
FROM exam_course_group x WHERE x.course_id IS NOT NULL AND x.course_id NOT IN (SELECT id FROM course);
-- exam_course_group.school_id
SELECT 'exam_course_group.school_id' AS fk, COUNT(*) AS orphans
FROM exam_course_group x WHERE x.school_id IS NOT NULL AND x.school_id NOT IN (SELECT id FROM school);


-- ----------------------------------------------------------------------------
-- 1) 建外键：每条以"已存在则跳过"包裹（PostgreSQL 语法 DO 块），可安全重复执行。
--    ON DELETE 决策：
--      RESTRICT 强归属（删父必须先把子清干净，否则拒绝——与现有删除守卫一致）；
--      SET NULL 可选关联（父被删，子置空，仍可独立存在）；
--      CASCADE  纯连接表（父被删，连接行随之删除）。
-- ----------------------------------------------------------------------------

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_level_school') THEN
    ALTER TABLE level ADD CONSTRAINT fk_level_school
      FOREIGN KEY (school_id) REFERENCES school (id) ON DELETE RESTRICT;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_class_level') THEN
    ALTER TABLE class_entity ADD CONSTRAINT fk_class_level
      FOREIGN KEY (level_id) REFERENCES level (id) ON DELETE SET NULL;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_class_school') THEN
    ALTER TABLE class_entity ADD CONSTRAINT fk_class_school
      FOREIGN KEY (school_id) REFERENCES school (id) ON DELETE RESTRICT;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_tc_teacher') THEN
    ALTER TABLE teacher_class ADD CONSTRAINT fk_tc_teacher
      FOREIGN KEY (teacher_account_id) REFERENCES account (id) ON DELETE CASCADE;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_tc_class') THEN
    ALTER TABLE teacher_class ADD CONSTRAINT fk_tc_class
      FOREIGN KEY (class_id) REFERENCES class_entity (id) ON DELETE CASCADE;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_account_school') THEN
    ALTER TABLE account ADD CONSTRAINT fk_account_school
      FOREIGN KEY (school_id) REFERENCES school (id) ON DELETE RESTRICT;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_student_class') THEN
    ALTER TABLE student ADD CONSTRAINT fk_student_class
      FOREIGN KEY (class_id) REFERENCES class_entity (id) ON DELETE SET NULL;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_student_parent_owner') THEN
    ALTER TABLE student ADD CONSTRAINT fk_student_parent_owner
      FOREIGN KEY (parent_owner_account_id) REFERENCES account (id) ON DELETE SET NULL;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_student_school') THEN
    ALTER TABLE student ADD CONSTRAINT fk_student_school
      FOREIGN KEY (school_id) REFERENCES school (id) ON DELETE RESTRICT;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_psb_parent') THEN
    ALTER TABLE parent_student_bind ADD CONSTRAINT fk_psb_parent
      FOREIGN KEY (parent_account_id) REFERENCES account (id) ON DELETE CASCADE;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_psb_student') THEN
    ALTER TABLE parent_student_bind ADD CONSTRAINT fk_psb_student
      FOREIGN KEY (student_id) REFERENCES student (id) ON DELETE CASCADE;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_exam_exam_type') THEN
    ALTER TABLE exam ADD CONSTRAINT fk_exam_exam_type
      FOREIGN KEY (exam_type_id) REFERENCES exam_type (id) ON DELETE RESTRICT;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_exam_creator') THEN
    ALTER TABLE exam ADD CONSTRAINT fk_exam_creator
      FOREIGN KEY (creator_account_id) REFERENCES account (id) ON DELETE SET NULL;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_exam_school') THEN
    ALTER TABLE exam ADD CONSTRAINT fk_exam_school
      FOREIGN KEY (school_id) REFERENCES school (id) ON DELETE RESTRICT;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_course_school') THEN
    ALTER TABLE course ADD CONSTRAINT fk_course_school
      FOREIGN KEY (school_id) REFERENCES school (id) ON DELETE RESTRICT;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_exam_type_school') THEN
    ALTER TABLE exam_type ADD CONSTRAINT fk_exam_type_school
      FOREIGN KEY (school_id) REFERENCES school (id) ON DELETE RESTRICT;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_grade_student') THEN
    ALTER TABLE grade ADD CONSTRAINT fk_grade_student
      FOREIGN KEY (student_id) REFERENCES student (id) ON DELETE RESTRICT;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_grade_school') THEN
    ALTER TABLE grade ADD CONSTRAINT fk_grade_school
      FOREIGN KEY (school_id) REFERENCES school (id) ON DELETE RESTRICT;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_grade_exam') THEN
    ALTER TABLE grade ADD CONSTRAINT fk_grade_exam
      FOREIGN KEY (exam_id) REFERENCES exam (id) ON DELETE RESTRICT;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_grade_course') THEN
    ALTER TABLE grade ADD CONSTRAINT fk_grade_course
      FOREIGN KEY (course_id) REFERENCES course (id) ON DELETE RESTRICT;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_grade_creator') THEN
    ALTER TABLE grade ADD CONSTRAINT fk_grade_creator
      FOREIGN KEY (creator_account_id) REFERENCES account (id) ON DELETE SET NULL;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_ecg_exam') THEN
    ALTER TABLE exam_course_group ADD CONSTRAINT fk_ecg_exam
      FOREIGN KEY (exam_id) REFERENCES exam (id) ON DELETE CASCADE;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_ecg_course') THEN
    ALTER TABLE exam_course_group ADD CONSTRAINT fk_ecg_course
      FOREIGN KEY (course_id) REFERENCES course (id) ON DELETE CASCADE;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_ecg_school') THEN
    ALTER TABLE exam_course_group ADD CONSTRAINT fk_ecg_school
      FOREIGN KEY (school_id) REFERENCES school (id) ON DELETE SET NULL;
  END IF;
END
$$;


-- ----------------------------------------------------------------------------
-- 2) 可选：FK 列补索引（PostgreSQL 不会自动为外键建索引）。仅当查询常按这些列过滤时建议补。
--    以"不存在则创建"包裹，可重复执行。
-- ----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_level_school ON level (school_id);
CREATE INDEX IF NOT EXISTS idx_class_level ON class_entity (level_id);
CREATE INDEX IF NOT EXISTS idx_class_school ON class_entity (school_id);
CREATE INDEX IF NOT EXISTS idx_tc_teacher ON teacher_class (teacher_account_id);
CREATE INDEX IF NOT EXISTS idx_tc_class ON teacher_class (class_id);
CREATE INDEX IF NOT EXISTS idx_account_school ON account (school_id);
CREATE INDEX IF NOT EXISTS idx_student_class ON student (class_id);
CREATE INDEX IF NOT EXISTS idx_student_parent_owner ON student (parent_owner_account_id);
CREATE INDEX IF NOT EXISTS idx_student_school ON student (school_id);
CREATE INDEX IF NOT EXISTS idx_psb_parent ON parent_student_bind (parent_account_id);
CREATE INDEX IF NOT EXISTS idx_psb_student ON parent_student_bind (student_id);
CREATE INDEX IF NOT EXISTS idx_exam_exam_type ON exam (exam_type_id);
CREATE INDEX IF NOT EXISTS idx_exam_creator ON exam (creator_account_id);
CREATE INDEX IF NOT EXISTS idx_exam_school ON exam (school_id);
CREATE INDEX IF NOT EXISTS idx_course_school ON course (school_id);
CREATE INDEX IF NOT EXISTS idx_exam_type_school ON exam_type (school_id);
CREATE INDEX IF NOT EXISTS idx_grade_student ON grade (student_id);
CREATE INDEX IF NOT EXISTS idx_grade_school ON grade (school_id);
CREATE INDEX IF NOT EXISTS idx_grade_exam ON grade (exam_id);
CREATE INDEX IF NOT EXISTS idx_grade_course ON grade (course_id);
CREATE INDEX IF NOT EXISTS idx_grade_creator ON grade (creator_account_id);
CREATE INDEX IF NOT EXISTS idx_ecg_exam ON exam_course_group (exam_id);
CREATE INDEX IF NOT EXISTS idx_ecg_course ON exam_course_group (course_id);
CREATE INDEX IF NOT EXISTS idx_ecg_school ON exam_course_group (school_id);


-- ----------------------------------------------------------------------------
-- 回滚（如需撤销本迁移）：按建外键的反序 DROP CONSTRAINT。示例（择需执行）：
--   ALTER TABLE exam_course_group DROP CONSTRAINT fk_ecg_school;
--   ...（其余同理）...
--   索引 DROP：DROP INDEX IF EXISTS idx_ecg_school; ...
-- 注意：删除外键不会删除数据，仅解除约束。
-- ============================================================================
