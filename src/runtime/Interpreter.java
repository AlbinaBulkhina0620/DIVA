package runtime;

import ast.Expr;
import ast.Stmt;
import lexer.Token;
import lexer.TokenType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.Map;

public class Interpreter {

    private Environment environment = new Environment();

    // Internal control-flow signal, caught only by the nearest loop
    private static final class ExitSignal extends RuntimeException {
        ExitSignal() { super(null, null, false, false); }
    }

    // Return unwinds nested statements to the nearest function call
    private static final class ReturnSignal extends RuntimeException {
        final Object value;
        ReturnSignal(Object value) {
            super(null, null, false, false);
            this.value = value;
        }
    }

    // Internal runtime representation of an inclusive integer range
    private record IntRange(int start, int end) {}

    // Immutable tuple, with one-based positional entries and optional named fields
    private static final class TupleValue {
        final List<Object> values;
        final Map<String, Integer> names;

        TupleValue(List<Object> values, Map<String, Integer> names) {
            this.values = List.copyOf(values.stream().map(x -> x == null ? NoneSentinel.VALUE : x).toList());
            this.names = Map.copyOf(names);
        }

        Object at(int index) {
            Object value = values.get(index);
            return value == NoneSentinel.VALUE ? null : value;
        }
    }

    // List.copyOf doesn't accept null; a tuple may contain the language's none value
    private enum NoneSentinel { VALUE }


    public void interpret(Stmt.Program program) {
        for (Stmt statement : program.statements) {
            execute(statement);
        }
    }

    // Run statements inside a child scope, then always restore the old scope
    private void executeBlock(Stmt.BlockStmt block, Environment newEnvironment) {
        Environment previous = environment;
        try {
            environment = newEnvironment;
            for (Stmt statement : block.statements) {
                execute(statement);
            }
        } finally {
            environment = previous;
        }
    }

    // Called by UserFunction: bind parameters in a fresh lexical environment
    Object callUserFunction(UserFunction function, List<Object> arguments) {
        if (arguments.size() != function.arity()) {
            throw new RuntimeError("Expected " + function.arity()
                    + " arguments, got " + arguments.size() + ".");
        }
        Environment local = new Environment(function.closure);
        for (int i = 0; i < function.params.size(); i++) {
            local.define(function.params.get(i).lexeme, arguments.get(i));
        }
        Environment previous = environment;
        try {
            environment = local;
            if (function.expressionBody != null) {
                return evaluate(function.expressionBody);
            }
            try {
                // Do not add a second scope: params and top-level function locals
                // belong to the same scope (as in SemanticAnalyzer).
                if (function.blockBody instanceof Stmt.BlockStmt block) {
                    for (Stmt statement : block.statements) execute(statement);
                } else if (function.blockBody != null) {
                    execute(function.blockBody);
                }
            } catch (ReturnSignal signal) {
                return signal.value;
            } catch (ExitSignal signal) {
                // A loop exit cannot jump from a called function into a caller's loop
                throw new RuntimeError("Exit is only allowed inside a loop.");
            }
            return null; // No explicit return means 'none'
        } finally {
            environment = previous;
        }
    }

