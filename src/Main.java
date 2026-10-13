import ast.Expr;
import ast.Stmt;
import java.util.List;
import lexer.Lexer;
import lexer.Token;
import parser.Parser;

public class Main {
    public static void main(String[] args) {
        String source = """
            var x := 42
            var flag := true
            
            func add(a: int, b: int): int is
                return a + b
            end
            
            if flag and not false then
                print "OK"
            else
                print "FAIL"
            end
            
            for i in 1..10
                print i
            end
            
            print a.1.2.3
            """;

        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.tokenize();
        
        if (!lexer.getErrors().isEmpty()) {
            System.out.println("Lexer Errors");
            for (String e : lexer.getErrors()) System.out.println(e);
            return;
        }

        Parser parser = new Parser(tokens);
        Stmt.Program program = parser.parse();
        
        if (!parser.getErrors().isEmpty()) {
            System.out.println("Parser encountered errors:");
            for (String e : parser.getErrors()) System.out.println(e);
        } else {
            System.out.println("Parsing successful!");
        }

        System.out.println("\nFull Abstract Syntax Tree");
        ASTPrinter printer = new ASTPrinter();
        printer.print(program);

        System.out.println("Number of top-level statements: " + program.statements.size());
    }

    static class ASTPrinter {
        private int indentLevel = 0;

        private void printIndent() {
            for (int i = 0; i < indentLevel; i++) System.out.print("  ");
        }

        public void print(Stmt stmt) {
            if (stmt == null) { printIndent(); System.out.println("<null>"); return; }
            printIndent();
            
            // Handle the root Program node
            if (stmt instanceof Stmt.Program) {
                System.out.println("(Program");
                indentLevel++;
                for (Stmt s : ((Stmt.Program) stmt).statements) print(s);
                indentLevel--;
                printIndent(); System.out.println(")");
            } else if (stmt instanceof Stmt.VarDeclStmt) {
                Stmt.VarDeclStmt s = (Stmt.VarDeclStmt) stmt;
                System.out.println("(VarDecl " + s.name.lexeme + (s.type != null ? " : " + s.type.lexeme : "") + (s.init != null ? " =" : "") + ")");
                if (s.init != null) { indentLevel++; print(s.init); indentLevel--; }
            } else if (stmt instanceof Stmt.AssignStmt) {
                Stmt.AssignStmt s = (Stmt.AssignStmt) stmt;
                System.out.println("(Assign " + s.op.lexeme + ")");
                indentLevel++; print(s.target); print(s.value); indentLevel--;
            } else if (stmt instanceof Stmt.IfStmt) {
                System.out.println("(If");
                indentLevel++;
                print(((Stmt.IfStmt) stmt).condition);
                print(((Stmt.IfStmt) stmt).thenBranch);
                if (((Stmt.IfStmt) stmt).elseBranch != null) print(((Stmt.IfStmt) stmt).elseBranch);
                indentLevel--;
                printIndent(); System.out.println(")");
            } else if (stmt instanceof Stmt.WhileStmt) {
                System.out.println("(While");
                indentLevel++;
                print(((Stmt.WhileStmt) stmt).condition);
                print(((Stmt.WhileStmt) stmt).body);
                indentLevel--;
                printIndent(); System.out.println(")");
            } else if (stmt instanceof Stmt.ForStmt) {
                Stmt.ForStmt s = (Stmt.ForStmt) stmt;
                System.out.println("(For " + s.var.lexeme);
                indentLevel++;
                print(s.iterable);
                print(s.body);
                indentLevel--;
                printIndent(); System.out.println(")");
            } else if (stmt instanceof Stmt.LoopStmt) {
                System.out.println("(Loop");
                indentLevel++; print(((Stmt.LoopStmt) stmt).body); indentLevel--;
                printIndent(); System.out.println(")");
            } else if (stmt instanceof Stmt.ExitStmt) {
                System.out.println("(Exit)");
                if (((Stmt.ExitStmt) stmt).value != null) { indentLevel++; print(((Stmt.ExitStmt) stmt).value); indentLevel--; }
            } else if (stmt instanceof Stmt.ReturnStmt) {
                System.out.println("(Return)");
                if (((Stmt.ReturnStmt) stmt).value != null) { indentLevel++; print(((Stmt.ReturnStmt) stmt).value); indentLevel--; }
            } else if (stmt instanceof Stmt.PrintStmt) {
                System.out.println("(Print");
                indentLevel++; print(((Stmt.PrintStmt) stmt).value); indentLevel--;
                printIndent(); System.out.println(")");
            } else if (stmt instanceof Stmt.FuncDeclStmt) {
                Stmt.FuncDeclStmt s = (Stmt.FuncDeclStmt) stmt;
                System.out.println("(FuncDecl " + s.name.lexeme);
                indentLevel++; print(s.body); indentLevel--;
                printIndent(); System.out.println(")");
            } else if (stmt instanceof Stmt.ExprStmt) {
                System.out.println("(ExprStmt");
                indentLevel++; print(((Stmt.ExprStmt) stmt).expr); indentLevel--;
                printIndent(); System.out.println(")");
            } else if (stmt instanceof Stmt.BlockStmt) {
                System.out.println("(Block");
                indentLevel++;
                for (Stmt s : ((Stmt.BlockStmt) stmt).statements) print(s);
                indentLevel--;
                printIndent(); System.out.println(")");
            } else {
                System.out.println("(Unknown Stmt)");
            }
        }

