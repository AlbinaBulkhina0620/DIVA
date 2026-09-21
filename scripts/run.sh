#!/bin/bash

javac -d out src/lexer/*.java src/Main.java
java -cp out Main