    private void execute(Stmt stmt) {
        if (stmt instanceof Stmt.BlockStmt s) {
            executeBlock(s, new Environment(environment));
            return;
        }

        if (stmt instanceof Stmt.IfStmt s) {
            if (condition(evaluate(s.condition), "if")) {
                execute(s.thenBranch);
            } else if (s.elseBranch != null) {
                execute(s.elseBranch);
            }
            return;
        }

        if (stmt instanceof Stmt.WhileStmt s) {
            while (condition(evaluate(s.condition), "while")) {
                try {
                    execute(s.body);
                } catch (ExitSignal ignored) {
                    break;
                }
            }
            return;
        }

        if (stmt instanceof Stmt.LoopStmt s) {
            while (true) {
                try {
                    execute(s.body);
                } catch (ExitSignal ignored) {
                    break;
                }
            }
            return;
        }


        if (stmt instanceof Stmt.ForStmt s) {
            Object iterable = evaluate(s.iterable);

            // Support integer ranges and arrays
            if (!(iterable instanceof IntRange)
                    && !(iterable instanceof List<?>)) {
                throw new RuntimeError(s.var,
                        "For expects an integer range or an array.");
            }
            Environment previous = environment;
            try {
                environment = new Environment(previous);
                if (iterable instanceof IntRange range) {
                    // for i in 1..5
                    environment.define(s.var.lexeme, range.start());

                    int step = range.start() <= range.end() ? 1 : -1;
                    int i = range.start();

                    while (true) {
                        environment.assign(s.var.lexeme, i);
                        try {
                            execute(s.body);
                        } catch (ExitSignal ignored) {
                            break;
                        }
                        if (i == range.end()) break;
                        i += step;
                    }
                } else if (iterable instanceof List<?> array) {
                    // for val in [1, 2, 3]
                    environment.define(s.var.lexeme, null);
                    for (Object element : array) {
                        environment.assign(s.var.lexeme, element);
                        try {
                            execute(s.body);
                        } catch (ExitSignal ignored) {
                            break;
                        }
                    }
                }
            } finally {
                environment = previous;
            }
            return;
        }

        if (stmt instanceof Stmt.ExitStmt s) {
            if (s.value != null) {
                throw new RuntimeError("Exit does not take a value.");
            }
            throw new ExitSignal();
        }

        if (stmt instanceof Stmt.ReturnStmt s) {
            throw new ReturnSignal(s.value == null ? null : evaluate(s.value));
        }

        if (stmt instanceof Stmt.FuncDeclStmt s) {
            environment.define(s.name.lexeme, new UserFunction(s, environment));
            return;
        }

        if (stmt instanceof Stmt.VarDeclStmt s) {
            Object value = s.init != null ? evaluate(s.init) : null;
            environment.define(s.name.lexeme, value);
            return;
        }


        if (stmt instanceof Stmt.AssignStmt s) {

            if (s.target instanceof Expr.IdentExpr id) {
                Object value = evaluate(s.value);

                if (s.op.type != TokenType.ASSIGN) {
                    value = evaluateBinary(environment.get(id.name.lexeme), assignmentOperator(s.op), value, s.op);
                }
                environment.assign(id.name.lexeme, value);
                return;
            }
            // Array element assignment
            if (s.target instanceof Expr.IndexExpr index) {
                List<Object> array = requireArray(evaluate(index.object), s.op);
                Object indexValue = evaluate(index.index);

                if (!(indexValue instanceof Integer)) {
                    throw new RuntimeError(s.op, "Array index must be an integer.");
                }
                int position = (Integer) indexValue;
                if (position < 1) {
                    throw new RuntimeError(s.op, "Array index must be at least 1.");
                }
                Object value = evaluate(s.value);
                if (s.op.type == TokenType.ASSIGN) {
                    // grow the array if necessary
                    while (array.size() < position) {
                        array.add(null);
                    }
                    array.set(position - 1, value);
                } else {
                    // element must already exist
                    int i = arrayIndex(position, array.size(), s.op);
                    value = evaluateBinary(array.get(i), assignmentOperator(s.op), value, s.op);
                    array.set(i, value);
                }
                return;
            }
            throw new RuntimeError(s.op, "Assignment target must be a variable or array element "
                            + "(tuple members are read-only).");
        }

        if (stmt instanceof Stmt.PrintStmt s) {
            System.out.println(stringify(evaluate(s.value)));
            return;
        }

        if (stmt instanceof Stmt.ExprStmt s) {
            evaluate(s.expr);
            return;
        }
        throw new RuntimeError("Unsupported statement: " + stmt.getClass().getSimpleName());
    }

