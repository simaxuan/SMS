# 考试成绩管理系统 · 统一数据库字段规范（DDL 基线 v1.1）

> 文档性质：**全团队开发对齐的权威字段规范**（终极事实源）。
> 日期：2026-09-18（v1.1 修订 2026-09-22）　|　依据：第六轮优化整改方案 v5（已定稿）
> v1.1 变更：随 2026-09-21~22 落地同步——① `grade` 新增 `version`（乐观锁）列；② `system_setting` 落地 `school_id` 与 `uk_setting_school_key` 组合唯一键；③ `exam_course_group` 删除 `idx_ecg_exam`（被 `uk_exam_course` 覆盖）；④ 新增 `parent_preference` 表定义。实体为字段唯一事实来源，文档已与其对齐。
> 关键约束：**表结构由 JPA 实体注解自动生成**（H2 `create-drop` / PG `update`）。故「实体字段标注 == 数据库字段」，本文档即实体开发必须遵守的字段契约。
> 用途：开发前统一字段命名与类型；开发后据此做字段核对与联调验证，杜绝前后端/数据库字段不一致。

---

## 一、全局命名与类型规范（全员强制）

### 1.1 命名规范
- **表名**：蛇形小写（snake_case），如 `class_entity`、`parent_student_bind`。
- **列名**：蛇形小写，多词用 `_` 连接；Java 字段用驼峰 `camelCase`，通过 `@Column(name="snake_case")` 显式映射。
- **ID 主键**：统一 `id BIGINT`，自增（IDENTITY）。
- **外键语义字段**：统一 `<xxx>_id`（如 `student_id`、`exam_id`、`course_id`、`class_id`、`level_id`、`school_id`、`parent_account_id`、`exam_type_id`、`creator_account_id`）。
- **布尔字段**：用 Boolean 包装类（如 `deleted`、`enrolled`、`merged`），默认有明确语义。
- **时间字段**：创建 `created_at TIMESTAMP DEFAULT now()`（updatable=false）；单位调整用 `exam_date DATE`。
- **来源/类型枚举**：VARCHAR 存储字符串枚举（不用 int），如 `source`、`role`、`scope_type`、`origin`、`relation`、`auth_type`。

### 1.2 全局类型约定

| Java 类型 | 数据库类型 | 约定 |
|---|---|---|
| `Long` | BIGINT | 主键、外键 |
| `Integer` | INT | 分数/满分/数量 |
| `String` | VARCHAR(n) | 长度按列规格 |
| `LocalDate` | DATE | 日期（exam_date/birth_date）|
| `LocalDateTime` | TIMESTAMP | 时间（created_at）|
| `Boolean` | BOOLEAN | 布尔 |
| `BigDecimal` | DECIMAL | 金额/百分比（当前成绩用 int，聚合用 BigDecimal 计算）|

### 1.3 校验与约束
- `nullable=false` 表示非空；`uniqueConstraints` 声明唯一键；`indexes` 声明索引（命名 `idx_*` / `uk_*`）。
- 保留字规避：`SystemSetting` 用 `skey/svalue` 避开 `value` 保留字（沿用现有约定）。

---

## 二、表结构与字段定义（v5 最终版）

### 2.1 school（校园，多租户根）— 新增

| 列名 | Java 字段 | 类型 | 空 | 长度 | 约束/索引/说明 |
|---|---|---|---|---|---|
| id | id | BIGINT | N | - | PK 自增 |
| name | name | VARCHAR | N | 128 | 学校名 |
| code | code | VARCHAR | Y | 32 | **UNIQUE**，学校编码 |

```java
@Table(name="school", uniqueConstraints=@UniqueConstraint(name="uk_school_code", columnNames="code"))
```

### 2.2 level（级部/年级）— 新增

| 列名 | Java 字段 | 类型 | 空 | 长度 | 约束/索引/说明 |
|---|---|---|---|---|---|
| id | id | BIGINT | N | - | PK |
| school_id | schoolId | BIGINT | N | - | FK school.id |
| name | name | VARCHAR | N | 64 | 如"三年级" |
| created_at | createdAt | TIMESTAMP | N | - | 默认 now |

唯一键：`uk_school_level (school_id, name)`。

### 2.3 class_entity（班级）— 新增 2 列

