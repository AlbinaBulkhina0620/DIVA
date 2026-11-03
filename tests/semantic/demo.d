var x := 5 + 3 * 2
print x

if 3 < 5 then
    var x := "inner scope"
    print x
else
    print "unreachable branch"
end
print x

func add(a: int, b: int): int is
    return a + b
    print "unreachable after return"
end
print add(2 + 3, 4)

while false
    print "unreachable loop"
end

loop
    print "once"
    exit
    print "unreachable after exit"
end
print "after loop"

var captured := 10
var plusCaptured := (n) => n + captured
print plusCaptured(5)