    public Object evaluate(Expr expr) {
        if (expr instanceof Expr.IntExpr e) return e.token.value;
        if (expr instanceof Expr.RealExpr e) return e.token.value;
        if (expr instanceof Expr.StringExpr e) return e.token.value;
        if (expr instanceof Expr.BoolExpr e) return e.value;
        if (expr instanceof Expr.NoneExpr) return null;

        if (expr instanceof Expr.FuncExpr e) {
            return new UserFunction(e, environment);
        }

        if (expr instanceof Expr.LambdaExpr e) {
            return new UserFunction(e, environment);
        }

        if (expr instanceof Expr.CallExpr e) {
            Object callee = evaluate(e.callee);
            List<Object> args = new ArrayList<>();
            for (Expr arg : e.args) args.add(evaluate(arg));
            if (!(callee instanceof UserFunction function)) {
                throw new RuntimeError("Call target is not a function.");
            }
            return function.call(this, args);
        }

        if (expr instanceof Expr.IdentExpr e) {
            return environment.get(e.name.lexeme);
        }
        if (expr instanceof Expr.ArrayExpr e) {
            List<Object> items = new ArrayList<>();
            for (Expr element : e.elements) items.add(evaluate(element));
            return items;
        }

        if (expr instanceof Expr.TupleExpr e) {
            List<Object> items = new ArrayList<>();
            Map<String, Integer> names = new LinkedHashMap<>();
            for (Expr.TupleExpr.Entry entry : e.entries) {
                if (entry.name != null && names.putIfAbsent(entry.name.lexeme, items.size()) != null) {
                    throw new RuntimeError(entry.name, "Duplicate tuple field '" + entry.name.lexeme + "'.");
                }
                items.add(evaluate(entry.value));
            }
            return new TupleValue(items, names);
        }

        if (expr instanceof Expr.IndexExpr e) {
            Object collection = evaluate(e.object);
            Object index = evaluate(e.index);
            if (collection instanceof List<?> array) {
                return array.get(arrayIndex(index, array.size(), null));
            }
            throw new RuntimeError("Indexing requires an array.");
        }

        if (expr instanceof Expr.MemberExpr e) {
            Object object = evaluate(e.object);
            if (object instanceof List<?> array && "length".equals(e.name.lexeme)) {
                return array.size();
            }
            if (object instanceof TupleValue tuple) {
                if (e.name.type == TokenType.INT) {
                    return tuple.at(arrayIndex(e.name.value, tuple.values.size(), e.name));
                }
                if (e.name.type == TokenType.REAL) {
                    throw new RuntimeError(e.name, "Tuple index must be an integer.");
                }
                Integer index = tuple.names.get(e.name.lexeme);
                if (index == null) throw new RuntimeError(e.name, "Unknown tuple field '" + e.name.lexeme + "'.");
                return tuple.at(index);
            }
            throw new RuntimeError(e.name, "Member access requires a tuple (or array.length).");
        }

        if (expr instanceof Expr.PostfixExpr e) {
            if (e.expr instanceof Expr.IdentExpr id) {
                Object old = environment.get(id.name.lexeme);
                Object updated = increment(old, e.op);
                environment.assign(id.name.lexeme, updated);
                return old;
            }
            if (e.expr instanceof Expr.IndexExpr index) {
                List<Object> array = requireArray(evaluate(index.object), e.op);
                int i = arrayIndex(evaluate(index.index), array.size(), e.op);
                Object old = array.get(i);
                array.set(i, increment(old, e.op));
                return old;
            }
            throw new RuntimeError(e.op, "Postfix target must be a variable or array element.");
        }


        if (expr instanceof Expr.UnaryExpr e) {
            Object right = evaluate(e.right);
            if (e.op.type == TokenType.MINUS) {
                if (right instanceof Integer n) {
                    if (n == Integer.MIN_VALUE) {
                        throw new RuntimeError(e.op, "Integer overflow.");
                    }
                    return -n;
                }
                if (right instanceof Double n) return -n;
                throw new RuntimeError(e.op, "Operand must be numeric.");
            }
            if (e.op.type == TokenType.NOT) {
                if (right instanceof Boolean b) return !b;
                throw new RuntimeError(e.op, "Operand must be boolean.");
            }
            throw new RuntimeError(e.op, "Unsupported unary operator.");
        }

        if (expr instanceof Expr.IsExpr e) {
            Object value = evaluate(e.value);
            return switch (e.typeName.lexeme) {
                case "int" -> value instanceof Integer;
                case "real" -> value instanceof Double;
                case "string" -> value instanceof String;
                case "bool", "boolean" -> value instanceof Boolean;
                case "none" -> value == null;
                case "array" -> value instanceof List<?>;
                case "tuple" -> value instanceof TupleValue;
                case "func" -> value instanceof UserFunction;
                default -> throw new RuntimeError(e.typeName,
                                "Unknown type: " + e.typeName.lexeme);
            };
        }

        if (expr instanceof Expr.BinaryExpr e) {
            Object left = evaluate(e.left);

            // These operators must short-circuit to avoid evaluating unused operands
            if (e.op.type == TokenType.AND) {
                boolean l = condition(left, "and");
                return !l ? false : condition(evaluate(e.right), "and");
            }
            if (e.op.type == TokenType.OR) {
                boolean l = condition(left, "or");
                return l ? true : condition(evaluate(e.right), "or");
            }

            Object right = evaluate(e.right);
            if (e.op.type == TokenType.EQ || e.op.type == TokenType.NEQ) {
                boolean equal = left instanceof Number a && right instanceof Number b
                        ? a.doubleValue() == b.doubleValue()
                        : Objects.equals(left, right);
                return e.op.type == TokenType.EQ ? equal : !equal;
            }
            if (e.op.type == TokenType.XOR) {
                return condition(left, "xor") ^ condition(right, "xor");
            }
            if (e.op.type == TokenType.RANGE) {
                if (!(left instanceof Integer start) || !(right instanceof Integer end)) {
                    throw new RuntimeError(e.op, "Range endpoints must be integers.");
                }
                return new IntRange(start, end);
            }
            return evaluateBinary(left, e.op.type, right, e.op);
        }

        throw new RuntimeError("Unsupported expression: " + expr.getClass().getSimpleName());
    }

