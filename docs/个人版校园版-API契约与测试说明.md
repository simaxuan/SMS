# 个人版（家长）/ 校园版（老师）——API 契约与测试覆盖说明

> 版本：v23.0（第六轮 v5 定稿文档同步）
> 日期：2026-09-18
> 用途：补齐「文档资料」「测试覆盖」「依赖配置」三维度证据，作为上线核对依据；已与《第六轮优化整改方案设计（v5 定稿）》及《统一数据库字段规范-第六轮DDL基线》对齐。
> ⚠️ 测试计数以本文第四节为准：后端 **76 项** JUnit、前端 **30 项** Vitest（含后续轮次新增用例：计量/权限/多租户/读路径防护/API契约/HTTP拦截器/租户强约束等）。

---

## 一、API 契约一览

统一约定：
- 前缀：`/api`
- 认证：JWT(HS256) 经 **HttpOnly Cookie（`exam_token`）** 随请求自动携带（前端 `withCredentials`）；兼容请求头 `X-Token`（服务端 `AuthInterceptor` 优先取 X-Token，其次 Cookie）。会话失效（登出/改密/重置）经 token 黑名单 + 版本失效即时推送，重启/多实例下由存储实现决定（默认内存态仅限单机，生产建议 Redis）。
- 统一响应体：`{ "code": int, "message": string, "data": T }`，`code=200` 表示成功
- 未携带/无效 token 访问受保护接口 → HTTP 401
- 越权（角色/数据范围不足）→ HTTP 403
- 资源不存在 → HTTP 404；参数/业务校验失败 → HTTP 400
- **多租户隔离**：`account/student/exam/course/exam_type` 写入归属取登录上下文 `school_id`，**不信任请求体 schoolId**；任何跨校访问（含统计/导出）必须被 `school_id` 校验拒绝。

### 认证（/api/auth，除 login/register/logout/wechat 外均需 token）

| 方法 | 路径 | 角色 | 请求体 | 说明 |
|---|---|---|---|---|
| POST | /api/auth/register | 公开 | {username, password(6-64), role, nickname?, phone?} | 仅允许 PARENT 自助注册；TEACHER 由种子/管理员创建，注册返回 400 |
| POST | /api/auth/login | 公开 | {username, password, role} | 家长 username 即手机号（按 username/phone 匹配）；role 必须与账号匹配，返回 {token, user}；失败锁定固定 **5 次/15 分钟**（硬编码） |
| POST | /api/auth/logout | 登录 | -（header X-Token） | 销毁会话 |
| GET | /api/auth/me | 登录 | - | 返回当前用户信息（含 schoolId/scopeType/phone） |
| POST | /api/auth/wechat | **需 token** | - | **微信登录预留占位（本期不做）**，需登录后调用，返回「未启用」；不实现鉴权逻辑，不做绑定引导 UI |

### 组织管理（/api/schools、/api/levels、/api/teacher-classes，ALL 或 CLASS 按范围）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET/POST/PUT/DELETE | /api/schools | 校园（多租户根）CRUD；**仅 ALL 角色（本校内）**可写 |
| GET/POST/PUT/DELETE | /api/levels | 级部 CRUD；带 school_id，归属校下唯一 |
| GET/POST/PUT/DELETE | /api/teacher-classes | 老师↔班级归属（teacher_class）；ALL 或指定老师 |
| GET/POST/PUT/DELETE | /api/classes | 班级 CRUD；新增 `levelId`/`schoolId`；删除有学生的班级 → 400 |

### 基础数据（/api/courses、/api/exam-types、/api/exams，登录即可）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET/POST/PUT/DELETE | /api/courses | 课程 CRUD；新增 `schoolId`；删除有成绩的课程 → 400 |
| GET/POST/PUT/DELETE | /api/exam-types | 考试类型 CRUD；新增 `schoolId` |
| GET/POST/PUT/DELETE | /api/exams | 考试 CRUD；新增 `schoolId`；创建/更新校验 examType 存在；删除有成绩的考试 → 400 |
| GET/POST/PUT/DELETE | /api/exam-course-groups | 考试-科目组合（exam_course_group）；`active` 标记是否计入总分 |

### 学生管理（老师）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/students?keyword&page&size&schoolId | 分页/关键词搜索（排除软删）；新增 `schoolId` 过滤 |
| GET | /api/students/{id} | 详情（不含明文 id_card；`idCard` 仅脱敏返回）；不存在 → 404 |
| POST | /api/students | 学号唯一校验；新增 `idCard(AES)/fatherPhone/motherPhone/origin/parentOwnerAccountId/enrolled/merged/schoolId` |
| PUT | /api/students/{id} | 学号唯一校验（排除自身）；同扩展字段 |
| DELETE | /api/students/{id} | 软删除 |

