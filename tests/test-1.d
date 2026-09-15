print "=== Testing literals and basic types ==="

// Integer literals
var intVal := 42
print intVal  // 42

// Real literals
var realVal := 3.14
print realVal  // 3.14

// Boolean literals
var boolVal := true
print boolVal  // true

// String literals (single and double quotes)
var str1 := 'Hello'
var str2 := "World"
print str1
print str2

// Special value none
var noneVal := none
print noneVal  // none

// Array literal
var arr := [1, 2, 3, "four", 5.5]
print arr

// Tuple literal
var tuple := {a := 1, b := "two", 3.3}
print tuple

// Function literal
var funcVal := func(x) => x * 2
print funcVal