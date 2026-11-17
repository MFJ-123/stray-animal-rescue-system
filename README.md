# 流浪动物救助系统

课程项目共用基础工程，采用 Vue 3、Java 17、Spring Boot 和 MySQL Server 8.0.46 社区版 / InnoDB。仓库：[MFJ-123/stray-animal-rescue-system](https://github.com/MFJ-123/stray-animal-rescue-system)。

当前版本包含工作台、注册登录、会话保护、个人资料与改密、志愿者资格申请审核、管理角色配置及资格管理。公共角色/对象权限、字段白名单、公开只读入口和通用幂等受理已提供；个人资料接口已实际接入。账户关键操作与审计同事务提交。实际暂停须接入 B/C/E 的责任查询，缺失时安全阻止；其他业务模块、完整运营审计、outbox和消息仍待后续实现。

## 环境准备

| 组件 | 统一版本 | 确认方式 |
| --- | --- | --- |
| JDK | 17，设置 JAVA_HOME | java -version |
| Node.js | 24.22.1 | node --version |
| npm | 11.6.2 | npm --version；需要时 npm install --global npm@11.6.2 |
| Maven | 3.9.11，backend/mvnw 自动获取 | 首次使用需网络，无需单独安装 |
| MySQL Server | 8.0.46，InnoDB | 连接后 SELECT VERSION(); |

Workbench 8.0 CE 是可选管理工具。依赖由 pom.xml、Maven Wrapper、.node-version 和 package-lock.json 锁定，具体版本见[接入说明](docs/基础工程接入说明.md)。

## 获取项目和建立数据库

```powershell
git clone https://github.com/MFJ-123/stray-animal-rescue-system.git
Set-Location stray-animal-rescue-system
```

使用 Workbench 以本机数据库管理员连接，先将以下密码占位内容改为自己的专用密码，再执行；不要提交改后的密码：

```sql
SELECT VERSION();
CREATE DATABASE sar CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'sar_app'@'127.0.0.1' IDENTIFIED BY '替换为本机专用密码';
GRANT ALL PRIVILEGES ON sar.* TO 'sar_app'@'127.0.0.1';
```

如已有同名库或账号，先确认用途或换新名称，不删除已有数据。各人使用自己的数据库；应用启动时 Flyway 自动执行 db/migration 的 SQL，不要手动重复执行 V001。

## 开发启动

一个 PowerShell 终端进入 backend 目录，设置当前终端环境：

```powershell
$env:JAVA_HOME = '你的JDK17安装目录'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:SAR_DB_URL = 'jdbc:mysql://127.0.0.1:3306/sar?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&characterEncoding=UTF-8'
$env:SAR_DB_USER = 'sar_app'
$dbSecret = Read-Host '输入本机数据库密码' -AsSecureString
$env:SAR_DB_PASSWORD = [System.Net.NetworkCredential]::new('', $dbSecret).Password
.\mvnw.cmd spring-boot:run
```

第二个终端进入 frontend 目录：

```powershell
npm ci
npm run dev
```

打开 **http://127.0.0.1:5173**，工作台应显示“后端与数据库已连接”。前端将 /api 代理到后端 8080，无需开放跨源权限。后端自检接口为 GET http://127.0.0.1:8080/api/v1/system/status，实际读取数据库版本和基础记录；失败返回统一 503。两项服务仅绑定 127.0.0.1，停止时 Ctrl+C。

macOS/Linux 用 export 设置同名环境变量，后端执行 sh mvnw spring-boot:run；前端命令相同。

## 初始化第一个管理员

普通注册只产生普通用户。第一个系统管理员通过本机环境变量初始化，步骤见[账户与资格接入说明](docs/账户与资格接入说明.md)。仓库没有内置通用管理员密码。你的本机预览管理员凭据另存在工作目录的“本机预览管理员信息.txt”，该文件没有上传 GitHub。

## 测试和统一打包

前端目录：

```powershell
npm test
npm run build
```

后端目录：

```powershell
.\mvnw.cmd test
# 先设置 SAR_DB_* 指向独立 MySQL 8.0.46 测试库，再运行集成验证：
.\mvnw.cmd -Pmysql-it verify
```

普通后端测试不依赖数据库；集成测试执行真实迁移、重复迁移、SQL 和 HTTP 验证，不使用已有业务数据。前后端启动后，在 frontend 执行 npx playwright install chromium，再执行 npm run test:e2e，验证桌面/手机尺寸的真实连接、导航、刷新与失败恢复。浏览器下载不可用时，可在本机设置 SAR_BROWSER_EXECUTABLE 为已有 Chrome 的完整程序路径；CI 使用 Playwright 配套 Chromium。

在仓库根目录统一构建。Windows 下先停止正在运行的旧 JAR，再执行，以免旧进程锁住构建文件：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/build.ps1
# 配置好专用测试库后可加 -WithMysqlTests
```

产物 **backend/target/sar.jar** 含前端页面和迁移 SQL。保留数据库环境变量，根目录执行 java -jar backend/target/sar.jar，打开 **http://127.0.0.1:8080**，无需单独运行前端。Hash 路由支持模块页面刷新。Linux/macOS 先在 frontend 执行 npm ci、npm test、npm run build，再到 backend 执行 sh mvnw -B -ntp verify。

开发默认 HTTP；HTTPS、启停方式见[部署说明](deploy/README.md)。正式 HTTPS 浏览器信任与完整备份恢复在后续发布任务验收。

## 分工和资料

| 成员 | 主责 |
| --- | --- |
| A | C1、C7审计与事件基础设施、公共工程和发布 |
| B | C2线索、C3救助任务 |
| C | C4动物与照护、附件 |
| D | C5领养申请与交接、迁移登记 |
| E | C6退养回访、C7消息举报统计、联合验收组织 |

- [基础工程接入](docs/基础工程接入说明.md)、[基础验证记录](docs/基础工程验证记录.md)
- [账户与资格接入](docs/账户与资格接入说明.md)、[账户验证记录](docs/账户与资格验证记录.md)
- [公共权限与接口接入](docs/公共权限与接口接入说明.md)、[公共机制验证记录](docs/公共机制验证记录.md)
- [五人分工](docs/五人分工与集成方案.md)、[GitHub协作](docs/GitHub建仓与操作.md)
- [40条需求](docs/requirements.csv)、[21个用例](docs/test-matrix.csv)、[10项质量指标](docs/quality-matrix.csv)
- [任务清单](docs/backlog.csv)、[接口模板](docs/接口契约模板.md)、[跨模块契约](docs/跨模块契约清单.md)
- [Word基线](docs/baseline/manifest.json)、[数据库版本变更](docs/数据库版本变更记录.md)、[发布清单](docs/发布验收清单.md)

python scripts/check_plan.py 仅检查资料。Application foundation 工作流另行验证前端、Java、真实 MySQL 和浏览器；通过这些工程检查不代表业务功能已经通过验收。
