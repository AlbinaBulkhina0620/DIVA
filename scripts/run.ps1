param([string]$SourceFile, [switch]$Ast)
$ErrorActionPreference = 'Stop'
Push-Location "$PSScriptRoot/.."
try {
    $sources = Get-ChildItem src -Recurse -Filter *.java | ForEach-Object { $_.FullName }
    & javac -encoding UTF-8 -d out $sources
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    $mainArgs = @()
    if ($Ast) { $mainArgs += '--ast' }
    if ($SourceFile) { $mainArgs += $SourceFile }
    & java -cp out Main @mainArgs
    $result = $LASTEXITCODE
} finally { Pop-Location }
exit $result
