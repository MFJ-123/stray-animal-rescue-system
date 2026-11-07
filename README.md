# 流浪动物救助系统

这是项目的协作初始化资料，包含五人分工、接口和数据边界、需求与验收追踪、GitHub 协作模板。当前尚无应用源码，不是可以启动的系统；公开仓库地址为 https://github.com/MFJ-123/stray-animal-rescue-system 。成员邀请待组长填写四位组员的 GitHub 用户名后办理。

先阅读 [五人分工与集成方案](docs/五人分工与集成方案.md)。成员 A 是组长，其余 B—E 待填写真实姓名和 GitHub 用户名；不按个人特长或开发周期预设任务。

| 成员 | 主责 |
| --- | --- |
| A | C1、C7 审计及事件基础设施、共同工程、集成发布 |
| B | C2 线索、C3 救助任务与协作 |
| C | C4 动物与照护、附件 |
| D | C5 领养申请与交接、迁移登记 |
| E | C6 退养回访、C7 消息举报统计、联合验收组织 |

## 资料入口

- [GitHub 建仓和日常操作](docs/GitHub建仓与操作.md)
- [40 条需求分工](docs/requirements.csv)
- [21 个验收用例](docs/test-matrix.csv)
- [10 项质量指标](docs/quality-matrix.csv)
- [启动任务清单](docs/backlog.csv)
- [接口契约模板](docs/接口契约模板.md)
- [跨模块契约清单](docs/跨模块契约清单.md)
- [源文档与校验清单](docs/baseline/manifest.json)
- [发布验收清单](docs/发布验收清单.md)

技术基线为 Vue 3、Java 17、Spring Boot、MySQL 8.4 / InnoDB。业务范围以 docs/baseline 下两份原文为准。当前脚手架目录只有职责说明，具体依赖版本、应用构建及启动命令由 A 在工程骨架任务完成后补齐并验证。

## 本地资料检查

在仓库根目录执行 `python scripts/check_plan.py`（Python 3 标准库即可）。它检查需求和验收编号覆盖、负责人、基线文档哈希和相对链接，不会验证 Java/Vue 或业务实现。随附 GitHub Actions 仅运行这一资料检查；应用源码建立后必须另外增加实际应用测试。
