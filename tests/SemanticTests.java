import ast.Expr;
import ast.Stmt;
import lexer.Lexer;
import parser.Parser;
import semantic.Optimizer;
import semantic.SemanticAnalyzer;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

/** No dependencies: run with java -cp out SemanticTests. */
public class SemanticTests {
    private static int checks;

    public static void main(String[] args) {
        valid("var x := 1\nx := \"text\"\nx := true\nprint x");
        valid("var x\nprint x\nvar y: int");
        valid("var x := 1\nif true then\nvar x := 2\nprint x\nend\nprint x");
        valid("var x := 2\nvar f := (y) => x + y\nprint f(3)");
        valid("func f(n) is\nreturn f(n)\nend\nprint f(1)");
        valid("loop\nexit\nend\nfor i in 1..3\nprint i\nend");
        valid("func f(x: real): real is\nreturn 1\nend\nprint f(2)");
        valid("var x := 1\nif true then\nx := false\nend\nprint x");
        valid("var x := 1\nfunc change() is\nx := \"s\"\nend\nchange()\nprint x + \"!\"");
        invalid("print missing", "Undeclared name");
        invalid("print x\nvar x := 1", "Undeclared name");
        invalid("var x := x", "Undeclared name");
        invalid("var x\nvar x", "Duplicate declaration");
        invalid("if true then\nvar x := 1\nend\nprint x", "Undeclared name");
        invalid("for i in 1..3\nprint i\nend\nprint i", "Undeclared name");
        invalid("return 1", "Return is only allowed");
        invalid("exit", "Exit is only allowed");
        invalid("loop\nexit 1\nend", "Exit does not take a value");
        invalid("loop\nfunc f() is\nexit\nend\nend", "Exit is only allowed");
        invalid("func f(x, x) is\nreturn x\nend", "Duplicate declaration");
        invalid("var f := (x, x) => x", "Duplicate declaration");
        invalid("func f(x) is\nvar x := 1\nend", "Duplicate declaration");
        invalid("func f() is\nprint absent\nend", "Undeclared name");
        invalid("var f := (x) => absent + x", "Undeclared name");
        invalid("1 := 2", "Assignment target");
        invalid("(1 + 2)++", "Assignment target");
        invalid("var t\nt.a := 1", "tuple members are read-only");
        invalid("func f() is\nreturn 1\nend\nf := 3", "Cannot assign");
        invalid("if 1 then\nprint 2\nend", "If condition");
        invalid("while \"x\"\nexit\nend", "While condition");
        invalid("print 1 + true", "numeric operand");
        invalid("print none + 1", "numeric operand");
        invalid("print not 1", "Operand of not");
        invalid("print true and 2", "Right operand");
        invalid("print \"a\" = \"b\"", "numeric operand");
        invalid("for i in 1..true\nprint i\nend", "Range end");
        invalid("for i in 2\nprint i\nend", "For expects");
        invalid("var a\nprint a[true]", "Array index");
        invalid("print 2[1]", "Indexing requires");
        invalid("var a\nprint a.0", "Tuple indices start");
        invalid("print 2()", "Call target");
        invalid("func f(a: int) is\nreturn a\nend\nf()", "Expected 1 arguments");
        invalid("func f(a: int) is\nreturn a\nend\nf(true)", "Argument 1");
        invalid("print ((x: int) => x)(false)", "Argument 1");
        invalid("func f(): int is\nreturn \"s\"\nend", "Return value");
        invalid("func f(): int is\nreturn\nend", "Return value");
        invalid("var x: int := false", "Initializer");
        invalid("var x: mystery", "Unknown type");
        invalid("if false then\nprint missing\nend", "Undeclared name");
        invalid("func f() is\nreturn 1\nprint missing\nend", "Undeclared name");

        folded("5 + 3 * 2", 11);
        folded("-5 / 2", -3);
        folded("5 / 2", 2);
        folded("5 / 2.0", 2.5);
        folded("5 + 0.5", 5.5);
        folded("not (3 < 5)", false);
        folded("true xor false", true);
        folded("\"Hello\" + \" world\"", "Hello world");
        folded("5 % 2", 1);
        preserved("1 / 0");
        preserved("1 % 0");
        preserved("2147483647 + 1");
        preserved("(-2147483647 - 1) / -1");

        Stmt.Program branch = optimized("var x := 1\nif 3 < 5 then\nvar x := 2\nelse\nprint 0\nend\nprint x");
        expect(branch.statements.get(1) instanceof Stmt.BlockStmt, "selected branch keeps its scope");
        expect(new SemanticAnalyzer().analyze(branch).isEmpty(), "optimized scope remains valid");
        Stmt.Program loop = optimized("while false\nprint 1\nend\nprint 2");
        expect(((Stmt.BlockStmt) loop.statements.get(0)).statements.isEmpty(), "false loop removed");
        Stmt.Program function = optimized("func f() is\nreturn 1 + 2\nprint 99\nend\nprint f()");
        Stmt.BlockStmt body = (Stmt.BlockStmt) ((Stmt.FuncDeclStmt) function.statements.get(0)).body;
        expect(body.statements.size() == 1, "unreachable code after return removed");
        Stmt.Program exits = optimized("loop\nexit\nprint 99\nend\nprint 1");
        expect(((Stmt.BlockStmt) ((Stmt.LoopStmt) exits.statements.get(0)).body).statements.size() == 1,
                "unreachable code after exit removed");
        expect(exits.statements.size() == 2, "exit does not terminate enclosing block");
        Stmt.Program both = optimized("func f(x) is\nif x then\nreturn 1\nelse\nreturn 2\nend\nprint 3\nend");
        expect(((Stmt.BlockStmt) ((Stmt.FuncDeclStmt) both.statements.get(0)).body).statements.size() == 1,
                "both returning branches terminate");
        Stmt.Program one = optimized("func f(x) is\nif x then\nreturn 1\nend\nprint 3\nend");
        expect(((Stmt.BlockStmt) ((Stmt.FuncDeclStmt) one.statements.get(0)).body).statements.size() == 2,
                "one returning branch does not terminate");
        Stmt.Program effects = optimized("func f() is\nprint 1\nreturn 3\nend\nprint 0 * f()");
        expect(((Stmt.PrintStmt) effects.statements.get(1)).value instanceof Expr.BinaryExpr,
                "multiplication by zero retains function call");
        Stmt.Program boolEffects = optimized("func f(): bool is\nprint 1\nreturn true\nend\nprint false and f()");
        expect(((Stmt.PrintStmt) boolEffects.statements.get(1)).value instanceof Expr.BinaryExpr,
                "boolean simplification retains nonconstant operands");
        Stmt.Program original = parse("print 2 + 3\nif true then\nprint 1\nend");
        String before = tree(original);
        SemanticAnalyzer analyzer = new SemanticAnalyzer();
        analyzer.analyze(original);
        expect(before.equals(tree(original)), "checks do not modify AST");
        new Optimizer().optimize(original);
        expect(before.equals(tree(original)), "optimizer does not corrupt original AST");
        analyzer.analyze(parse("print absent"));
        expect(analyzer.analyze(parse("print 1")).isEmpty(), "analyzer can be reused");
        System.out.println("Passed " + checks + " semantic and optimization checks.");
    }