### 成绩（/api/grades，登录可用，按角色+数据范围隔离）

| 方法 | 路径 | 老师 | 家长 |
|---|---|---|---|
| GET | /api/grades?examId&courseId&studentId&scope | source=teacher 全校（按可见 class/level/school） | 绑定学生：teacher(只读)+本人 parent |
| POST | /api/grades | 录入 teacher 成绩 | 仅绑定学生，source=parent |
| POST | /api/grades/batch | 批量 | 批量（受限，超额跳过） |
| PUT | /api/grades/{id} | teacher 来源 | 仅本人录入的 parent；改挂未绑定学生 → 403 |
| DELETE | /api/grades/{id} | teacher 来源 | 仅本人录入的 parent |

唯一约束：`(student_id, exam_id, course_id, source)` —— 同学生/考试/课程下 teacher 与 parent 来源可各存一条。
分值校验：分数 ∈ [0, 满分]；满分默认 100，允许 >100。
`school_id` 经 `student.school_id` 推导（grade 表不冗余 school_id，查询时 join 校验一致）。

### 家长绑定（/api/parent，PARENT）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/parent/binds | 我的绑定学生列表 |
| POST | /api/parent/bind | **四要素**：{studentNo, name, phone, idCardLast8, relation, password} 学号+姓名+手机号+身份证后8位(+密码) 精确匹配；默认密码=身份证后8位(BCrypt)，不强制首改 |
| DELETE | /api/parent/binds/{studentId} | 解绑 |

### 统计（/api/statistics）

| 方法 | 路径 | 角色 | 说明 |
|---|---|---|---|
| GET | /api/statistics/course?examId&courseId | TEACHER | 横截面：total/min/max/avg/percentAvg/passRate/excellentRate/distribution |
| GET | /api/statistics/student-trend?studentId | 登录 | 个体轨迹；家长仅限绑定学生 |
| GET | /api/statistics/student?examId&studentId&examCourseGroupId | 登录 | **多科总分**：返回 `totalScore`/`totalFull`/`totalPercent`/`stdScore`（三口径并存） |
| GET | /api/statistics/rank?examId&courseId&classScope&showTies&scope=class\|level\|school | 登录 | 三级范围排名（`classScope`=班内排名开关，`showTies`=并列开关，`scope`=class/level/school 仅老师用）；家长分支仅返回本人 `{rank,score,percent,className,total}` + 班级/级部聚合（按 `parent_rank_detail`），**不返回全班明细** |

**统计口径（重要）——三指标并存**：
- **总分 `totalScore`** = `Σscore`（原始分累加，主展示）。
- **总得分率 `totalPercent`** = `Σscore / Σfull × 100`（跨考试可比）。
- **标准分 `stdScore`** = 各科得分率均值加权：
  - `equal`（默认）：`mean(各科 score/full×100)`；
  - `full-weighted`：`Σscore/Σfull×100`（≡ totalPercent）；
  - `custom-weighted`：`Σ(得分率×w_i)/Σw_i`，权重由 `system_setting.std_weights` 配置（**本期开放**）。
- 多科总分经 `exam_course_group`（`active` 标记计入科目）计算；缺考/无效科目不进分母；`full<=0` 不计；空数据 `stdScore=null`。
- 及格线 = 满分×60%；优秀线 = 满分×85%；分数段按得分率分桶（<60%、60-69%、70-79%、80-89%、≥90%）；不同满分均换算为得分率后统计；`passRate`/`excellentRate` 为百分比数值（0-100），`avg` 为绝对分均值，`percentAvg` 为平均得分率。

**家长排名档位 `parent_rank_detail`**（`system_setting`，默认 `aggregate`）：
- `aggregate`（默认）：仅本人名次 + 班级/级部聚合数值，不返回他人明细；
- `limited`：返回本人名次 + 同档聚合（弱化明细）；
- `full`：返回全班明细（仅 ALL 角色可用，默认不开启）。

---

## 二、自动化测试覆盖

### 后端（JUnit 5，H2 内存库，`backend/src/test`）

**AuthFlowTest（10 项）** —— MockMvc 全流程：
- 未登录访问业务接口 → 401
- admin 老师登录 + /me 获取当前用户
- 角色不匹配登录 → 401
- 家长自助注册成功
- 自助注册老师 → 400（防提权）
- 密码过短校验 → 400
- 无效 token → 401