| 列名 | Java 字段 | 类型 | 空 | 长度 | 约束/索引/说明 |
|---|---|---|---|---|---|
| id | id | BIGINT | N | - | PK |
| name | name | VARCHAR | N | 64 | UNIQUE（现有）|
| **level_id** | **levelId** | BIGINT | Y | - | FK level.id（**新增**）|
| **school_id** | **schoolId** | BIGINT | Y | - | FK school.id（**新增**）|
| created_at | createdAt | TIMESTAMP | N | - | 默认 now |

> 注：`name` 现有 UNIQUE 在多租户下需评估是否改为 `(school_id, name)` 组合唯一；首期建议调整为 `uk_class_school_name(school_id, name)`（见 6.1 迁移注意）。

### 2.4 teacher_class（老师-班级归属）— 新增

| 列名 | Java 字段 | 类型 | 空 | 长度 | 约束/索引/说明 |
|---|---|---|---|---|---|
| id | id | BIGINT | N | - | PK |
| teacher_account_id | teacherAccountId | BIGINT | N | - | FK account.id(role=TEACHER) |
| class_id | classId | BIGINT | N | - | FK class_entity.id |
| created_at | createdAt | TIMESTAMP | N | - | 默认 now |

唯一键：`uk_teacher_class (teacher_account_id, class_id)`。

### 2.5 account（账号）— 新增 6 列

| 列名 | Java 字段 | 类型 | 空 | 长 | 约束/索引/说明 |
|---|---|---|---|---|---|
| id | id | BIGINT | N | - | PK |
| username | username | VARCHAR | N | 64 | UNIQUE（现有）|
| password_hash | passwordHash | VARCHAR | N | 128 | BCrypt |
| role | role | VARCHAR | N | 16 | TEACHER/PARENT |
| nickname | nickname | VARCHAR | Y | 32 | |
| **phone** | **phone** | VARCHAR | Y | 20 | **UNIQUE（新增，家长登录名/校验凭据）** |
| **school_id** | **schoolId** | BIGINT | Y | - | FK school.id（**新增**）|
| **scope_type** | **scopeType** | VARCHAR | Y | 16 | CLASS/SCHOOL/ALL，默认 CLASS（**新增**）|
| **wechat_openid** | **wechatOpenid** | VARCHAR | Y | 64 | 微信预留（**新增**）|
| **wechat_unionid** | **wechatUnionid** | VARCHAR | Y | 64 | 微信预留（**新增**）|
| **auth_type** | **authType** | VARCHAR | Y | 16 | password/wechat/phone（**新增**）|
| **last_password_change** | **lastPasswordChange** | TIMESTAMP | Y | - | 改密审计（**新增**）|
| created_at | createdAt | TIMESTAMP | N | - | 默认 now |

唯一键：`uk_account_username(username)`、`uk_account_phone(phone)`（新增）。

### 2.6 student（学生）— 新增 8 列

| 列名 | Java 字段 | 类型 | 空 | 长 | 约束/索引/说明 |
|---|---|---|---|---|---|
| id | id | BIGINT | N | - | PK |
| student_no | studentNo | VARCHAR | N | 32 | UNIQUE（现有 uk_student_no）|
| name | name | VARCHAR | N | 64 | |
| gender | gender | VARCHAR | Y | 8 | |
| class_id | classId | BIGINT | Y | - | FK class_entity.id |
| birth_date | birthDate | DATE | Y | - | |
| phone | phone | VARCHAR | Y | 32 | 学生本人电话（现有）|
| **id_card** | **idCard** | VARCHAR | Y | 255 | **AES 加密（新增）** |
| **id_card_hash** | **idCardHash** | VARCHAR | Y | 128 | BCrypt(后8位)（**新增**）|
| **father_phone** | **fatherPhone** | VARCHAR | Y | 20 | 父方手机号（**新增**）|
| **mother_phone** | **motherPhone** | VARCHAR | Y | 20 | 母方手机号（**新增**）|
| **origin** | **origin** | VARCHAR | Y | 16 | school/self（**新增**，默认 school）|
| **parent_owner_account_id** | **parentOwnerAccountId** | BIGINT | Y | - | 自建学生家长（**新增**）|
| **enrolled** | **enrolled** | BOOLEAN | Y | - | 是否入学（**新增**，默认 true）|
| **merged** | **merged** | BOOLEAN | Y | - | 已认领合并（**新增**，默认 false）|
| **school_id** | **schoolId** | BIGINT | Y | - | FK school.id（**新增**）|
| deleted | deleted | BOOLEAN | N | - | 软删（现有，默认 false）|
| created_at | createdAt | TIMESTAMP | N | - | 默认 now |