    private Object evaluateBinary(Object left, TokenType operator, Object right, Token token) {
        if (operator == TokenType.PLUS && left instanceof String a && right instanceof String b) {
            return a + b;
        }
        if (operator == TokenType.PLUS && left instanceof List<?> a && right instanceof List<?> b) {
            List<Object> joined = new ArrayList<>(a);
            joined.addAll(b);
            return joined;
        }

        if (operator == TokenType.PLUS
                && left instanceof TupleValue a
                && right instanceof TupleValue b) {

            List<Object> values = new ArrayList<>();
            Map<String, Integer> names = new LinkedHashMap<>();

            // Copy values from the first tuple
            for (int i = 0; i < a.values.size(); i++) {
                values.add(a.at(i));
            }
            names.putAll(a.names);
            // Copy values from the second tuple
            int offset = values.size();
            for (int i = 0; i < b.values.size(); i++) {
                values.add(b.at(i));
            }

            // Preserve named fields and adjust their positions
            for (Map.Entry<String, Integer> entry : b.names.entrySet()) {
                if (names.containsKey(entry.getKey())) {
                    throw new RuntimeError(token, "Duplicate tuple field '" + entry.getKey() + "'.");
                }
                names.put(entry.getKey(), offset + entry.getValue());
            }
            return new TupleValue(values, names);
        }

        if (!(left instanceof Number a) || !(right instanceof Number b)) {
            throw new RuntimeError(token, "Operands must be numeric.");
        }

        double x = a.doubleValue();
        double y = b.doubleValue();
        if (left instanceof Integer i && right instanceof Integer j) {
            try {
                return switch (operator) {
                    case PLUS -> Math.addExact(i, j);
                    case MINUS -> Math.subtractExact(i, j);
                    case STAR -> Math.multiplyExact(i, j);
                    case SLASH -> {
                        if (j == 0) throw new RuntimeError(token, "Division by zero.");
                        if (i == Integer.MIN_VALUE && j == -1) {
                            throw new RuntimeError(token, "Integer overflow.");
                        }
                        yield Math.floorDiv(i, j);
                    }
                    case PERCENT -> {
                        if (j == 0) throw new RuntimeError(token, "Division by zero.");
                        yield i % j;
                    }
                    default -> evaluateReal(operator, x, y, token);
                };
            } catch (ArithmeticException ex) {
                throw new RuntimeError(token, "Integer overflow.");
            }
        }
        return evaluateReal(operator, x, y, token);
    }

