# 部署与HTTPS

根目录执行 scripts/build.ps1，生成 backend/target/sar.jar；设置 SAR_DB_* 后执行 powershell -ExecutionPolicy Bypass -File deploy/start.ps1。前台运行，Ctrl+C 停止；默认绑定 127.0.0.1:8080。MySQL Server 8.0.46 需单独运行。

页面、API、迁移随单 JAR 提供。开发默认 HTTP；课程演示 HTTPS 可使用 JDK keytool 生成本机测试证书，实际证书放仓库外，例如 D:/SAR/config：

```powershell
keytool -genkeypair -alias sar-local -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore D:/SAR/config/sar-local.p12 -validity 365 -dname "CN=localhost" -ext "SAN=dns:localhost,ip:127.0.0.1"
```

交互输入密码，然后在根目录设置：

```powershell
$env:SAR_SSL_ENABLED = 'true'
$env:SAR_PORT = '8443'
$env:SAR_SSL_KEY_STORE = 'file:D:/SAR/config/sar-local.p12'
$certSecret = Read-Host '输入证书密码' -AsSecureString
$env:SAR_SSL_KEY_STORE_PASSWORD = [System.Net.NetworkCredential]::new('', $certSecret).Password
java -jar backend/target/sar.jar --spring.config.additional-location=file:deploy/application-https.example.yml
```

示例使用 8443；按概要设计演示时可将 SAR_PORT 改为 443。须由演示浏览器信任对应测试证书，不能全局禁用证书验证；证书、密码不进仓库。浏览器信任及正式演示验收在后续发布任务完成。

本次提供构建、前台启停及 HTTPS 配置。完整业务备份恢复脚本由后续 A/C 完成，数据库与附件按同一批次备份、在独立位置恢复；不能用基础自检表的验证冒充全系统恢复。
