$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
& (Join-Path $root 'build.ps1')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$testClasses = Join-Path $root 'build\test-classes'
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
Get-ChildItem -LiteralPath $testClasses -Recurse -File -ErrorAction SilentlyContinue | Remove-Item -Force
$mainSources = Get-ChildItem -LiteralPath (Join-Path $root 'src\main\java') -Recurse -Filter '*.java' | ForEach-Object { $_.FullName }
$testSources = Get-ChildItem -LiteralPath (Join-Path $root 'src\test\java') -Recurse -Filter '*.java' | ForEach-Object { $_.FullName }
& javac -encoding UTF-8 -d $testClasses @($mainSources + $testSources)
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Push-Location $root
try {
    & java '-Dfile.encoding=UTF-8' '-Dsun.stdout.encoding=UTF-8' '-Dsun.stderr.encoding=UTF-8' -cp 'build\test-classes' cn.apikeyscan.SelfTest $root
    exit $LASTEXITCODE
} finally {
    Pop-Location
}



