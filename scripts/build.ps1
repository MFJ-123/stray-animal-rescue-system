param([switch]$WithMysqlTests)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
Push-Location (Join-Path $repoRoot 'frontend')
try {
    & npm.cmd ci
    if ($LASTEXITCODE -ne 0) { throw '前端依赖安装失败' }
    & npm.cmd test
    if ($LASTEXITCODE -ne 0) { throw '前端测试失败' }
    & npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw '前端构建失败' }
} finally { Pop-Location }
Push-Location (Join-Path $repoRoot 'backend')
try {
    $mavenArgs = @('-B', '-ntp', 'clean', 'verify')
    if ($WithMysqlTests) { $mavenArgs += '-Pmysql-it' }
    & .\mvnw.cmd @mavenArgs
    if ($LASTEXITCODE -ne 0) { throw '后端构建或测试失败' }
} finally { Pop-Location }
Write-Output '构建完成：backend/target/sar.jar（已包含前端页面）'
