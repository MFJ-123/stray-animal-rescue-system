# 后端目录
A 建立统一 Spring Boot 工程。web 和 jobs 为接入层，domain 为业务层，persistence 为持久化层，按业务子包分工。当前无 pom.xml 或应用源码。Java 17、MySQL 8.4 为基线，其余确切版本经共同验证后锁定。跨包通过服务契约调用，不修改别人的表。