        public void print(Expr expr) {
            if (expr == null) { printIndent(); System.out.println("<null>"); return; }
            printIndent();
            
            if (expr instanceof Expr.IntExpr) {
                System.out.println("(Int " + ((Expr.IntExpr) expr).token.lexeme + ")");
            } else if (expr instanceof Expr.RealExpr) {
                System.out.println("(Real " + ((Expr.RealExpr) expr).token.lexeme + ")");
            } else if (expr instanceof Expr.StringExpr) {
                System.out.println("(String \"" + ((Expr.StringExpr) expr).token.value + "\")");
            } else if (expr instanceof Expr.BoolExpr) {
                System.out.println("(Bool " + ((Expr.BoolExpr) expr).value + ")");
            } else if (expr instanceof Expr.NoneExpr) {
                System.out.println("(None)");
            } else if (expr instanceof Expr.IdentExpr) {
                System.out.println("(Ident " + ((Expr.IdentExpr) expr).name.lexeme + ")");
            } else if (expr instanceof Expr.BinaryExpr) {
                Expr.BinaryExpr e = (Expr.BinaryExpr) expr;
                System.out.println("(" + e.op.lexeme);
                indentLevel++; print(e.left); print(e.right); indentLevel--;
                printIndent(); System.out.println(")");
            } else if (expr instanceof Expr.UnaryExpr) {
                Expr.UnaryExpr e = (Expr.UnaryExpr) expr;
                System.out.println("(Unary " + e.op.lexeme);
                indentLevel++; print(e.right); indentLevel--;
                printIndent(); System.out.println(")");
            } else if (expr instanceof Expr.PostfixExpr) {
                Expr.PostfixExpr e = (Expr.PostfixExpr) expr;
                System.out.println("(Postfix " + e.op.lexeme);
                indentLevel++; print(e.expr); indentLevel--;
                printIndent(); System.out.println(")");
            } else if (expr instanceof Expr.CallExpr) {
                Expr.CallExpr e = (Expr.CallExpr) expr;
                System.out.println("(Call");
                indentLevel++; print(e.callee);
                for (Expr arg : e.args) print(arg);
                indentLevel--;
                printIndent(); System.out.println(")");
            } else if (expr instanceof Expr.IndexExpr) {
                Expr.IndexExpr e = (Expr.IndexExpr) expr;
                System.out.println("(Index");
                indentLevel++; print(e.object); print(e.index); indentLevel--;
                printIndent(); System.out.println(")");
            } else if (expr instanceof Expr.MemberExpr) {
                Expr.MemberExpr e = (Expr.MemberExpr) expr;
                System.out.println("(Member ." + e.name.lexeme);
                indentLevel++; print(e.object); indentLevel--;
                printIndent(); System.out.println(")");
            } else if (expr instanceof Expr.LambdaExpr) {
                System.out.println("(Lambda");
                indentLevel++; print(((Expr.LambdaExpr) expr).body); indentLevel--;
                printIndent(); System.out.println(")");
            } else {
                System.out.println("(Unknown Expr)");
            }
        }
    }
}