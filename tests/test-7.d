print "=== Testing assignments ==="

var x := 5
print x  // 5

x := 10
print x  // 10

x := x + 5
print x  // 15

// Assignment with different types (dynamic typing)
x := "string"
print x

x := [1, 2, 3]
print x

// Array element assignment
var arr := [1, 2, 3, 4, 5]
arr[3] := 99
print arr  // [1, 2, 99, 4, 5]

// Tuple element assignment (should be impossible - read-only)
var tup := {a := 1, b := 2}
// tup.a := 3  // This should cause an error