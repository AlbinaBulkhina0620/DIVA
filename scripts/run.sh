#!/bin/bash
set -eu
cd -- "${BASH_SOURCE[0]%/*}/.."
javac -encoding UTF-8 -d out src/lexer/*.java src/ast/*.java src/parser/*.java src/semantic/*.java src/runtime/*.java src/Main.java
java -cp out Main "$@"
