# 后端

Java 17、Spring Boot 3.5.16、Maven Wrapper 3.9.11、MySQL Server 8.0.46 / InnoDB。当前实现连接检查、统一响应、请求号、异常处理和 Flyway 迁移。

设置 SAR_DB_URL、SAR_DB_USER、SAR_DB_PASSWORD 后执行 mvnw.cmd spring-boot:run，默认 127.0.0.1:8080，完整命令见[首页](../README.md)。

web 和 jobs 为接入层；domain 为业务层；persistence 为持久化层；common 维护响应、请求号和时钟。各负责人在这些层下使用 access/clue/rescue/animalcare/adoption/followup/operations 业务包；system 只用于工程自检。

控制器不直接操作数据库，跨包通过服务契约调用；Clock 默认 UTC，可在测试替换。账户接口已实现数据库会话、来源与账号登录保护、角色校验和CSRF防护。API控制器默认需登录；注册登录及显式标注PublicRead的GET/HEAD可匿名调用，写入不受该注解放行。CurrentAccess、ObjectAuthorization、IdempotencyService和Versions供业务包复用，具体对象规则由各业务方注册，缺失时拒绝。见[公共机制接入](../docs/公共权限与接口接入说明.md)。

mvnw.cmd test 执行无需数据库的单元测试；mvnw.cmd -Pmysql-it verify 额外运行真实 MySQL 测试。SQL 唯一源为根 db/migration，Maven 自动纳入资源；前端先构建后，frontend/dist 也打入 sar.jar。完整交付使用根 scripts/build.ps1。

domain/operations提供AuditWriter、AuditService、OutboxService及OutboxHandler；jobs/OutboxJob定时领取已注册类型。审计和事件登记要求已有业务事务，E在同一消费事务生成站内消息。查询与追加纠错、人工重试接口均已接入权限及幂等，详见[审计事件接入](../docs/审计与事件接入说明.md)。
