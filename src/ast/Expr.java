package ast;
import java.util.List;
import lexer.Token;

public interface Expr {
    class IntExpr implements Expr { public final Token token; public IntExpr(Token t) { this.token = t; } }
    class RealExpr implements Expr { public final Token token; public RealExpr(Token t) { this.token = t; } }
    class StringExpr implements Expr { public final Token token; public StringExpr(Token t) { this.token = t; } }
    class BoolExpr implements Expr { public final Token token; public final boolean value; public BoolExpr(Token t, boolean v) { token=t; value=v; } }
    class NoneExpr implements Expr { public final Token token; public NoneExpr(Token t) { this.token = t; } }
    class IdentExpr implements Expr { public final Token name; public IdentExpr(Token n) { this.name = n; } }
    class BinaryExpr implements Expr { public final Expr left; public final Token op; public final Expr right; public BinaryExpr(Expr l, Token o, Expr r) { left=l; op=o; right=r; } }
    class UnaryExpr implements Expr { public final Token op; public final Expr right; public UnaryExpr(Token o, Expr r) { op=o; right=r; } }
    class PostfixExpr implements Expr { public final Expr expr; public final Token op; public PostfixExpr(Expr e, Token o) { expr=e; op=o; } }
    class CallExpr implements Expr { public final Expr callee; public final List<Expr> args; public CallExpr(Expr c, List<Expr> a) { callee=c; args=a; } }
    class IndexExpr implements Expr { public final Expr object; public final Expr index; public IndexExpr(Expr o, Expr i) { object=o; index=i; } }
    class MemberExpr implements Expr { public final Expr object; public final Token name; public MemberExpr(Expr o, Token n) { object=o; name=n; } }
    class LambdaExpr implements Expr { public final List<Token> params; public final Expr body; public LambdaExpr(List<Token> p, Expr b) { params=p; body=b; } }
}