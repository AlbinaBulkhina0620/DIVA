print "=== Testing dynamic typing ==="

var x := 42
print x  // integer

x := 3.14
print x  // real

x := "Hello"
print x  // string

x := true
print x  // boolean

x := [1, 2, 3]
print x  // array

x := {a := 1, b := 2}
print x  // tuple

x := func(y) => y * y
print x  // function

x := none
print x  // none