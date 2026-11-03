package semantic;

import ast.Expr;
import ast.Stmt;
import lexer.Token;
import lexer.TokenType;
import java.util.ArrayList;
import java.util.List;

/** Builds a changed AST; run only after successful semantic checks. */
public class Optimizer {
    public Stmt.Program optimize(Stmt.Program program) {
        return new Stmt.Program(statements(program.statements));
    }

    private List<Stmt> statements(List<Stmt> original) {
        List<Stmt> result = new ArrayList<>();
        for (Stmt statement : original) {
            Stmt optimized = statement(statement);
            result.add(optimized);
            if (terminates(optimized)) break;
        }
        return result;
    }

    private Stmt statement(Stmt statement) {
        if (statement == null) return null;
        if (statement instanceof Stmt.BlockStmt s) return new Stmt.BlockStmt(statements(s.statements));
        if (statement instanceof Stmt.VarDeclStmt s) return new Stmt.VarDeclStmt(s.name, s.type, expression(s.init));
        if (statement instanceof Stmt.AssignStmt s) return new Stmt.AssignStmt(expression(s.target), s.op, expression(s.value));
        if (statement instanceof Stmt.PrintStmt s) return new Stmt.PrintStmt(expression(s.value));
        if (statement instanceof Stmt.ExprStmt s) return new Stmt.ExprStmt(expression(s.expr));
        if (statement instanceof Stmt.ReturnStmt s) return new Stmt.ReturnStmt(expression(s.value));
        if (statement instanceof Stmt.ExitStmt s) return new Stmt.ExitStmt(expression(s.value));
        if (statement instanceof Stmt.FuncDeclStmt s) {
            return new Stmt.FuncDeclStmt(s.name, s.params, s.paramTypes, s.returnType, statement(s.body));
        }
        if (statement instanceof Stmt.IfStmt s) {
            Expr condition = expression(s.condition);
            if (condition instanceof Expr.BoolExpr constant) {
                Stmt branch = constant.value ? s.thenBranch : s.elseBranch;
                // Keep the branch block: flattening it would change variable scope.
                return branch == null ? new Stmt.BlockStmt(List.of()) : statement(branch);
            }
            return new Stmt.IfStmt(condition, statement(s.thenBranch), statement(s.elseBranch));
        }
        if (statement instanceof Stmt.WhileStmt s) {
            Expr condition = expression(s.condition);
            if (condition instanceof Expr.BoolExpr constant && !constant.value) {
                return new Stmt.BlockStmt(List.of());
            }
            return new Stmt.WhileStmt(condition, statement(s.body));
        }
        if (statement instanceof Stmt.ForStmt s) return new Stmt.ForStmt(s.var, expression(s.iterable), statement(s.body));
        if (statement instanceof Stmt.LoopStmt s) return new Stmt.LoopStmt(statement(s.body));
        throw new IllegalArgumentException("Unsupported statement: " + statement.getClass());
    }

    private boolean terminates(Stmt statement) {
        if (statement instanceof Stmt.ReturnStmt || statement instanceof Stmt.ExitStmt) return true;
        if (statement instanceof Stmt.BlockStmt block) {
            return !block.statements.isEmpty() && terminates(block.statements.get(block.statements.size() - 1));
        }
        if (statement instanceof Stmt.IfStmt s) {
            return s.elseBranch != null && terminates(s.thenBranch) && terminates(s.elseBranch);
        }
        // An exit inside a loop only exits that loop, not its enclosing block.
        return false;
    }

    private Expr expression(Expr expression) {
        if (expression instanceof Expr.UnaryExpr e) {
            Expr right = expression(e.right);
            Object value = value(right);
            if (e.op.type == TokenType.NOT && value instanceof Boolean b) return literal(!b, e.op);
            if (e.op.type == TokenType.MINUS) {
                if (value instanceof Integer n && n != Integer.MIN_VALUE) return literal(-n, e.op);
                if (value instanceof Double n) return literal(-n, e.op);
            }
            return new Expr.UnaryExpr(e.op, right);
        }
        if (expression instanceof Expr.BinaryExpr e) {
            Expr left = expression(e.left);
            Expr right = expression(e.right);
            Object folded = fold(value(left), e.op.type, value(right));
            return folded == null ? new Expr.BinaryExpr(left, e.op, right) : literal(folded, e.op);
        }
        if (expression instanceof Expr.CallExpr e) {
            List<Expr> args = new ArrayList<>();
            for (Expr arg : e.args) args.add(expression(arg));
            return new Expr.CallExpr(expression(e.callee), args);
        }
        if (expression instanceof Expr.IndexExpr e) return new Expr.IndexExpr(expression(e.object), expression(e.index));
        if (expression instanceof Expr.MemberExpr e) return new Expr.MemberExpr(expression(e.object), e.name);
        if (expression instanceof Expr.PostfixExpr e) return new Expr.PostfixExpr(expression(e.expr), e.op);
        if (expression instanceof Expr.LambdaExpr e) return new Expr.LambdaExpr(e.params, e.paramTypes, expression(e.body));
        return expression;
    }

    private Object value(Expr e) {
        if (e instanceof Expr.IntExpr x) return x.token.value;
        if (e instanceof Expr.RealExpr x) return x.token.value;
        if (e instanceof Expr.StringExpr x) return x.token.value;
        if (e instanceof Expr.BoolExpr x) return x.value;
        return null;
    }

    private Object fold(Object left, TokenType op, Object right) {
        if (left instanceof Boolean a && right instanceof Boolean b) {
            return switch (op) {
                case AND -> a && b;
                case OR -> a || b;
                case XOR -> a ^ b;
                default -> null;
            };
        }
        if (left instanceof String a && right instanceof String b && op == TokenType.PLUS) return a + b;
        if (!(left instanceof Number a) || !(right instanceof Number b)) return null;
        double x = a.doubleValue(), y = b.doubleValue();
        if (!Double.isFinite(x) || !Double.isFinite(y)) return null;
        switch (op) {
            case EQ: return x == y;
            case NEQ: return x != y;
            case LT: return x < y;
            case LE: return x <= y;
            case GT: return x > y;
            case GE: return x >= y;
            default: break;
        }
        // Keep exceptional arithmetic for runtime; never invent an overflow result.
        if ((op == TokenType.SLASH || op == TokenType.PERCENT) && y == 0) return null;
        if (a instanceof Integer && b instanceof Integer) {
            long i = a.longValue(), j = b.longValue();
            Long result = switch (op) {
                case PLUS -> i + j;
                case MINUS -> i - j;
                case STAR -> i * j;
                case SLASH -> Math.floorDiv(i, j); // D specifies rounding down.
                case PERCENT -> i % j;
                default -> null;
            };
            if (result == null || result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) return null;
            return result.intValue();
        }
        Double result = switch (op) {
            case PLUS -> x + y;
            case MINUS -> x - y;
            case STAR -> x * y;
            case SLASH -> x / y;
            default -> null;
        };
        return result != null && Double.isFinite(result) ? result : null;
    }

    private Expr literal(Object value, Token source) {
        TokenType type = value instanceof Integer ? TokenType.INT
                : value instanceof Double ? TokenType.REAL
                : value instanceof String ? TokenType.STRING
                : Boolean.TRUE.equals(value) ? TokenType.TRUE : TokenType.FALSE;
        Token token = new Token(type, String.valueOf(value), value, source.line, source.column);
        return switch (type) {
            case INT -> new Expr.IntExpr(token);
            case REAL -> new Expr.RealExpr(token);
            case STRING -> new Expr.StringExpr(token);
            default -> new Expr.BoolExpr(token, (Boolean) value);
        };
    }
}
