import ast.Stmt;
import lexer.Lexer;
import lexer.Token;
import parser.Parser;
import runtime.Interpreter;
import runtime.RuntimeError;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class InterpreterTest {
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        // Stage 3.2: literals, arithmetic, variables, printing, errors
        test("Integer literal", "print 42", "42");
        test("Real literal", "print 3.14", "3.14");
        test("String literal", "print \"Hello\"", "Hello");
        test("Boolean literal", "print true", "true");
        test("None literal", "print none", "none");
        test("Addition", "print 2 + 3", "5");
        test("Subtraction", "print 10 - 4", "6");
        test("Multiplication", "print 3 * 4", "12");
        test("Integer division", "print 10 / 3", "3");
        test("Remainder", "print 10 % 3", "1");
        test("Operator precedence", "print 2 + 3 * 4", "14");
        test("Parentheses", "print (2 + 3) * 4", "20");
        test("Unary minus", "print -5", "-5");
        test("Real arithmetic", "print 2.5 + 1.5", "4.0");
        test("String concatenation", "print \"Hello\" + \" World\"", "Hello World");
        test("Variable declaration", "var x := 10\nprint x", "10");
        test("Variable assignment", "var x := 10\nx := 20\nprint x", "20");
        test("Dynamic typing", "var x := 10\nx := \"Hello\"\nprint x", "Hello");
        test("Uninitialized variable", "var x\nprint x", "none");
        test("Plus assignment", "var x := 10\nx += 5\nprint x", "15");
        test("Minus assignment", "var x := 10\nx -= 3\nprint x", "7");
        test("Multiple print statements", "print 10\nprint 20\nprint 30", "10\n20\n30");
        test("Variables and arithmetic", "var x := 10\nvar y := 20\nprint x + y\nx := 50\nprint x", "30\n50");
        testRuntimeError("Division by zero", "print 10 / 0", "Division by zero");
        testRuntimeError("Integer overflow", "print 2147483647 + 1", "Integer overflow");
        testRuntimeError("Invalid arithmetic", "print \"hello\" - 5", "Operands must be numeric");

        // Stage 3.3: braces create lexical scopes
        test("Simple block", "{\nvar x := 10\nprint x\n}", "10");
        test("Read outer variable", "var x := 10\n{\nprint x\n}", "10");
        test("Shadow outer variable", "var x := 10\n{\nvar x := 20\nprint x\n}\nprint x", "20\n10");
        test("Assign outer variable", "var x := 10\n{\nx := 30\n}\nprint x", "30");
        test("Nested blocks", "var x := 1\n{\nvar x := 2\n{\nvar x := 3\nprint x\n}\nprint x\n}\nprint x", "3\n2\n1");
        test("Compound assignment to outer", "var x := 5\n{\nx += 2\n}\nprint x", "7");
        testRuntimeError("Block local out of scope", "{\nvar secret := 10\n}\nprint secret", "Undefined variable 'secret'");
        testRuntimeError("Duplicate variable within block", "{\nvar x := 1\nvar x := 2\n}", "already defined");
        testScopeRestoredAfterError();

        // Stage 3.4: boolean and comparison expressions
        test("Numeric comparisons", "print 2 < 3\nprint 3 >= 3\nprint 4 != 5", "true\ntrue\ntrue");
        test("Mixed numeric equality", "print 2 = 2.0\nprint 2 != 3.0", "true\ntrue");
        test("Other equality", "print true = true\nprint none = none\nprint \"a\" != \"b\"", "true\ntrue\ntrue");
        test("Boolean operations", "print true and false\nprint true or false\nprint true xor false\nprint not false", "false\ntrue\ntrue\ntrue");
        test("And short circuit", "print false and (1 / 0 = 1)", "false");
        test("Or short circuit", "print true or (1 / 0 = 1)", "true");
        testRuntimeError("Nonboolean if", "if 1 then\nprint 2\nend", "must be boolean");
        testRuntimeError("Nonboolean logical", "print 1 and true", "must be boolean");

        // Stage 3.4: conditions and loops
        test("If true", "if true then\nprint 10\nend", "10");
        test("If false", "if false then\nprint 10\nend", "");
        test("If else", "if false then\nprint 1\nelse\nprint 2\nend", "2");
        test("If branch scope", "var x := 7\nif true then\nvar x := 9\nprint x\nend\nprint x", "9\n7");
        test("While loop", "var x := 0\nwhile x < 3\nprint x\nx += 1\nend", "0\n1\n2");
        test("While with loop keyword", "var x := 0\nwhile x < 2 loop\nprint x\nx += 1\nend", "0\n1");
        test("While body fresh scope", "var i := 0\nwhile i < 2\nvar tmp := i\nprint tmp\ni += 1\nend", "0\n1");
        test("For ascending inclusive", "for i in 1..3\nprint i\nend", "1\n2\n3");
        test("For descending inclusive", "for i in 3..1\nprint i\nend", "3\n2\n1");
        test("For with loop keyword", "for i in 1..2 loop\nprint i\nend", "1\n2");
        test("For single element", "for i in 4..4\nprint i\nend", "4");
        testRuntimeError("For variable out of scope", "for i in 1..2\nprint i\nend\nprint i", "Undefined variable 'i'");
        test("Loop with exit", "var i := 0\nloop\ni += 1\nif i = 3 then\nexit\nend\nend\nprint i", "3");
        test("While with exit", "var i := 0\nwhile true\ni += 1\nif i = 2 then\nexit\nend\nend\nprint i", "2");
        test("Nested loop exit is local", "for i in 1..2\nfor j in 1..3\nprint i * 10 + j\nexit\nend\nend", "11\n21");
        test("For exit", "for i in 1..5\nprint i\nif i = 2 then\nexit\nend\nend", "1\n2");
        test("Loop exits preserve scope", "var x := 5\nloop\nvar x := 10\nexit\nend\nprint x", "5");
        testRuntimeError("Invalid range endpoints", "for i in 1.5..3\nprint i\nend", "Range endpoints must be integers");

        // Stage 3.6: arrays, tuples, member/index access and mutation.
        test("Empty array", "print []", "[]");
        test("Array literal mixed values", "print [1, true, none, \"hi\"]", "[1, true, none, \"hi\"]");
        test("Array one-based indexing", "var a := [10,20,30]\nprint a[1]\nprint a[3]", "10\n30");
        test("Array length", "var a := [3,4,5]\nprint a.length", "3");
        test("Empty array length", "print [].length", "0");
        test("Nested arrays", "var a := [[1,2],[3,4]]\nprint a[2][1]", "3");
        test("Nested array display", "print [[1], [2,3]]", "[[1], [2, 3]]");
        test("Array concatenation", "print [1,2] + [3,4]", "[1, 2, 3, 4]");
        test("Array concat with empty", "var a := []\na := a + [5]\nprint a", "[5]");
        test("Array assignment", "var a := [1,2]\na[2] := 9\nprint a[2]", "9");
        test("Array compound assignment", "var a := [1,2]\na[1] += 5\nprint a", "[6, 2]");
        test("Array postfix", "var a := [3]\nprint a[1]++\nprint a[1]", "3\n4");
        test("Variable postfix", "var x := 4\nprint x--\nprint x", "4\n3");
        test("Array passed into function", "var f := func(a) => a[1] + a[2]\nprint f([5,6])", "11");
        test("Array mutation in function", "var a := [1]\nvar f := func(xs) is\nxs[1] := 7\nend\nf(a)\nprint a", "[7]");
        test("Named tuple access", "var t := {a := 1, b := 2}\nprint t.a\nprint t.b", "1\n2");
        test("Tuple numeric member", "var t := {a := 9, 4, 7}\nprint t.1\nprint t.2\nprint t.3", "9\n4\n7");
        test("Tuple display", "var t := {a := 1, b := \"two\", 3.3}\nprint t", "{a := 1, b := \"two\", 3.3}");
        test("Nested tuple access", "var t := {inner := {v := 42}}\nprint t.inner.v", "42");
        test("Tuple with none", "var t := {x := none}\nprint t.x", "none");
        testRuntimeError("Zero array index", "print [10][0]", "out of bounds");
        testRuntimeError("Array index too large", "print [10][2]", "out of bounds");
        testRuntimeError("Array noninteger index", "print [10][1.5]", "Index must be an integer");
        testRuntimeError("Indexing nonarray", "print 10[1]", "Indexing requires an array");
        testRuntimeError("Missing tuple field", "print {a := 1}.missing", "Unknown tuple field");
        testRuntimeError("Tuple index zero", "print {a := 1}.0", "out of bounds");
        testRuntimeError("Tuple assignment prohibited", "var t := {a := 1}\nt.a := 3", "tuple members are read-only");
        testRuntimeError("Duplicate tuple field", "print {a := 1, a := 2}", "Duplicate tuple field");
        test("Test-14 array processing", "var numbers := [1,2,3,4,5,6,7,8,9,10]\nvar even := []\nvar i := 1\nwhile i <= numbers.length loop\nif numbers[i] % 2 = 0 then\neven := even + [numbers[i]]\nend\ni := i + 1\nend\nprint even", "[2, 4, 6, 8, 10]");
        test("Test-14 squares total", "var squares := []\nvar i := 1\nwhile i <= 5 loop\nsquares := squares + [i * i]\ni += 1\nend\nvar sum := 0\ni := 1\nwhile i <= squares.length loop\nsum += squares[i]\ni += 1\nend\nprint sum", "55");

        // Stage 3.5: functions, calls, recursion, closures and returns.
        test("Short function", "var add := func(a, b) => a + b\nprint add(2, 3)", "5");
        test("Expression function inline call", "print (func(x) => x * 2)(6)", "12");
        test("No arguments", "var f := func() => 42\nprint f()", "42");
        test("Three arguments", "var sum := func(a,b,c) => a+b+c\nprint sum(1,2,3)", "6");
        test("Block function return", "var f := func(n) is\nreturn n * 3\nend\nprint f(4)", "12");
        test("Block function implicit none", "var f := func() is\nvar x := 1\nend\nprint f()", "none");
        test("Return without value", "var f := func() is\nreturn\nend\nprint f()", "none");
        test("Early return", "var f := func() is\nreturn 7\nprint 99\nend\nprint f()", "7");
        test("Return nested in if", "var f := func(n) is\nif n > 0 then\nreturn 10\nelse\nreturn 20\nend\nend\nprint f(1)\nprint f(0)", "10\n20");
        test("Recursive factorial", "var factorial := func(n) is\nif n <= 1 then\nreturn 1\nelse\nreturn n * factorial(n - 1)\nend\nend\nprint factorial(5)", "120");
        test("Recursive fib", "var fib := func(n) is\nif n <= 1 then\nreturn n\nend\nreturn fib(n-1) + fib(n-2)\nend\nprint fib(7)", "13");
        test("Higher-order function", "var apply := func(f, x) => f(x)\nvar twice := func(x) => x*2\nprint apply(twice, 5)", "10");
        test("Returning closure", "var makeAdder := func(n) is\nreturn func(x) => x + n\nend\nvar add5 := makeAdder(5)\nprint add5(10)", "15");
        test("Closure sees later mutation", "var x := 10\nvar f := func() => x\nx := 20\nprint f()", "20");
        test("Capture definition scope", "var x := 10\nvar f := func() => x\n{\nvar x := 100\nprint f()\n}", "10");
        test("Closure maintains state", "var counter := func() is\nvar n := 0\nreturn func() is\nn += 1\nreturn n\nend\nend\nvar f := counter()\nprint f()\nprint f()", "1\n2");
        test("Lambda syntax", "var f := (x) => x + 1\nprint f(4)", "5");
        test("Named function declaration", "func triple(x) is\nreturn x*3\nend\nprint triple(4)", "12");
        test("Named recursion", "func fact(n) is\nif n <= 1 then\nreturn 1\nend\nreturn n * fact(n-1)\nend\nprint fact(5)", "120");
        test("Function local shadows global", "var x := 2\nvar f := func(x) => x+1\nprint f(10)\nprint x", "11\n2");
        test("Function scope restored after call", "var x := 7\nvar f := func() is\nvar x := 90\nreturn x\nend\nprint f()\nprint x", "90\n7");
        test("Function calls inside loop", "var f := func(x) => x*2\nfor i in 1..3\nprint f(i)\nend", "2\n4\n6");
        test("Function return inside loop", "var f := func() is\nloop\nreturn 7\nend\nend\nprint f()", "7");
        testRuntimeError("Too few arguments", "var f := func(a, b) => a+b\nprint f(1)", "Expected 2 arguments, got 1");
        testRuntimeError("Too many arguments", "var f := func(a) => a\nprint f(1,2)", "Expected 1 arguments, got 2");
        testRuntimeError("Calling non-function", "var x := 10\nprint x(3)", "not a function");
        testRuntimeError("Call after reassign to number", "var f := func() => 2\nf := 3\nprint f()", "not a function");
        testRuntimeError("Duplicate parameter name", "var f := func(x,x) => x\nprint f(1,2)", "already defined");

        test("Original test-13.d program", """
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
            """, "=== Testing functions ===\n5\n120\n10\n15\n6\nHello, World!");

        // Stage 3.7: end-to-end checks run through Main (lex, parse,
        // semantic analysis, optimizer, and interpreter).
        testMain("Main executes arithmetic", "var x := 10\nprint x + 20", "30", false);
        testMain("Main executes recursive function", "var fact := func(n) is\nif n <= 1 then\nreturn 1\nend\nreturn n * fact(n - 1)\nend\nprint fact(5)", "120", false);
        testMain("Main executes array and tuple", "var a := [3, 4]\nvar t := {label := \"ok\"}\nprint a[2]\nprint t.label", "4\nok", false);
        testMain("Main AST debug mode", "print 2 + 3", "Original Abstract Syntax Tree", true);

        System.out.println("\n==========================");
        System.out.println("Passed: " + passed);
        System.out.println("Failed: " + failed);
        System.out.println("==========================");
        if (failed > 0) System.exit(1);
    }

    private static Stmt.Program parse(String source) {
        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.tokenize();
        if (!lexer.getErrors().isEmpty()) throw new AssertionError("Lexer errors: " + lexer.getErrors());
        Parser parser = new Parser(tokens);
        Stmt.Program program = parser.parse();
        if (!parser.getErrors().isEmpty()) throw new AssertionError("Parser errors: " + parser.getErrors());
        return program;
    }

    private static void test(String name, String source, String expected) {
        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            Interpreter interpreter = new Interpreter();
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            interpreter.interpret(parse(source));
            String actual = captured.toString(StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").stripTrailing();
            System.setOut(original);
            if (expected.equals(actual)) {
                pass(name);
            } else {
                fail(name, "Expected: " + expected.replace("\n", "\\n")
                        + " | Actual: " + actual.replace("\n", "\\n"));
            }
        } catch (Throwable error) {
            System.setOut(original);
            fail(name, error.toString());
        } finally {
            System.setOut(original);
        }
    }

    private static void testRuntimeError(String name, String source, String message) {
        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            Stmt.Program program = parse(source);
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            new Interpreter().interpret(program);
            System.setOut(original);
            fail(name, "Expected a runtime error containing: " + message);
        } catch (RuntimeError error) {
            System.setOut(original);
            if (error.getMessage().contains(message)) pass(name);
            else fail(name, "Unexpected error: " + error.getMessage());
        } catch (RuntimeException error) {
            System.setOut(original);
            // Environment.java currently throws RuntimeException for name errors.
            if (error.getMessage() != null && error.getMessage().contains(message)) pass(name);
            else fail(name, "Unexpected error: " + error.getMessage());
        } catch (Throwable error) {
            System.setOut(original);
            fail(name, error.toString());
        } finally {
            System.setOut(original);
        }
    }

    // An error inside a block must not leave the interpreter in the child scope.
    private static void testScopeRestoredAfterError() {
        String name = "Scope restored after runtime error";
        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            Interpreter interpreter = new Interpreter();
            interpreter.interpret(parse("var x := 10"));
            try {
                interpreter.interpret(parse("{\nvar x := 20\nprint 1 / 0\n}"));
                fail(name, "Expected division-by-zero error");
                return;
            } catch (RuntimeError error) {
                if (!error.getMessage().contains("Division by zero")) {
                    fail(name, "Unexpected error: " + error.getMessage());
                    return;
                }
            }
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            interpreter.interpret(parse("print x"));
            System.setOut(original);
            String actual = captured.toString(StandardCharsets.UTF_8).strip();
            if ("10".equals(actual)) pass(name);
            else fail(name, "Expected 10, got " + actual);
        } catch (Throwable error) {
            System.setOut(original);
            fail(name, error.toString());
        } finally {
            System.setOut(original);
        }
    }

    private static void testMain(String name, String source, String expected, boolean astMode) {
        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        java.nio.file.Path file = null;
        try {
            file = java.nio.file.Files.createTempFile("diva-stage37-", ".d");
            java.nio.file.Files.writeString(file, source);
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            if (astMode) Main.main(new String[] {"--ast", file.toString()});
            else Main.main(new String[] {file.toString()});
            System.setOut(original);
            String actual = captured.toString(StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").stripTrailing();
            boolean good = astMode ? actual.contains(expected)
                    && actual.contains("Optimized Abstract Syntax Tree")
                    : actual.equals(expected);
            if (good) pass(name);
            else fail(name, "Expected: " + expected + " | Actual: " + actual);
        } catch (Throwable error) {
            System.setOut(original);
            fail(name, error.toString());
        } finally {
            System.setOut(original);
            if (file != null) {
                try { java.nio.file.Files.deleteIfExists(file); }
                catch (java.io.IOException ignored) {}
            }
        }
    }

    private static void pass(String name) {
        passed++;
        System.out.println("[PASS] " + name);
    }

    private static void fail(String name, String details) {
        failed++;
        System.out.println("[FAIL] " + name);
        System.out.println("  " + details);
    }
}
