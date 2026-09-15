print "=== Testing edge cases ==="

// Division by zero
// var x := 10 / 0  // Should produce error

// Accessing out-of-bounds array
var arr := [1, 2, 3]
// arr[5]  // Should produce error

// Non-existent tuple element
var t := {a := 1, b := 2}
// t.c  // Should produce error

// Type checking with 'is'
var x := 42
print x is int  // true
print x is real // false

x := 3.14
print x is real // true
print x is int  // false

x := "Hello"
print x is string // true

x := true
print x is boolean // true

x := none
print x is none // true

// Function with wrong number of arguments
var add := func(a, b) => a + b
// add(1)     // Should produce error
// add(1,2,3) // Should produce error

// Comparing different types
print "5" = 5  // Should produce error? (spec says only same types allowed)

// Large numbers
var big := 999999999999
print big

// Negative array index
// arr[-1]  // Should produce error or undefined behavior?

// Empty operations
print [] + []  // []
print {} + {}  // {}