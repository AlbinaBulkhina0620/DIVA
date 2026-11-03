param([string]$SourceFile)
$ErrorActionPreference = 'Stop'
Push-Location "$PSScriptRoot/.."
try {
    $sources = Get-ChildItem src -Recurse -Filter *.java | ForEach-Object { $_.FullName }
    & javac -encoding UTF-8 -d out $sources
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    if ($SourceFile) { & java -cp out Main $SourceFile }
    else { & java -cp out Main }
    $result = $LASTEXITCODE
} finally { Pop-Location }
exit $result
