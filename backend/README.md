# 后端目录
A 建立统一 Spring Boot 工程。web 和 jobs 为接入层，domain 为业务层，persistence 为持久化层，按业务子包分工。当前无 pom.xml 或应用源码。Java 17、MySQL Server 8.0.46 社区版 / InnoDB 为基线，其余确切版本经共同验证后锁定。跨包通过服务契约调用，不修改别人的表。

全组在连接目标数据库后执行 `SELECT VERSION();` 核对服务端版本。MySQL Workbench 8.0 CE 可作为管理工具；应用通过数据库驱动连接 MySQL Server。选型变更见[数据库版本变更记录](../docs/数据库版本变更记录.md)。
