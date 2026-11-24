package ast;
import java.util.List;
import lexer.Token;

public interface Stmt {
    class VarDeclStmt implements Stmt { public final Token name; public final Token type; public final Expr init; public VarDeclStmt(Token n, Token t, Expr i) { name=n; type=t; init=i; } }
    class AssignStmt implements Stmt { public final Expr target; public final Token op; public final Expr value; public AssignStmt(Expr t, Token o, Expr v) { target=t; op=o; value=v; } }
    class IfStmt implements Stmt { public final Expr condition; public final Stmt thenBranch; public final Stmt elseBranch; public IfStmt(Expr c, Stmt t, Stmt e) { condition=c; thenBranch=t; elseBranch=e; } }
    class WhileStmt implements Stmt { public final Expr condition; public final Stmt body; public WhileStmt(Expr c, Stmt b) { condition=c; body=b; } }
    class ForStmt implements Stmt { public final Token var; public final Expr iterable; public final Stmt body; public ForStmt(Token v, Expr i, Stmt b) { var=v; iterable=i; body=b; } }
    class LoopStmt implements Stmt { public final Stmt body; public LoopStmt(Stmt b) { body=b; } }
    class ExitStmt implements Stmt { public final Expr value; public ExitStmt(Expr v) { this.value=v; } }
    class ReturnStmt implements Stmt { public final Expr value; public ReturnStmt(Expr v) { this.value=v; } }
    class PrintStmt implements Stmt {
        public final Expr value;
        public final List<Expr> values;
        public PrintStmt(Expr v) { this(List.of(v)); }
        public PrintStmt(List<Expr> v) { values=List.copyOf(v); value=values.get(0); }
    }
    // Several declarations in the current scope, without introducing a block.
    class DeclarationGroup implements Stmt {
        public final List<Stmt> declarations;
        public DeclarationGroup(List<Stmt> declarations) { this.declarations=List.copyOf(declarations); }
    }
    class FuncDeclStmt implements Stmt {
        public final Token name;
        public final List<Token> params;
        public final List<Token> paramTypes;
        public final Token returnType;
        public final Stmt body;
        public FuncDeclStmt(Token n, List<Token> p, Token r, Stmt b) {
            this(n, p, java.util.Collections.nCopies(p.size(), null), r, b);
        }
        public FuncDeclStmt(Token n, List<Token> p, List<Token> types, Token r, Stmt b) {
            name=n; params=p; paramTypes=types; returnType=r; body=b;
        }
    }
    class ExprStmt implements Stmt { public final Expr expr; public ExprStmt(Expr e) { this.expr=e; } }
    class BlockStmt implements Stmt { public final List<Stmt> statements; public BlockStmt(List<Stmt> s) { statements=s; } }
    class Program implements Stmt { public final List<Stmt> statements; public Program(List<Stmt> s) { statements=s; } }
}
