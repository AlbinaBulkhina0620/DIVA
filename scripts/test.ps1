$ErrorActionPreference = 'Stop'
Push-Location "$PSScriptRoot/.."
try {
    $sources = Get-ChildItem src -Recurse -Filter *.java | ForEach-Object { $_.FullName }
    & javac -encoding UTF-8 -d out $sources tests/SemanticTests.java
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & java -cp out SemanticTests
    $result = $LASTEXITCODE
} finally { Pop-Location }
exit $result
