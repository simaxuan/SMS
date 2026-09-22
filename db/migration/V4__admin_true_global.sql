-- ============================================================================
-- V4__admin_true_global.sql
-- 三遗留处理（2026-09-21）— 项 3：种子 admin 改 true-global 的存量库适配
-- ----------------------------------------------------------------------------
-- 背景：DataSeeder 已把种子 admin 的 school_id 置为 NULL（真全局超管，scope_type=ALL）。
--       但历史上经 V2 回填脚本或旧版本创建的账号，ALL 超管的 school_id 可能仍指向某具体学校，
--       与"统一隔离口径"（SCOPE_ALL 且 schoolId=null 才放开全校总览）冲突——会被锁在该校。
--
-- 本脚本把【所有】scope_type='ALL' 的账号 school_id 置空，使其恢复"真全局超管"语义：
--   - 读路径：effectiveSchoolId() 返回 null → 放开全校总览；
--   - 写路径：resolveWriteSchoolId() 信任请求体 → 可跨校挂载组织；
--   - 前端登录校切换器可将其切到具体校（schoolId=该校），再切回 NULL 即总览。
--
-- 执行：备份后执行一次即可，幂等（重复执行仅把已为 NULL 的行再置空，无副作用）。
--       新库由 DataSeeder 直接生成 NULL，本脚本影响 0 行，安全。
-- 注意：若个别 ALL 超管"必须固定归属某校"，请勿执行本脚本，改由前端切换器显式设定。
-- ============================================================================

UPDATE account
SET school_id = NULL
WHERE scope_type = 'ALL'
  AND school_id IS NOT NULL;

-- 校验：应无 scope_type='ALL' 且 school_id 非空的账号
-- SELECT count(*) FROM account WHERE scope_type = 'ALL' AND school_id IS NOT NULL;
