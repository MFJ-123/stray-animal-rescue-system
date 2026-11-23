# B 模块代码与最新基线差距分析表

## 盘点范围与版本

- 远端 main 经 `git fetch origin main` 核对为 `7d0d14f299e4c3f6bd47c4820171d268f0bcf051`。本地 `feat/b-clue-rescue` 与当前 `feat/0007-0018-contracts` 均指向 `a395349d6903a6a229c105faa63392b3e23153d9`。该引用没有提交 B 的 Java、Vue、SQL 或测试。以下「草稿」均指当前工作区的未跟踪文件。两引用之间只变更 README、跨模块契约和新增评阅版 Word。
- 已核对 main 的 [README](https://github.com/MFJ-123/stray-animal-rescue-system/blob/7d0d14f/README.md)、[基础工程接入](https://github.com/MFJ-123/stray-animal-rescue-system/blob/7d0d14f/docs/基础工程接入说明.md)、[公共权限与接口接入](https://github.com/MFJ-123/stray-animal-rescue-system/blob/7d0d14f/docs/公共权限与接口接入说明.md)、[审计与事件接入](https://github.com/MFJ-123/stray-animal-rescue-system/blob/7d0d14f/docs/审计与事件接入说明.md)、[账户与资格接入](https://github.com/MFJ-123/stray-animal-rescue-system/blob/7d0d14f/docs/账户与资格接入说明.md)、[跨模块契约](https://github.com/MFJ-123/stray-animal-rescue-system/blob/7d0d14f/docs/跨模块契约清单.md)、[迁移登记](https://github.com/MFJ-123/stray-animal-rescue-system/blob/7d0d14f/docs/迁移登记.csv)、[测试矩阵](https://github.com/MFJ-123/stray-animal-rescue-system/blob/7d0d14f/docs/test-matrix.csv) 与[概要设计评阅修订版](https://github.com/MFJ-123/stray-animal-rescue-system/tree/7d0d14f/docs/design)。Word 封面版本为 V1.1，Git 对象提取的 SHA-256 为 `b2a6639bac45d7da163c7ddc1b9c361847ca22b2b8fd27b1946c1e6492eed205`。下文的表号和章节均指此版。
- A 已合入公共工程、账户与资格、公共权限、幂等、审计及 outbox。项目目标库为 MySQL Server 8.0.46 / InnoDB。B 的草稿目前以单独的测试配置运行，生产接入及测试结果本次均未运行。

## Java、Vue 和测试差距

草稿路径中的 Java 文件均位于 `backend/src/main/java/cn/edu/sar/` 下。行号用于定位本次只读快照。

| 检查项 | main 基线 | B 草稿与结论 | 后续修订 |
|---|---|---|---|
| 会话与角色 | `AccessInterceptor.current(request).id()` 提供可信操作者。 `CurrentAccess.load(long)` 返回 Actor，含 roles、volunteerState、version，`requireRole(long,String)` 重查角色。 | `domain/rescue/RescuePartners.java` 采用无参 `currentActor()`、两个布尔角色、`requestId()`，只有测试实现。业务 Bean 引用了该依赖，正常启动缺少可注入实现。 | 控制器传入可信 actor ID，请求号从 A 的 RequestIdFilter 取得。角色从公共服务获取。 |
| 资格与暂停 | `AccessService.lockActiveVolunteer(long)` 返回 Account，要求已有事务。C3 实现 `ResponsibilityProvider.module()` 返回 TASK，`findOpen(long)` 返回 `List<Responsibility(type,objectId,description,path)>`。 | `RescuePartners.lockActiveVolunteer(long)` 返回 void，夹具仅锁假账户并检查 active。`RescueQueries.java:131` 返回 Map 列表，包含 PENDING 与 ACTIVE；无 TASK provider。 | 接 A 真实资格锁。按约定列当前负责人和有效协作责任，另确认待确认报名如何处置。暂停与新增责任用同一账户锁。 |
| 对象授权和公开入口 | `@PublicRead` 只放行选定的 GET/HEAD，结合 B 自己的 `ObjectPolicy` 和显式 DTO。 | `web/rescue/RescueController.java:30` 同一映射处理公开、本人、审核、任务列表，未加 `@PublicRead`。详情及历史主要由 `RescueQueries` 手工过滤，无 B 策略 Bean。 | 独立标注公开 GET，避免同一方法放行受保护路径。按 CLUE/TASK/PARTICIPANT 等范围复查当前关系。公共投影只接受平面标量，参与者、时间线等嵌套资料先逐层过滤再组装，列表 total 也走同一可见条件。 |
| 幂等 | `IdempotencyService.execute(long,String,String,Object,Class<T>,Runnable,Supplier<T>)`。每次回放前鉴权。回执唯一键为操作者＋动作＋键，请求键 8～64 位，动作字符符合 `[A-Z0-9_.:-]`。 | 草稿 `once(actor,operation,key,JsonNode,work)` 没有授权回调；动作含小写和斜杠，键允许 1～100 位。夹具按 actor+key 锁回执并比较序列化原文，不能证明生产回放语义。 | 用固定的动作码，业务输入含目标和预期版本，按 A 规则生成摘要。重放先查当前角色/归属，首次执行才校验前态和版本。 |
| 版本、响应及 system | `Versions.require(long,long)` 返回冲突码及 `data.currentVersion`。 `ApiResponse.success(data,request)`、BusinessException 和 GlobalExceptionHandler 统一错误和请求号。 `GET /api/v1/system/status` 仅用于启动和数据库自检。 | `Command.version()` 已读取 expectedVersion，但上限为 int；`RescueStore.checkVersion` 使用自定义冲突。`RescueController` 手拼 Map，错误体缺 data，与公共前端客户端的结构检查不兼容。Clock 注入方向一致。 | 沿用 A 的响应与错误，用 long 对齐物理版本。系统自检可验证运行环境，B 权限或业务证据从各自接口采集。 |
| 审计与 outbox | `AuditWriter.append(Change)` 返回审计编号，`OutboxService.append(key,type,objectType,long,Map<String,String>)` 返回 Event，两者均为 MANDATORY。模块 RESCUE，动作/对象大写。事件 payload 只接受受控 ID、状态码及 version，E 以 handler 消费。 | `record(..., List<Long> recipients)` 与 `remindAdministrators(eventKey,id)` 无直接对应。草稿含小写动作，夹具只写 test_event。线索关注/取消关注提前 return，无对应审计调用。 | 事务内写审计和必要事件，关联 auditId。E 通过 B 的历史和授权查询解析接收人，保留取消前的关系，避免只查 ACTIVE 漏发。后台审计可用系统 actorId=0，outbox 的 ID 字段只接受正数，后台事件用 auditId/taskId 引用。覆盖真实无变化、重放、回滚及重复提醒。 |
| 0014 认领、报名、确认 | 认领正文为 expectedVersion＋requestKey，返回 taskId、ownerId、state、version。报名仅必填 requestKey，确认仅必填报名 expectedVersion，两者返回 participantId、state、version。 | `TaskService.act` 对报名也校验任务版本，`participation` 对确认也强制键，统一 Change 只有 id/state/version。Vue 表单按动作统一附键。 | 采用三个明确 DTO，保留报名创建去重和确认/撤回互斥。公共“确认带键”与本条细则请 A/B 在契约中对齐。 |
| 0017 结果与验收 | 评阅版结果使用 kind、detail、expectedVersion、animalIds 或 missingReason。验收用 decision、expectedVersion，退回附 reason。 | `TaskService.java:149` 读取 resultKind、note、单个 animalId 和 noRecordReason。写结果时检查动物，验收分支未复核动物；Vue 使用草稿字段。 | 按新版数组和字段名接入。与 C 明确已有动物关联入口和提交结果如何复用、状态复查时点及与死亡归档的并发规则。草稿退回重提时只累加任务关联，需明确每轮结果对应的动物快照，避免把历史关联纳入本轮验收。 |
| Vue 请求、路由与页面 | `request<T>(path,options)` 自带 /api/v1，返回 `ApiEnvelope<T>`；`access.ts` 维护会话。路由由 A 汇总，当前 /clue、/rescue 指向说明页。 | `rescueHost.ts` 要求注入 RescueHost，调用形态为 method＋完整 API 路径＋body、直接返回 data，无实际 provider。草稿独立路由未注册。已建公开、本人、审核和任务区域，使用 snake_case DTO。 | 对齐相对路径和 `.data`，使用真实会话、公共错误与适配各接口的重试工具，确认 C 上传和下载。A 接入角色路由后验证三类界面及深层链接。 |
| 测试与真实环境 | 主工程用 `backend/mvnw.cmd -Pmysql-it verify` 在独立 MySQL 8.0.46 库执行 Flyway、HTTP 集成验证。矩阵 TC-02～05、16、17 均待验证。 | `tests/b/src/test/java/cn/edu/sar/tests/BWorkflowTest.java`有 8 个测试，覆盖上报回放、合并、100 请求认领、配额、确认撤回竞争、转派验收、公开边界、事务失败和取消提醒。默认 H2，另可通过 B_TEST_JDBC_URL 指向空库。测试自建简化 account/animal，手工执行 B 草稿 SQL，MockMvc 为 standalone。 | 将可复用场景接入统一 MySQL 与 HTTP 环境。补真实会话/CSRF、TC-12/15/18 联调、Flyway 新建和 V005 升级、事务失败后逐表核验。现有 100 请求直接调用 TaskService，帮助方法为所有命令补齐键和版本。另测三条 0014 最小合法 JSON、真实 HTTP 的 409 响应和真实审计/事件。测试源码只能作为场景设计证据。 |

## V1.1 物理模型差距

| 表或关系 | 草稿 | V1.1 与落地事项 |
|---|---|---|
| clue / animal_type | clue.species 为 varchar(32)，region 为 varchar(100)，version 为 INT。 | 表 7.18～7.19 以 `clue.type_id → animal_type.id` 存物种，region 为 varchar(32)，version 为 BIGINT。统一字典、未知物种编码及编号归属后修订查询、候选和展示。草稿另有公开摘要、优先级、发布时间等字段，逐项按查询契约登记，不直接删弃。 |
| clue_review | `clue_history(clue_id,actor_id,action,note,created_at)`，仅 clue_id 有外键。 | 表 7.20 使用 `clue_review`，actor_id 引用 account，reason varchar(500)，可空 replaces_id 自引用。写入、历史读取、附件多态类型和测试查询均仍使用旧模型，需一起迁移。 |
| clue_follow | 联合主键为 clue_id/user_id，双方外键已具备。 | 表 7.21 另要求 created_at；补关注时刻并核对取消关注后新消息的接收人规则。 |
| rescue_task | 以可空生成列 `active_clue_id` 和唯一索引限制未结任务，草稿用 region/ instructions，结果类别写在历史表。 | 表 7.22 明订生成列 `open_clue_id`，同一主线索历史 1:N，同时最多一个未结。约束思路已有，物理名、引用 SQL、BIGINT version、action_area、result_kind 仍有差距。保留可空 owner 外键，终结态生成 NULL。定时扫描所需字段另按明确周期登记。 |
| participant | 活动关系用 `UNIQUE(task_id,active_user)`，同表有 OWNER/COLLABORATOR、退出时间、原因。 | 表 7.23 定义协作报名，关注有效报名唯一及历史，并使用 BIGINT version。负责人当前关系由 rescue_task.owner_id 表达。请 D/B 确认负责人历史是否沿用草稿扩展或由执行/审计记录追溯，避免同一事实双写后不一致。 |
| task_progress | `task_entry` 使用 action、note、confirmed、replaces_id、public_note、结果和未建档原因。actor_id 无外键。 | 表 7.24 为 `task_progress`，使用 kind、detail、created_at、replaces_id，actor_id 引用 account。保留每轮提交、通过、退回和经办人，确认状态、公开摘要和未建档原因的字段承载待评审。草稿中时间线、统计及附件绑定均仍依赖旧表或类型。 |
| task_animal | B 草稿以 task_id+animal_id 联合主键，基本脚本只有 rescue_task 外键，另一草稿 ALTER 引用 animal。 | 表 7.26 的两端 FK 与拆分思路一致，远端尚无两侧业务表。C 创建 animal 后才能补齐其 FK。按动物查询任务需校验索引，确认动物列索引及联合验收用途。 |

所有上述 B SQL 仍属草稿。没有证据表明它们已在共享 Flyway 历史中执行。若有人在个人库手工执行过草稿，个人旧数据是否需要保留和如何映射，是独立迁移验证事项。

## BE.3.8 事务位置核对

| 命令 | 草稿事务位置 | 最新设计应落到 |
|---|---|---|
| 建任务，0013 | `TaskService.create` 调 A 草稿资格锁，再调用 `TaskClues.lockVerifiedMaster`，检查 openTasks 并写任务。 | `workflow` 开外层事务，调用 C2 校验已核实主线索，调用 C3 校验未结唯一性和创建任务。直接指派加入资格及配额校验。 |
| 开始执行，0015 | `TaskService.act` 的 start 和负责人首次 progress 调 `TaskClues.markProcessing`；TaskClues 是 MANDATORY。 | 两条进入执行的入口复用同一协调操作。C2 改线索，C3 改任务；线索版本、任务版本、历史、审计与事件在同一事务内。 |
| 撤回，0008 | `ClueService.act` 查任务并调用 `TaskService.cancelUnclaimed`。已认领/处理中分支记录终止申请。 | `workflow` 向 C3 取得当前阻塞并同步取消可取消任务；发起终止申请、撤回和取消的状态、记录及消息分支明示。 |
| 合并，0010 | `ClueService.act` 给源/目标加锁，查双方未结任务，按条件取消源待认领任务、写主从关系。 | `workflow` 从 C2 获取并重验线索，调用 C3 阻塞查询或取消。校验主从历史、任务协调及一致锁序。 |
| 结案，0012 | `ClueService.act` 读 `RescueStore.openTasks` 并写结局与 CLOSED。 | `workflow` 在同事务查询未结任务，再调 C2 完成结案，与并发建任务互斥。 |

目前未见 `domain/workflow/` 文件，控制器直接依赖两侧业务服务。`ClueService → TaskService → TaskClues` 已避开直接构造器环，但 C2 仍直接读取任务数据，`TaskClues` 也直接读 `rescue_task`。后续协调入口调用独立查询和原子更新服务，C2/C3 分别访问各自持久化。A 的操作者/回执锁与设计的主线索优先描述存在组合差异，确认完整锁顺序前保留为待决项。

## 迁移编号与验收

- main 实际有 V001 基础、V002 账户、V003 资格提交回执、V004 通用幂等、V005 审计/outbox。登记表最后一条为 V005，**最早可从 V006 向 D 申请，B 尚未获配该号**。迁移目录 README 的“从 V004 起”已落后于登记表与审计接入说明。
- 统一后端工程只打包 `V*__*.sql`，Flyway 开启命名校验。两个 `B__*.sql` 不在自动迁移链内；测试的 ResourceDatabasePopulator 直接运行它们。V002 已提供 account FK 目标。C 的 animal_type 与 animal 目前均未见正式迁移。
- 建议 D 按真实依赖登记。先提供 `animal_type`，再建有类型 FK 的 B 表；`task_animal` 可随 B 创建并先挂任务 FK。等 `animal` 和 `task_animal` 均存在，再用后续迁移补动物 FK。若 C 的 animal 可在不依赖 B 表的版本先建，也可以在 B 建关联表时一次写入两端 FK。正式编号和作者由 D 确认，每个文件一个作者。
- 在独立 MySQL 8.0.46 库验证空库全迁移及从 V005 升级，检查两端 FK、已有数据与历史保留、终态释放和再次创建、并发唯一及协作配额。共享迁移应用后以新版本修复。当前实测未运行。

待拍板接口、输入输出和验收场景见[需与 A C D E 确认的接口与迁移问题清单](需与A-C-D-E确认的接口与迁移问题清单.md)。
