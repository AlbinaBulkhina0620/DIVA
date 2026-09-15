print "=== Testing combined constructs ==="

// FizzBuzz
var fizzbuzz := func(n) is
    var i := 1
    while i <= n loop
        if i % 15 = 0 then
            print "FizzBuzz"
        else if i % 3 = 0 then
            print "Fizz"
        else if i % 5 = 0 then
            print "Buzz"
        else
            print i
        end
        i := i + 1
    end
end

fizzbuzz(15)

// Array processing with functions
var numbers := [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
var even := []
var i := 1
while i <= numbers.length loop
    if numbers[i] % 2 = 0 then
        even := even + [numbers[i]]
    end
    i := i + 1
end
print even  // [2, 4, 6, 8, 10]

// Sum of squares
var squares := []
i := 1
while i <= 5 loop
    squares := squares + [i * i]
    i := i + 1
end
var sum := 0
i := 1
while i <= squares.length loop
    sum := sum + squares[i]
    i := i + 1
end
print sum  // 55