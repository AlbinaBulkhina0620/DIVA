print "=== Testing variable declarations ==="

// Declaration with initialization
var x := 42
print x

// Declaration without initialization (should be none)
var y
print y  // none

// Multiple declarations
var a := 1
var b := 2
var c := 3
print a + b + c  // 6

// Shadowing in nested scope
var outer := "outer"
print outer

if true then
    var outer := "inner"
    print outer  // inner
end

print outer  // outer