**GradeServiceTest（16 项）** —— Service 集成（@Transactional 回滚）：
- 老师录入 source=teacher、同 source 重复拒绝、teacher/parent 来源并存
- 家长权限：仅绑定学生录入、改挂未绑定 → 拒绝、不可改老师成绩、不可删他人成绩、不可改他人 parent
- 未登录 → 拒绝
- 分数超满分 → 拒绝、批量超额跳过
- 家长列表可见性（仅绑定学生，teacher 只读 + 本人 parent）
- 家长零绑定 → 空列表
- 老师列表仅见 teacher 来源
- 统计及格/优秀率、avg

> 以上为既有表单组件相关用例。以下为各业务/安全测试类（合计 **76 项** JUnit），当前基线 `mvn test` 全绿 76/0/0。

**SchoolLevelMultiTenantTest（7 项）** —— 多租户与三级范围：
- school/level 创建带 school_id，级部名在校内唯一
- teacher_class 归属 + DataScopeService 三级范围（class/level/school）
- 跨校访问统计/导出 → 403（school_id 强隔离）
- CLASS 老师跨级部时 `visibleLevelIds` 限所属班级上位级部（不扩非所属级部）
- 多租户下读/写路径 school_id 校验闭环
- **八轮强约束**：老师录入成绩冗余 `school_id` 快照正确回填（= 目标学生所属校）
- **八轮强约束**：`GradeRepository.findBySchoolId` 按校过滤读取，跨校成绩互不串

**ReadScopeGuardTest（4 项）** —— 读路径班域防护：
- 老师越班查看学生成绩/统计/排名 → 403
- 家长越绑定查看 → 403
- DataScope 可见范围计算与过滤正确

**EncryptBindTest（2 项）** —— 加密与绑定四要素：
- student.id_card AES 加密入库、`id_card_hash`=BCrypt(后8位)，明文不返回
- 绑定四要素（学号+姓名+手机号+身份证后8位）校验 + 默认密码(后8位)BCrypt；双父母各绑定同一学生、信息/编辑一致（保留 creator_account_id 审计）；恢复默认密码仅老师端可操作

**StatisticsMetricTest（5 项）** —— 三口径与科目组合：
- totalScore/totalPercent/stdScore 三指标计算正确；exam_course_group 控制计入科目
- stdScore 三种权重模式（equal/full-weighted/custom-weighted）与边界（缺考不进分母、空数据 null）
- 满分换算/得分率/分数段分发与及格优秀线口径回归

**ParentRankPrivacyTest（7 项）** —— 排名隐私：
- `parent_rank_detail=aggregate` 默认仅返回本人名次 + 班级/级部聚合，不返回全班明细
- `full` 档位仅 ALL 角色可返回全班明细
- 档位开关、班内/校排、并列展示等场景

**LoginLockTest（1 项）** —— 账号安全：
- 登录失败固定 5 次/15 分钟锁定（硬编码，不配置化）

**SettingsAndAnalysisTest（12 项）** —— 设置与统计分析接口回归：
- Settings API 读写、std_weights/std_weight_mode 解析
- 排名/对比/趋势/学生多科汇总接口统计口径正确

运行：`cd backend && mvn -Dtest=AuthFlowTest,GradeServiceTest,SchoolLevelMultiTenantTest,EncryptBindTest,StatisticsMetricTest,ParentRankPrivacyTest,ReadScopeGuardTest,SettingsAndAnalysisTest,LoginLockTest test`（或直接 `mvn test`）

要求：`mvn test` 全绿（Failures=0, Errors=0，共 76 项 JUnit）为该版本放行门槛。

### 前端（Vitest，见 frontend/test/*.test.js，共 30 项）

**routerGuard.test.js（6 项）** —— 路由守卫判定 `resolveNav`（纯逻辑）：
- 未登录访问受保护页 → 跳 /login 带 redirect；角色受限 → 回跳首页；角色匹配/公开路由 → 放行；已登录访问登录页 → 按角色回跳

**useAuth.test.js（6 项）** —— 会话 composable：
- 无/老师/家长会话判读、损坏缓存按无会话、登出清理、登出异常兜底

**api.contract.test.js（9 项）** —— API 契约（api/index.js → http 调用映射）：
- studentApi/dictApi/examApi/gradeApi/statApi/settingsApi/authApi/多租户组织/家长 各方法 → http 方法+路径+参数一致（覆盖全部 10 组 API）

**http.interceptor.test.js（9 项）** —— 统一响应/鉴权/错误拦截：
- code=200 → 剥离返回 data；code≠200 → ElMessage 提示并 reject；code=401 → 清理本地会话并 reject；HTTP 业务错误 → 取后端 message；HTTP 401 降级；网络错误兜底；请求拦截器透传 config（HttpOnly Cookie 承载，不注入 X-Token）