### 2.7 parent_student_bind（绑定）— relation 枚举化（无结构变更，仅值约束）

| 列名 | Java 字段 | 类型 | 空 | 长 | 约束/索引/说明 |
|---|---|---|---|---|---|
| id | id | BIGINT | N | - | PK |
| parent_account_id | parentAccountId | BIGINT | N | - | FK account.id |
| student_id | studentId | BIGINT | N | - | FK student.id |
| relation | relation | VARCHAR | Y | 16 | father/mother/other（枚举化）|
| created_at | createdAt | TIMESTAMP | N | - | 默认 now |

唯一键：`uk_parent_student (parent_account_id, student_id)`（现有）。

### 2.8 system_setting（系统设置）— 新增键（无结构变更）

| 键名(KEY) | 值(Value) | 说明 | 归属 v5 |
|---|---|---|---|
| parent_rank_visible | true/false | 家长排名可见（现有）| 一 |
| **parent_rank_detail** | aggregate/limited/full | 家长排名档位，默认 aggregate | 一 |
| **std_weight_mode** | equal/full-weighted/custom-weighted | 标准分权重，默认 equal | 四 |
| **std_weights** | JSON | 自定义科目权重 | 四 |

> 注：v1.1 更新——`system_setting` 已落地 `school_id`（BIGINT，可空，全局行 NULL）并按校隔离，组合唯一键 `uk_setting_school_key(school_id, skey)`（`SystemSetting` 实体 `@Table` 声明），替代原「预留待决策」表述。

### 2.9 exam（考试）— school_id 新增（custom 保留）

| 列名 | Java 字段 | 类型 | 空 | 长 | 约束/索引/说明 |
|---|---|---|---|---|---|
| id | id | BIGINT | N | - | PK |
| name | name | VARCHAR | N | 128 | |
| exam_type_id | examTypeId | BIGINT | Y | - | FK exam_type.id |
| exam_date | examDate | DATE | Y | - | |
| semester | semester | VARCHAR | Y | 32 | 学期 |
| source | source | VARCHAR | N | 16 | school/custom |
| creator_account_id | creatorAccountId | BIGINT | Y | - | custom 来源创建家长 |
| **school_id** | **schoolId** | BIGINT | Y | - | FK school.id（**新增**）|
| created_at | createdAt | TIMESTAMP | N | - | 默认 now |

索引：`idx_exam_source_semester(source, semester)`（现有）。

### 2.10 course / exam_type — school_id 新增

course：`id, name, full_score(INT,默认100), school_id(BIGINT,新增), created_at`
- 唯一键：现有 `uk_course_name(name)`；多租户下评估改 `uk_course_school_name(school_id,name)`。

exam_type：`id, name, school_id(BIGINT,新增), created_at`
- 唯一键：现有 `uk_exam_type_name(name)`；评估改组合唯一。

### 2.11 grade（成绩）— 八轮强约束：新增冗余 school_id（快照）

> ⚠️ **口径已更新（第八轮）**：原「无结构变更（school 经 student 推导）」已落地强约束增强——新增冗余 `school_id` 快照列（写路径依所属学生回填），支撑按校过滤/查询/未来 DB 外键与报表；唯一键维持 `(student_id,exam_id,course_id,source)`（student_id 全局唯一已定位归属；school_id 可空，若并入唯一键会因 SQL NULL 语义破坏去重，故不放）。

