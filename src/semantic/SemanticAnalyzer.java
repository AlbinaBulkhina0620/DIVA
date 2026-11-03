package semantic;

import ast.Expr;
import ast.Stmt;
import lexer.Token;
import lexer.TokenType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SemanticAnalyzer {
    private enum Type { UNKNOWN, INT, REAL, BOOL, STRING, NONE, FUNCTION, RANGE }

    private static class Symbol {
        final Stmt.FuncDeclStmt function;
        Symbol(Stmt.FuncDeclStmt function) { this.function = function; }
    }

    private final List<String> errors = new ArrayList<>();
    private final Deque<Map<String, Symbol>> scopes = new ArrayDeque<>();
    private int loopDepth;
    private boolean inFunction;
    private Type returnType;

    public List<String> analyze(Stmt.Program program) {
        errors.clear();
        scopes.clear();
        loopDepth = 0;
        inFunction = false;
        returnType = Type.UNKNOWN;
        scopes.push(new HashMap<>());
        for (Stmt statement : program.statements) check(statement);
        scopes.pop();
        return List.copyOf(errors);
    }

    private void declare(Token name, Stmt.FuncDeclStmt function) {
        if (scopes.peek().containsKey(name.lexeme)) {
            error(name, "Duplicate declaration of '" + name.lexeme + "'.");
        } else {
            scopes.peek().put(name.lexeme, new Symbol(function));
        }
    }

    private Symbol lookup(Token name) {
        for (Map<String, Symbol> scope : scopes) {
            if (scope.containsKey(name.lexeme)) return scope.get(name.lexeme);
        }
        error(name, "Undeclared name '" + name.lexeme + "'.");
        return null;
    }

    private void check(Stmt statement) {
        if (statement == null) return;
        if (statement instanceof Stmt.BlockStmt s) {
            scopes.push(new HashMap<>());
            for (Stmt child : s.statements) check(child);
            scopes.pop();
        } else if (statement instanceof Stmt.VarDeclStmt s) {
            Type actual = typeOf(s.init);
            Type declared = annotation(s.type);
            if (s.init != null) compatible(declared, actual, s.name, "Initializer");
            declare(s.name, null);
        } else if (statement instanceof Stmt.AssignStmt s) {
            Type target = writable(s.target, s.op);
            Type value = typeOf(s.value);
            if (s.op.type != TokenType.ASSIGN) {
                binary(s.op.type == TokenType.PLUS_ASSIGN ? TokenType.PLUS : TokenType.MINUS,
                        target, value, s.op);
            }
            // No fixed variable type: x := 1; x := "hello" is legal D.
        } else if (statement instanceof Stmt.IfStmt s) {
            require(typeOf(s.condition), Type.BOOL, location(s.condition), "If condition");
            check(s.thenBranch);
            check(s.elseBranch);
        } else if (statement instanceof Stmt.WhileStmt s) {
            require(typeOf(s.condition), Type.BOOL, location(s.condition), "While condition");
            loopDepth++;
            check(s.body);
            loopDepth--;
        } else if (statement instanceof Stmt.LoopStmt s) {
            loopDepth++;
            check(s.body);
            loopDepth--;
        } else if (statement instanceof Stmt.ForStmt s) {
            Type iterable = typeOf(s.iterable);
            if (iterable != Type.UNKNOWN && iterable != Type.RANGE) {
                error(s.var, "For expects a range or an aggregate.");
            }
            scopes.push(new HashMap<>());
            declare(s.var, null);
            loopDepth++;
            check(s.body);
            loopDepth--;
            scopes.pop();
        } else if (statement instanceof Stmt.ExitStmt s) {
            if (loopDepth == 0) error(location(s.value), "Exit is only allowed inside a loop.");
            if (s.value != null) {
                error(location(s.value), "Exit does not take a value.");
                typeOf(s.value);
            }
        } else if (statement instanceof Stmt.ReturnStmt s) {
            Type actual = typeOf(s.value);
            if (!inFunction) error(location(s.value), "Return is only allowed inside a function.");
            else compatible(returnType, actual, location(s.value), "Return value");
        } else if (statement instanceof Stmt.FuncDeclStmt s) {
            // Named declarations are stable bindings; recursion is visible in the body.
            declare(s.name, s);
            int savedLoops = loopDepth;
            boolean savedFunction = inFunction;
            Type savedReturn = returnType;
            loopDepth = 0;
            inFunction = true;
            returnType = annotation(s.returnType);
            scopes.push(new HashMap<>());
            parameters(s.params, s.paramTypes);
            // Parameters and top-level locals share a function scope.
            if (s.body instanceof Stmt.BlockStmt body) {
                for (Stmt child : body.statements) check(child);
            } else check(s.body);
            scopes.pop();
            loopDepth = savedLoops;
            inFunction = savedFunction;
            returnType = savedReturn;
        } else if (statement instanceof Stmt.PrintStmt s) {
            typeOf(s.value);
        } else if (statement instanceof Stmt.ExprStmt s) {
            typeOf(s.expr);
        }
    }

    private void parameters(List<Token> names, List<Token> types) {
        for (int i = 0; i < names.size(); i++) {
            annotation(types.get(i));
            declare(names.get(i), null);
        }
    }

    private Type typeOf(Expr expression) {
        if (expression == null || expression instanceof Expr.NoneExpr) return Type.NONE;
        if (expression instanceof Expr.IntExpr) return Type.INT;
        if (expression instanceof Expr.RealExpr) return Type.REAL;
        if (expression instanceof Expr.BoolExpr) return Type.BOOL;
        if (expression instanceof Expr.StringExpr) return Type.STRING;
        if (expression instanceof Expr.IdentExpr e) {
            Symbol symbol = lookup(e.name);
            // Do not infer a mutable variable's type from its initializer.
            return symbol != null && symbol.function != null ? Type.FUNCTION : Type.UNKNOWN;
        }
        if (expression instanceof Expr.UnaryExpr e) {
            Type right = typeOf(e.right);
            if (e.op.type == TokenType.NOT) {
                require(right, Type.BOOL, e.op, "Operand of not");
                return Type.BOOL;
            }
            numeric(right, e.op);
            return right;
        }
        if (expression instanceof Expr.BinaryExpr e) {
            Type left = typeOf(e.left);
            Type right = typeOf(e.right);
            return binary(e.op.type, left, right, e.op);
        }
        if (expression instanceof Expr.PostfixExpr e) {
            Type type = writable(e.expr, e.op);
            numeric(type, e.op);
            return type;
        }
        if (expression instanceof Expr.IndexExpr e) {
            Type object = typeOf(e.object);
            require(typeOf(e.index), Type.INT, location(e.index), "Array index");
            if (object != Type.UNKNOWN) error(location(e.object), "Indexing requires an array.");
            return Type.UNKNOWN;
        }
        if (expression instanceof Expr.MemberExpr e) {
            Type object = typeOf(e.object);
            if (object != Type.UNKNOWN) error(e.name, "Member access requires a tuple.");
            if (e.name.type == TokenType.INT && ((Number) e.name.value).intValue() < 1) {
                error(e.name, "Tuple indices start at 1.");
            }
            if (e.name.type == TokenType.REAL) error(e.name, "Tuple index must be an integer.");
            return Type.UNKNOWN;
        }
        if (expression instanceof Expr.LambdaExpr e) {
            scopes.push(new HashMap<>());
            parameters(e.params, e.paramTypes);
            typeOf(e.body);
            scopes.pop();
            return Type.FUNCTION;
        }
        if (expression instanceof Expr.CallExpr e) {
            Type callee = typeOf(e.callee);
            List<Type> args = new ArrayList<>();
            for (Expr arg : e.args) args.add(typeOf(arg));
            require(callee, Type.FUNCTION, location(e.callee), "Call target");
            if (e.callee instanceof Expr.IdentExpr id) {
                // typeOf already reported an undeclared name.
                for (Map<String, Symbol> scope : scopes) {
                    Symbol symbol = scope.get(id.name.lexeme);
                    if (symbol == null) continue;
                    if (symbol.function != null) {
                        Stmt.FuncDeclStmt function = symbol.function;
                        arguments(args, function.paramTypes, id.name);
                        return annotation(function.returnType);
                    }
                    break;
                }
            } else if (e.callee instanceof Expr.LambdaExpr lambda) {
                arguments(args, lambda.paramTypes, location(e.callee));
            }
            return Type.UNKNOWN;
        }
        throw new IllegalArgumentException("Unsupported expression: " + expression.getClass());
    }

    private Type writable(Expr expression, Token at) {
        Type type = typeOf(expression);
        if (expression instanceof Expr.IdentExpr id) {
            for (Map<String, Symbol> scope : scopes) {
                Symbol symbol = scope.get(id.name.lexeme);
                if (symbol == null) continue;
                if (symbol.function != null) error(at, "Cannot assign to a named function declaration.");
                break;
            }
        } else if (!(expression instanceof Expr.IndexExpr)) {
            error(at, "Assignment target must be a variable or array element (tuple members are read-only).");
        }
        return type;
    }

    private Type binary(TokenType op, Type left, Type right, Token at) {
        switch (op) {
            case AND, OR, XOR:
                require(left, Type.BOOL, at, "Left operand");
                require(right, Type.BOOL, at, "Right operand");
                return Type.BOOL;
            case PIPE:
                require(right, Type.FUNCTION, at, "Pipe target");
                return Type.UNKNOWN;
            case RANGE:
                require(left, Type.INT, at, "Range start");
                require(right, Type.INT, at, "Range end");
                return Type.RANGE;
            case PLUS:
                if (left == Type.STRING && right == Type.STRING) return Type.STRING;
                if ((left == Type.UNKNOWN && right == Type.STRING)
                        || (right == Type.UNKNOWN && left == Type.STRING)) return Type.UNKNOWN;
                break;
            default: break;
        }
        numeric(left, at);
        numeric(right, at);
        if (op == TokenType.EQ || op == TokenType.NEQ || op == TokenType.LT
                || op == TokenType.LE || op == TokenType.GT || op == TokenType.GE) return Type.BOOL;
        if (op == TokenType.PERCENT) {
            require(left, Type.INT, at, "Remainder operand");
            require(right, Type.INT, at, "Remainder operand");
        }
        if (left == Type.UNKNOWN || right == Type.UNKNOWN) return Type.UNKNOWN;
        return left == Type.REAL || right == Type.REAL ? Type.REAL : Type.INT;
    }

    private void arguments(List<Type> args, List<Token> types, Token at) {
        if (args.size() != types.size()) error(at, "Expected " + types.size() + " arguments, got " + args.size() + ".");
        for (int i = 0; i < Math.min(args.size(), types.size()); i++) {
            compatible(annotation(types.get(i)), args.get(i), at, "Argument " + (i + 1));
        }
    }

    private Type annotation(Token token) {
        if (token == null) return Type.UNKNOWN;
        return switch (token.type) {
            case TYPE_INT -> Type.INT;
            case TYPE_REAL -> Type.REAL;
            case TYPE_BOOL -> Type.BOOL;
            case TYPE_STRING -> Type.STRING;
            case TYPE_FUNC, FUNC -> Type.FUNCTION;
            default -> { error(token, "Unknown type '" + token.lexeme + "'."); yield Type.UNKNOWN; }
        };
    }

    private void numeric(Type type, Token at) {
        if (type != Type.UNKNOWN && type != Type.INT && type != Type.REAL) {
            error(at, "Expected a numeric operand, got " + type + ".");
        }
    }

    private void require(Type actual, Type expected, Token at, String context) {
        if (actual != Type.UNKNOWN && actual != expected) {
            error(at, context + " must be " + expected + ", got " + actual + ".");
        }
    }

    private void compatible(Type expected, Type actual, Token at, String context) {
        if (expected == Type.UNKNOWN || (expected == Type.REAL && actual == Type.INT)) return;
        require(actual, expected, at, context);
    }

    private void error(Token at, String message) {
        errors.add("Semantic error" + (at == null ? "" : " at " + at.line + ":" + at.column) + ": " + message);
    }

    private Token location(Expr e) {
        if (e instanceof Expr.IdentExpr x) return x.name;
        if (e instanceof Expr.IntExpr x) return x.token;
        if (e instanceof Expr.RealExpr x) return x.token;
        if (e instanceof Expr.StringExpr x) return x.token;
        if (e instanceof Expr.BoolExpr x) return x.token;
        if (e instanceof Expr.NoneExpr x) return x.token;
        if (e instanceof Expr.BinaryExpr x) return x.op;
        if (e instanceof Expr.UnaryExpr x) return x.op;
        if (e instanceof Expr.PostfixExpr x) return x.op;
        if (e instanceof Expr.MemberExpr x) return x.name;
        if (e instanceof Expr.IndexExpr x) return location(x.object);
        if (e instanceof Expr.CallExpr x) return location(x.callee);
        if (e instanceof Expr.LambdaExpr x) return location(x.body);
        return null;
    }
}
