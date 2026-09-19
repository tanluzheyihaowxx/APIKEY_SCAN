param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $ScannerArgs
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$jar = Join-Path $root 'build\ApiKey_Scan.jar'
if (-not (Test-Path -LiteralPath $jar)) {
    & (Join-Path $root 'build.ps1')
}
& chcp.com 65001 | Out-Null
[Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
& java '-Dfile.encoding=UTF-8' '-Dsun.stdout.encoding=UTF-8' '-Dsun.stderr.encoding=UTF-8' -jar $jar --root $root @ScannerArgs
exit $LASTEXITCODE

