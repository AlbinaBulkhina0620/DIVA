print "=== Testing if statements ==="

var x := 5

// Simple if
if x > 0 then
    print "x is positive"
end

// if-else
if x < 0 then
    print "x is negative"
else
    print "x is not negative"
end

// Nested if
if x > 0 then
    if x < 10 then
        print "x is between 0 and 10"
    end
end

// Short form (if =>)
if x = 5 => print "x equals 5"

// More complex condition
var a := 10
var b := 20
if a < b and b > 15 then
    print "Condition is true"
end