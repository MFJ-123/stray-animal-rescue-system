# 数据库迁移

MySQL Server 8.0.46 / InnoDB。V001__foundation.sql 创建 sar_foundation 自检表；不包含账户或动物业务表。编号已登记，D 后续从 V002 起按依赖分配。

本目录是唯一 SQL 源，Maven 打入 classpath:db/migration，应用启动由 Flyway 自动执行并校验；不要复制或手工重复执行。已应用的共享迁移不改写，修复追加新版本，Flyway clean 默认禁用。

-Pmysql-it 使用真实 MySQL 验证 V001 及重复 migrate；新增迁移仍需验证空库与已有版本升级，不能将 V001 重复执行检查当作未来业务升级已通过。
