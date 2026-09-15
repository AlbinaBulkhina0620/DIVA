print "=== Testing exit and return ==="

// Exit from loop early
var i := 1
while i <= 10 loop
    print i
    if i = 5 => exit
    i := i + 1
end
print "Loop exited"

// Exit from nested loop
for i in 1..10 loop
    for j in 1..10 loop
        print i * j
        if i * j > 20 => exit
    end
end

// Return statement in function
var double := func(x) is
    return x * 2
end

print double(5)  // 10

// Return in short form
var square := func(x) => x * x
print square(5)  // 25