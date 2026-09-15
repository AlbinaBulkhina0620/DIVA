print "=== Testing tuples ==="

// Tuple creation
var t1 := {a := 1, b := "two", c := 3.14}
print t1

var t2 := {"x", 42, true}
print t2

var t3 := {}
print t3

// Tuple access by name
print t1.a  // 1
print t1.b  // two

// Tuple access by index
print t1.1  // 1
print t1.2  // two

// Mixed named and unnamed
var t4 := {a := 1, 2, b := "three", 4}
print t4

// Tuple concatenation
var t5 := {a := 1, b := 2}
var t6 := {c := 3, d := 4}
var t7 := t5 + t6
print t7  // {a := 1, b := 2, c := 3, d := 4}

// Tuple with array elements
var t8 := {arr := [1, 2, 3], x := 42}
print t8.arr[2]  // 2