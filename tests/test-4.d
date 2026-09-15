print "=== Testing arithmetic operations ==="

// Integer arithmetic
var a := 10 + 5
print a  // 15

a := 10 - 5
print a  // 5

a := 10 * 5
print a  // 50

a := 10 / 3
print a  // 3 (round down)

// Real arithmetic
var b := 10.5 + 3.2
print b  // 13.7

b := 10.5 - 3.2
print b  // 7.3

b := 10.5 * 2.0
print b  // 21.0

b := 10.0 / 3.0
print b  // 3.333...

// Mixed arithmetic
var c := 10 + 3.5
print c  // 13.5

c := 10.5 / 2
print c  // 5.25

// Unary operations
var d := +5
print d  // 5

d := -10
print d  // -10

d := -(-5)
print d  // 5