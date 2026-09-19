$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$src = Join-Path $root 'src\main\java'
$build = Join-Path $root 'build'
$classes = Join-Path $build 'classes'
$jar = Join-Path $build 'ApiKey_Scan.jar'

New-Item -ItemType Directory -Force -Path $classes | Out-Null
Get-ChildItem -LiteralPath $classes -Recurse -File -ErrorAction SilentlyContinue | Remove-Item -Force
$sources = Get-ChildItem -LiteralPath $src -Recurse -Filter '*.java' | ForEach-Object { $_.FullName }
if (-not $sources) { throw '未找到 Java 源文件' }
& javac -encoding UTF-8 -d $classes $sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& jar cfe $jar cn.apikeyscan.Main -C $classes .
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Output "构建完成: $jar"