| 列名 | Java 字段 | 类型 | 空 | 长 | 约束/索引/说明 |
|---|---|---|---|---|---|
| id | id | BIGINT | N | - | PK |
| **version** | **version** | **BIGINT** | **N** | - | **乐观锁版本（v1.1 新增 `@Version`，并发更新防护 D2-02，默认 0）** |
| student_id | studentId | BIGINT | N | - | FK student.id |
| **school_id** | **schoolId** | **BIGINT** | **Y** | - | **校归属快照（八轮新增，写路径回填；自建/未入学可空）** |
| exam_id | examId | BIGINT | N | - | FK exam.id |
| course_id | courseId | BIGINT | N | - | FK course.id |
| score | score | INT | N | - | |
| full_score | fullScore | INT | N | - | 默认 100 |
| source | source | VARCHAR | N | 16 | teacher/parent |
| creator_account_id | creatorAccountId | BIGINT | Y | - | 录入人 |
| created_at | createdAt | TIMESTAMP | N | - | 默认 now |

唯一键：`uk_student_exam_course_source(student_id,exam_id,course_id,source)`（现有，维持不变）。
索引：`idx_grade_exam_course_source`、`idx_grade_course_source`、`idx_grade_student_source`、**`idx_grade_school`（school_id）**。（`idx_grade_student(student_id)` 因被 `idx_grade_student_source(student_id, source)` 前缀覆盖，已删除去冗余）
写路径回填位置：`GradeService.toEntity/update`、`ParentService.migrateSelfGrades`、`DataSeeder`。

### 2.12 exam_course_group（考试-科目组合）— 新增

| 列名 | Java 字段 | 类型 | 空 | 长 | 约束/索引/说明 |
|---|---|---|---|---|---|
| id | id | BIGINT | N | - | PK |
| exam_id | examId | BIGINT | N | - | FK exam.id |
| course_id | courseId | BIGINT | N | - | FK course.id |
| active | active | BOOLEAN | N | - | 是否计入总分（默认 true）|
| school_id | schoolId | BIGINT | Y | - | FK school.id（**建议新增**，随上层推导）|

唯一键：`uk_exam_course(exam_id, course_id)`。
索引：无显式索引（`uk_exam_course` 前缀已覆盖按 exam_id 查询；`idx_ecg_exam` 已在 D4-02 删除，与第四节一致）。

### 2.13 revocation_entry（会话失效状态持久化）— 新增（八轮 D2-04）

> 取代原进程内内存实现（`InMemoryRevocationStore`）。用于跨重启 / 跨实例保留「登出黑名单」与「账号 token 版本」，使已登出 / 已改密的令牌重启后依旧失效。

| 列名 | Java 字段 | 类型 | 空 | 长 | 约束/索引/说明 |
|---|---|---|---|---|---|
| id | id | BIGINT | N | - | PK 自增 |
| kind | kind | VARCHAR | N | 8 | 类型：`DENY`（登出黑名单）/ `TV`（账号 token 版本）|
| key_str | key | VARCHAR | N | 64 | DENY→jti；TV→accountId 字符串 |
| val | val | BIGINT | N | - | DENY 固定 1；TV 为自增版本号 |
| expire_at | expireAt | BIGINT | Y | - | 过期毫秒时间戳（DENY 用；TV 为 null 永不过期）|
| created_at | createdAt | TIMESTAMP | N | - | 默认 now（updatable=false）|

唯一查找键：`(kind, key_str)`。
索引：`idx_revo_kind_key(kind, key_str)`（核心查询）、`idx_revo_expire(expire_at)`（定时清理已过期 DENY）。
说明：登录失败锁定计数（failureCount）因需原子自增且瞬态语义，保留进程内 `ConcurrentHashMap`，未入库；其余两项持久化。JPA 实体 `RevocationEntry`、仓库 `RevocationEntryRepository`、`JpaRevocationStore` 三件套落地。

### 2.14 parent_preference（家长偏好）— v1.1 新增文档

| 列名 | Java 字段 | 类型 | 空 | 长 | 约束/索引/说明 |
|---|---|---|---|---|---|
| id | id | BIGINT | N | - | PK 自增 |
| parent_account_id | parentAccountId | BIGINT | N | - | FK account.id |
| class_scope | classScope | BOOLEAN | N | - | 排名只看本班，默认 true |
| show_ties | showTies | BOOLEAN | N | - | 显示并列名次，默认 true |
| created_at | createdAt | TIMESTAMP | N | - | 默认 now |

唯一键：`parent_account_id`（`ParentPreference` 实体 `@Table` 声明，每家长一条偏好）。

---