    private static Stmt.Program parse(String source) {
        Lexer lexer = new Lexer(source);
        Parser parser = new Parser(lexer.tokenize());
        Stmt.Program program = parser.parse();
        if (!lexer.getErrors().isEmpty() || !parser.getErrors().isEmpty()) {
            throw new AssertionError("Invalid test source: " + source + "\n" + lexer.getErrors() + parser.getErrors());
        }
        return program;
    }

    private static void valid(String source) {
        List<String> errors = new SemanticAnalyzer().analyze(parse(source));
        expect(errors.isEmpty(), "expected valid program: " + source + "\n" + errors);
    }

    private static void invalid(String source, String message) {
        List<String> errors = new SemanticAnalyzer().analyze(parse(source));
        expect(errors.stream().anyMatch(e -> e.contains(message)), "expected " + message + " in " + errors);
    }

    private static Stmt.Program optimized(String source) {
        Stmt.Program program = parse(source);
        List<String> errors = new SemanticAnalyzer().analyze(program);
        if (!errors.isEmpty()) throw new AssertionError(errors);
        return new Optimizer().optimize(program);
    }

    private static void folded(String source, Object expected) {
        Expr value = ((Stmt.PrintStmt) optimized("print " + source).statements.get(0)).value;
        Object actual = value instanceof Expr.IntExpr e ? e.token.value
                : value instanceof Expr.RealExpr e ? e.token.value
                : value instanceof Expr.StringExpr e ? e.token.value
                : value instanceof Expr.BoolExpr e ? e.value : null;
        expect(expected.equals(actual), source + " should fold to " + expected + ", got " + actual);
    }

    private static void preserved(String source) {
        expect(((Stmt.PrintStmt) optimized("print " + source).statements.get(0)).value instanceof Expr.BinaryExpr,
                source + " must not fold");
    }

    private static String tree(Stmt.Program program) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream previous = System.out;
        try (PrintStream capture = new PrintStream(bytes)) {
            System.setOut(capture);
            new Main.ASTPrinter().print(program);
        } finally { System.setOut(previous); }
        return bytes.toString();
    }

    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
