# 测试与联合验收

已有测试：frontend/src/api/client.test.ts 为请求工具测试；frontend/e2e/foundation.spec.ts 为桌面/手机浏览器测试；backend/src/test 为 Java 单元测试与 MySQL 集成测试。命令见根 README，实测结果见 docs/基础工程验证记录.md。

本目录后续增加跨模块、并发、权限、性能与质量验收脚本，各负责人编写本模块测试，E 汇总证据。TC-01—21 与 NFR-01—10 仍需待业务完成后验收。并发与迁移必须使用 MySQL 8.0.46 / InnoDB，记录 SELECT VERSION()，不能以内存数据库替代。
