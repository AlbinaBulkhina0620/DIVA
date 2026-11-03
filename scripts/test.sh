#!/bin/bash
set -eu
cd "$(dirname "$0")/.."
javac -encoding UTF-8 -d out src/lexer/*.java src/ast/*.java src/parser/*.java src/semantic/*.java src/Main.java tests/SemanticTests.java
java -cp out SemanticTests
