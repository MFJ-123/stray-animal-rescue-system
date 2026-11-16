# 部署与恢复
A 负责统一构建、HTTPS、配置示例、启停及数据库备份恢复；C 负责附件备份、校验和恢复部分。D 校验迁移版本，E 组织另机恢复验收。最终采用 Windows、单个 Spring Boot JAR 提供前端和接口。密钥不进仓库，数据与附件按同批次恢复。当前仅职责说明，尚无部署脚本。

部署数据库为 MySQL Server 8.0.46 社区版 / InnoDB。使用与该服务端版本配套的备份恢复工具，在同版本独立测试库演练恢复。MySQL Workbench 8.0 CE 是可选管理工具，不代替运行中的 MySQL Server；启动检查记录 `SELECT VERSION();` 的结果。
