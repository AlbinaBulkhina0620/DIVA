print "=== Testing functions ==="

// Function as literal
var add := func(a, b) => a + b
print add(2, 3)  // 5

// Function with body
var factorial := func(n) is
    if n <= 1 then
        return 1
    else
        return n * factorial(n - 1)
    end
end

print factorial(5)  // 120

// Function as parameter
var apply := func(f, x) => f(x)
var double := func(x) => x * 2
print apply(double, 5)  // 10

// Function returning function
var makeAdder := func(n) is
    return func(x) => x + n
end

var add5 := makeAdder(5)
print add5(10)  // 15

// Multiple parameters
var sum := func(a, b, c) => a + b + c
print sum(1, 2, 3)  // 6

// Function with no parameters
var hello := func() => "Hello, World!"
print hello()  // Hello, World!