## 三、字段对齐矩阵（实体 ↔ DDL ↔ 前端 API 字段）

> 前端 JSON 字段统一 **camelCase**，与 Java 字段一致；校验收敛点详见第五节。

| 实体字段 | 列名 | 前端字段 | 说明 |
|---|---|---|---|
| student.idCard | id_card | idCard | 仅详情返回（脱敏），列表不返回 |
| student.idCardHash | id_card_hash | -（服务端内部）| 不暴露 |
| student.fatherPhone | father_phone | fatherPhone | |
| student.motherPhone | mother_phone | motherPhone | |
| student.origin | origin | origin | |
| student.parentOwnerAccountId | parent_owner_account_id | parentOwnerAccountId | |
| student.enrolled | enrolled | enrolled | |
| student.merged | merged | merged | |
| student.schoolId | school_id | schoolId | |
| account.phone | phone | phone | |
| account.scopeType | scope_type | scopeType | |
| account.authType | auth_type | authType | |
| class_entity.levelId/schoolId | level_id/school_id | levelId/schoolId | |

---

## 四、索引规范（防联调性能问题）

| 表 | 索引 | 目的 |
|---|---|---|
| school | `uk_school_code` | 学校编码唯一 |
| level | `uk_school_level` | 级部名唯一 |
| class_entity | `uk_class_school_name`、`idx_class_level` | 班名+校唯一、级部查询 |
| teacher_class | `uk_teacher_class`、`idx_tc_class` | 归属唯一、按班查老师 |
| account | `uk_account_phone`、`idx_account_school` | 手机号唯一、按校过滤（删学校前校验） |
| student | `idx_student_class`、`idx_student_school` | 范围过滤 |
| exam | `idx_exam_source_semester`、`idx_exam_school_source` | 学期维度统计、按校+来源过滤 |
| exam_course_group | `uk_exam_course` | 组合唯一（已覆盖按 exam_id 查询；idx_ecg_exam 删除） |
| grade | `idx_grade_exam_course_source`、`idx_grade_course_source`、`idx_grade_student_source`、`idx_grade_school` | 成绩查询（idx_grade_student 已删除） |

---

## 五、联调验证清单（开发后必做）

- [ ] **实体字段核对**：`glob backend/src/main/java/com/exam/entity/*.java` 逐实体比对本文档表结构，列名/类型/长度/空/唯一/索引一致。
- [ ] **DDL 生成核对**：H2 `create-drop` 启动生成的建表 SQL 与本文档一致（开启 `ddl-auto=create` 导出比对）。
- [ ] **前端 API 字段核对**：`frontend/src/api/*.js` + 组件 `row.*` 字段与后端返回 camelCase 字段完全一致。
- [ ] **后端测试**：`mvn test` 全绿（现 76 项 JUnit）。
- [ ] **前端测试**：`npm test` 全绿（现 12 项 + 新增）。
- [ ] **单 jar 构建**：`mvn package -Dfrontend.bundle.skip=false` 成功，前端 dist 注入。
- [ ] **端到端**：登录→家长绑定（四要素+默认密码）→双父母同看同编辑→自建→认领迁移→级部排名→分布统计→多科总分，全链路字段无冲突。

---

## 六、迁移与兼容注意

### 6.1 多租户唯一键调整（需 DBA 确认）
- `class_entity.name`、`course.name`、`exam_type.name` 现有**全局唯一**，多租户下同名校可能出现在不同校 → 建议改为 `(school_id, name)` 组合唯一。
- 影响：`DataSeeder`、DTO 校验、前端提示需同步。
- `system_setting` 多租户：若按校隔离需 `+ school_id`，本期若全校共享设置可不加（待 S2 决策）。

### 6.2 存量数据
- `account.phone`/`scope_type`、`student.id_card` 等新列为空（存量兼容）。
- `scope_type` 存量 TEACHER 默认 `CLASS` 但无 teacher_class 归属 → 空视图；需提供"归属初始化"或迁移脚本把旧 admin 置 `ALL`。

### 6.3 加密
- `id_card` AES 加密；`idCardHash` BCrypt(后 8 位)；两者均不可明文返回。

---

*本文档为全团队字段对齐的权威基线，任何字段改动须先更新本表再开发，并走联调验证清单。*