    private Object evaluateReal(TokenType operator, double x, double y, Token token) {
        if ((operator == TokenType.SLASH || operator == TokenType.PERCENT) && y == 0.0) {
            throw new RuntimeError(token, "Division by zero.");
        }
        Object result = switch (operator) {
            case PLUS -> x + y;
            case MINUS -> x - y;
            case STAR -> x * y;
            case SLASH -> x / y;
            case PERCENT -> x % y;
            case EQ -> x == y;
            case NEQ -> x != y;
            case LT -> x < y;
            case LE -> x <= y;
            case GT -> x > y;
            case GE -> x >= y;
            default -> throw new RuntimeError(token, "Unsupported operator: " + operator);
        };
        if (result instanceof Double d && !Double.isFinite(d)) {
            throw new RuntimeError(token, "Invalid numeric result.");
        }
        return result;
    }

    private boolean condition(Object value, String context) {
        if (value instanceof Boolean b) return b;
        throw new RuntimeError(context + " condition/operand must be boolean.");
    }

    private String stringify(Object value) {
        if (value == null) return "none";
        if (value instanceof Double d && d == 0.0) return "0.0";
        if (value instanceof List<?> items) {
            List<String> parts = new ArrayList<>();
            for (Object item : items) parts.add(collectionValue(item));
            return "[" + String.join(", ", parts) + "]";
        }
        if (value instanceof TupleValue tuple) {
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < tuple.values.size(); i++) {
                String name = null;
                for (Map.Entry<String, Integer> entry : tuple.names.entrySet()) {
                    if (entry.getValue() == i) { name = entry.getKey(); break; }
                }
                parts.add((name == null ? "" : name + " := ") + collectionValue(tuple.at(i)));
            }
            return "{" + String.join(", ", parts) + "}";
        }
        return value.toString();
    }

    private String collectionValue(Object value) {
        if (value instanceof String str) return "\"" + str.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        return stringify(value);
    }

    private List<Object> requireArray(Object object, Token at) {
        if (!(object instanceof List<?> list)) throw new RuntimeError(at, "Index assignment requires an array.");
        @SuppressWarnings("unchecked") List<Object> result = (List<Object>) list;
        return result;
    }

    private int arrayIndex(Object index, int size, Token at) {
        if (!(index instanceof Integer i)) throw new RuntimeError(at, "Index must be an integer.");
        if (i < 1 || i > size) throw new RuntimeError(at, "Index " + i + " out of bounds (1.." + size + ").");
        return i - 1;
    }

    private TokenType assignmentOperator(Token token) {
        return switch (token.type) {
            case PLUS_ASSIGN -> TokenType.PLUS;
            case MINUS_ASSIGN -> TokenType.MINUS;
            default -> throw new RuntimeError(token, "Unsupported assignment operator.");
        };
    }

    private Object increment(Object old, Token token) {
        if (!(old instanceof Number)) throw new RuntimeError(token, "Postfix operand must be numeric.");
        TokenType op = switch (token.type) {
            case INC -> TokenType.PLUS;
            case DEC -> TokenType.MINUS;
            default -> throw new RuntimeError(token, "Unsupported postfix operator.");
        };
        return evaluateBinary(old, op, 1, token);
    }
}
