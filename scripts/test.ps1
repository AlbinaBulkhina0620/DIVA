$ErrorActionPreference = 'Stop'
Push-Location "$PSScriptRoot/.."
try {
    $sources = Get-ChildItem src -Recurse -Filter *.java | ForEach-Object { $_.FullName }
    $tests = Get-ChildItem tests -Filter *.java | ForEach-Object { $_.FullName }
    & javac -encoding UTF-8 -d out $sources $tests
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    foreach ($suite in @('SemanticTests', 'EnvironmentTest', 'RuntimeErrorTest', 'InterpreterTest')) {
        & java -cp out $suite
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
    $result = 0
} finally { Pop-Location }
exit $result
