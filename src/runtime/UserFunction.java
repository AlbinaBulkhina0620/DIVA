package runtime;

import ast.Expr;
import ast.Stmt;
import lexer.Token;
import java.util.List;

public final class UserFunction {
    final List<Token> params;
    final Expr expressionBody;
    final Stmt blockBody;
    final Environment closure;
    final String name;

    public UserFunction(Expr.FuncExpr expression, Environment closure) {
        this(expression.params, expression.expressionBody, expression.blockBody, closure, null);
    }

    public UserFunction(Expr.LambdaExpr expression, Environment closure) {
        this(expression.params, expression.body, null, closure, null);
    }

    public UserFunction(Stmt.FuncDeclStmt statement, Environment closure) {
        this(statement.params, null, statement.body, closure, statement.name.lexeme);
    }

    private UserFunction(List<Token> params, Expr expressionBody, Stmt blockBody,
                         Environment closure, String name) {
        this.params = List.copyOf(params);
        this.expressionBody = expressionBody;
        this.blockBody = blockBody;
        this.closure = closure;
        this.name = name;
    }

    public int arity() {
        return params.size();
    }

    public Object call(Interpreter interpreter, List<Object> arguments) {
        return interpreter.callUserFunction(this, arguments);
    }

    @Override
    public String toString() {
        return name == null ? "<function>" : "<function " + name + ">";
    }
}