运行：`cd frontend && npm test`

### 手动端到端验证脚本（publish/logs/verify_*.py）

历史另以纯 Python urllib 直跑真实服务做端到端回归（login→token→业务链路），共 20 项评审修复点 + 软删统计专项，作为自动化测试之外的真实服务层兜底。

---

## 三、依赖配置核对

### 后端 pom.xml
- spring-boot-starter-web / data-jpa / validation ✅
- spring-security-crypto（BCrypt，不引入完整 Security）✅
- **AES 加密**：`APP_CRYPTO_KEY`（演示内置，README 置换）用于 `student.id_card` ✅
- h2（runtime，演示）/ postgresql（runtime，生产）✅
- lombok（optional）✅
- spring-boot-starter-test（test scope）✅ —— 本轮起实际使用
- 结论：依赖齐备、scope 正确；无多余依赖。

### 前端 package.json
- 运行时：vue / element-plus / echarts / vue-router / icons ✅（`pinia` 已于第八轮移除——状态简单未使用，消除死依赖，减小打包体积）
- 构建：vite / @vitejs/plugin-vue ✅
- 测试：vitest / @vue/test-utils / jsdom ✅

### 生产注意事项（上线前）
1. 生产用 `application-prod.yml`（PostgreSQL，`spring.profiles.active=prod`），**必须关闭 h2-console**（当前默认 resources 中 enabled:true 仅为演示）。
2. 会话撤销/登录锁等过程状态已抽象为 `RevocationStore` 接口，默认 `InMemoryRevocationStore`（单机可用）；规模化前可替换为 Redis 实现而不改业务代码。
3. localStorage 存 token 存在 XSS 暴露面 —— 生产建议 httpOnly Cookie + CSP。
4. avg 采用绝对分均值，满分不一致时口径需知悉（前端已同时展示 percentAvg）。
5. **AES 密钥 `APP_CRYPTO_KEY` 须外部注入**，禁止硬编码入库；`id_card` 明文不返回、不落日志。
6. **跨源白名单 `APP_ALLOWED_ORIGINS`**（逗号分隔）须按实际前端域名配置，生产默认空（拒绝跨源）；同源请求不受影响。JWT 密钥 `APP_SECURITY_TOKEN_SECRET` 亦须注入，`ProductionGuard` 拒绝默认值启动。
7. **多租户 `school_id` 强隔离**：统计/导出/CRUD 全部按登录上下文 school 校验，杜绝跨校串数据。
8. 登录失败锁定硬编码 5 次/15 分钟（v5 确认不配置化）；恢复默认密码仅老师端操作（审计留痕）。
9. 微信登录 `POST /api/auth/wechat` 仅占位返回「未启用」，不实现鉴权与绑定 UI（预留字段 openid/unionid/auth_type 仅建 schema）。

---

## 四、上线核对清单

- [ ] `mvn test` 全绿（**76 项 JUnit**）
- [ ] `npm test` 前端单测通过（**30 项 Vitest**）
- [ ] `mvn package -Dfrontend.bundle.skip=false` 构建成功，前端 dist 注入 jar
- [ ] 新 jar 部署 `publish/server/exam-score-backend-0.0.1-SNAPSHOT.jar`
- [ ] 端到端回归（verify_personal/verify_fixes/verify_softdel）通过
- [ ] 生产切换 PostgreSQL profile 并关闭 h2-console
- [ ] 注入 `APP_CRYPTO_KEY`（AES 加密生效）
- [ ] 多租户 `school_id` 隔离 + 三级范围排名验证通过
- [ ] 家长排名 `parent_rank_detail=aggregate` 默认隐私策略生效
- [ ] 三口径（totalScore/totalPercent/stdScore）+ custom-weighted 权重配置生效

---

## 五、会话失效边界说明（重要）

- **默认实现为进程内内存（`InMemoryRevocationStore`）**：登出黑名单（jti）、改密/重置后的 token 版本、登录失败锁定计数均存于 JVM 内存。
- **重启即失效**：单机重启后，已登出的旧 JWT 失效记录与失败锁定计数会丢失（账户 TTL 内可能重新可用）。单机单实例场景可接受。
- **多实例 / 高可用**：如需水平扩展（多节点 / 重启持续），须替换为共享存储实现（如 Redis），实现 `com.exam.service.RevocationStore` 接口注入即可，业务调用方零改动。
- 生产部署请结合「HTTPS + Cookie Secure（由反向代理补加）」以达成纵深会话安全。
