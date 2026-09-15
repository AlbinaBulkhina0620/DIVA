print "=== Testing arrays ==="

// Array creation
var empty := []
print empty

var arr1 := [1, 2, 3]
print arr1

var arr2 := ["a", "b", "c"]
print arr2

var mixed := [1, "two", 3.14, true]
print mixed

// Array access
print arr1[1]  // 1
print arr1[2]  // 2
print arr1[3]  // 3

// Array assignment
arr1[2] := 99
print arr1  // [1, 99, 3]

// Dynamic array growth
arr1[10] := 42
print arr1  // [1, 99, 3, none, ..., 42]

// Array concatenation
var a := [1, 2]
var b := [3, 4]
var c := a + b
print c  // [1, 2, 3, 4]

// Nested arrays
var nested := [[1, 2], [3, 4], [5, 6]]
print nested[1][2]  // 4