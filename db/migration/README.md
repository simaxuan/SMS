# 数据库迁移规范（db/migration）

> **定位**：本目录存放 JPA `ddl-auto: update` 之外的**补充性数据库迁移**（外键、数据回填、只读约束等 JPA 自动建表无法表达的 DDL/DML）。
> **不接管 schema 主管理**：表结构仍由 JPA 实体注解统一生成（实体是字段唯一事实来源，见
> `docs/个人版校园版-统一数据库字段规范-第六轮DDL基线.md`），本目录仅承载已验证的增量运维脚本。

## 为什么不用 Flyway 全面接管

本项目 schema 由 JPA `ddl-auto`（H2 文件库为 `update`，生产 PG 走 profile）自动建表/演进。
若将 Flyway 设为 schema 唯一权威，将与 JPA 自动建表**双重管控冲突**：Flyway 期望库结构与
版本脚本严格一致，而 JPA `update` 会随手补齐未在脚本中的列，导致 `validate` 校验失败。
因此此处采用**版本化脚本 + 幂等可重复执行 + 人工审批入口**，而非全套切换 Flyway，
以最小副作用正规化迁移，不破坏现有零依赖部署与多租户隔离。

## 版本约定

- 文件名：`V<N>__<用途>.sql`（同 Flyway 命名；N 为递增整数，不得改号或用 .h2 重编号）。
- H2 / PostgreSQL 语法有差异的脚本，追加后缀：`V3__xxx.sql`（PG 版）+ `V3__xxx.h2.sql`（H2 版）。
- 每个脚本必须**幂等**（可重复执行不报错）：用 `IF NOT EXISTS` / `UPDATE ... WHERE ...` / 先预检。
- 每份脚本首行注释注明：目的、影响表、执行前提、是否可重复执行。

## 执行入口（规范）

| 场景 | 做法 |
|---|---|
| 新装环境 | JPA 自动建表后，按 V2→V3→V4 顺序执行 `db/migration`（含对应 .h2/.sql 版本） |
| 存量库升级 | 按需执行缺失的 V<n> 版本；H2 用 `.h2.sql`，PG 用 `.sql` |
| 二次执行安全性 | 全部脚本幂等，重复执行不改结果 |

> H2 库执行：用 `org.h2.tools.Shell` 或启动器自检，`RUNSCRIPT FROM '...'` 时**省略 `-password`**
>（sa 默认空密码；传空串会导致参数错位报 "Feature not supported"）。
> 项目未接入 Flyway 自动执行，迁移需经人工/发布流程审批后执行（见 `publish.ps1` 相关步骤）。

## 脚本索引

| 版本 | 文件 | 用途 | 幂等 |
|---|---|---|---|
| V2 | `V2__backfill_school_id.sql` | 按学生归属回填历史 school_id | 是 |
| V3 | `V3__add_foreign_keys.sql`（PG）/ `V3__add_foreign_keys.h2.sql`（H2） | 24 物理外键 + 25 索引 | 是（IF NOT EXISTS） |
| V4 | `V4__admin_true_global.sql` | admin 超管 true-global（school_id 置空） | 是（WHERE 限定） |

## 新增迁移流程

1. 新增脚本编号为 `V<max+1>`；
2. 校验幂等性（空库 + 已执行库各跑一遍）；
3. 若涉及 H2/PG 语法差异，同时产出 `.sql` 与 `.h2.sql` 两份；
4. 在此索引表登记并附变更说明。
