param([string]$Jar = (Join-Path $PSScriptRoot '../backend/target/sar.jar'))
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $Jar)) { throw '没有找到 sar.jar，请先执行 scripts/build.ps1' }
if (-not $env:SAR_DB_PASSWORD) { throw '请先在当前终端设置 SAR_DB_PASSWORD，具体步骤见 README' }
& java -jar (Resolve-Path -LiteralPath $Jar).Path
if ($LASTEXITCODE -ne 0) { throw '应用启动失败，请检查数据库配置和日志' }
