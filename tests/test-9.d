print "=== Testing loops ==="

// While loop
var i := 1
while i <= 5 loop
    print i
    i := i + 1
end

// For loop with range
for i in 1..5 loop
    print i * i
end

// For loop with array
var arr := [1, 2, 3, 4, 5]
var sum := 0
for val in arr loop
    sum := sum + val
end
print sum  // 15

// Infinite loop with exit
var counter := 0
loop
    print "Iteration"
    counter := counter + 1
    if counter = 3 => exit
end

// Nested loops
for i in 1..3 loop
    for j in 1..3 loop
        print i * j
    end
end