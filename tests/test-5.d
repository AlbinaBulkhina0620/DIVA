print "=== Testing comparisons and logical operations ==="

// Integer comparisons
var a := 5
var b := 10

print a < b   // true
print a > b   // false
print a <= b  // true
print a >= b  // false
print a = b   // false
print a /= b  // true

// Real comparisons
var c := 3.14
var d := 3.14

print c = d   // true
print c < d   // false
print c >= d  // true

// Mixed comparisons
print a < 10.5   // true
print 10.5 < b   // false

// Logical operations
print true and true    // true
print true and false   // false
print false or true    // true
print false or false   // false
print true xor true    // false
print true xor false   // true

// Unary not
print not true   // false
print not false  // true