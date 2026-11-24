package runtime;

import ast.Expr;
import ast.Stmt;
import lexer.Token;
import lexer.TokenType;
import java.util.ArrayList;
import java.util.List;
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
            // Unlike List.copyOf, this accepts the language's none (null) value.
            this.values = java.util.Collections.unmodifiableList(new ArrayList<>(values));
            this.names = Map.copyOf(names);
        }

        Object at(int index) {
            return values.get(index);
        }
    }


    public void interpret(Stmt.Program program) {
        try {
            for (Stmt statement : program.statements) {
                execute(statement);
            }
        } catch (ExitSignal signal) {
            throw new RuntimeError("Exit is only allowed inside a loop.");
        } catch (ReturnSignal signal) {
            throw new RuntimeError("Return is only allowed inside a function.");
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
        if (stmt instanceof Stmt.DeclarationGroup s) {
            for (Stmt declaration : s.declarations) execute(declaration);
            return;
        }
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
            if (iterable instanceof TupleValue tuple) {
                List<Object> elements = new ArrayList<>();
                for (int i = 0; i < tuple.values.size(); i++) elements.add(tuple.at(i));
                iterable = elements;
            }

            // Support integer ranges and arrays
            if (!(iterable instanceof IntRange)
                    && !(iterable instanceof List<?>)) {
                throw new RuntimeError(s.var,
                        "For expects an integer range, array or tuple.");
            }
            if (iterable instanceof IntRange range) {
                int step = range.start() <= range.end() ? 1 : -1;
                for (int i = range.start(); ; i += step) {
                    if (!executeIteration(s, i) || i == range.end()) break;
                }
            } else if (iterable instanceof List<?> array) {
                // Snapshot the values so mutation during iteration is deterministic.
                for (Object element : new ArrayList<>(array)) {
                    if (!executeIteration(s, element)) break;
                }
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
            UserFunction function = new UserFunction(s, environment.capture());
            environment.define(s.name.lexeme, function);
            function.closure.captureBinding(s.name.lexeme, environment);
            return;
        }

        if (stmt instanceof Stmt.VarDeclStmt s) {
            Object value = s.init != null ? evaluate(s.init) : null;
            environment.define(s.name.lexeme, value);
            if (value instanceof UserFunction function
                    && (s.init instanceof Expr.FuncExpr || s.init instanceof Expr.LambdaExpr)) {
                function.closure.captureBinding(s.name.lexeme, environment);
            }
            return;
        }


        if (stmt instanceof Stmt.AssignStmt s) {

            if (s.target instanceof Expr.IdentExpr id) {
                Object old = s.op.type == TokenType.ASSIGN ? null : environment.get(id.name.lexeme);
                Object value = evaluate(s.value);

                if (s.op.type != TokenType.ASSIGN) {
                    value = evaluateBinary(old, assignmentOperator(s.op), value, s.op);
                }
                environment.assign(id.name.lexeme, value);
                return;
            }
            // Array element assignment
            if (s.target instanceof Expr.IndexExpr index) {
                List<Object> array = requireArray(evaluate(index.object), s.op);
                Object key = evaluate(index.index);
                if (!(key instanceof Integer position)) {
                    throw new RuntimeError(s.op, "Index must be an integer.");
                }
                if (position < 1) {
                    throw new RuntimeError(s.op, "Index " + position + " out of bounds (indices start at 1).");
                }
                Object old = s.op.type == TokenType.ASSIGN ? null
                        : array.get(arrayIndex(position, array.size(), s.op));
                Object value = evaluate(s.value);
                if (s.op.type != TokenType.ASSIGN) {
                    value = evaluateBinary(old, assignmentOperator(s.op), value, s.op);
                } else {
                    while (array.size() < position) array.add(null);
                }
                array.set(position - 1, value);
                return;
            }
            throw new RuntimeError(s.op, "Assignment target must be a variable or array element "
                            + "(tuple members are read-only).");
        }

        if (stmt instanceof Stmt.PrintStmt s) {
            List<String> values = new ArrayList<>();
            for (Expr value : s.values) values.add(stringify(evaluate(value)));
            System.out.println(String.join(" ", values));
            return;
        }

        if (stmt instanceof Stmt.ExprStmt s) {
            evaluate(s.expr);
            return;
        }
        throw new RuntimeError("Unsupported statement: " + stmt.getClass().getSimpleName());
    }

    private boolean executeIteration(Stmt.ForStmt loop, Object value) {
        Environment previous = environment;
        try {
            environment = new Environment(previous);
            if (loop.var != null) environment.define(loop.var.lexeme, value);
            execute(loop.body);
            return true;
        } catch (ExitSignal ignored) {
            return false;
        } finally {
            environment = previous;
        }
    }

    public Object evaluate(Expr expr) {
        if (expr instanceof Expr.IntExpr e) return e.token.value;
        if (expr instanceof Expr.RealExpr e) return e.token.value;
        if (expr instanceof Expr.StringExpr e) return e.token.value;
        if (expr instanceof Expr.BoolExpr e) return e.value;
        if (expr instanceof Expr.NoneExpr) return null;

        if (expr instanceof Expr.FuncExpr e) {
            return new UserFunction(e, environment.capture());
        }

        if (expr instanceof Expr.LambdaExpr e) {
            return new UserFunction(e, environment.capture());
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
                int position = arrayIndex(evaluate(index.index), array.size(), e.op);
                Object old = array.get(position);
                array.set(position, increment(old, e.op));
                return old;
            }
            throw new RuntimeError(e.op, "Postfix target must be a variable or array element.");
        }


        if (expr instanceof Expr.UnaryExpr e) {
            Object right = evaluate(e.right);
            if (e.op.type == TokenType.PLUS) {
                if (right instanceof Number) return right;
                throw new RuntimeError(e.op, "Operand must be numeric.");
            }
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
                if (!(left instanceof Number a) || !(right instanceof Number b)) {
                    throw new RuntimeError(e.op, "Operands must be numeric.");
                }
                boolean equal = a.doubleValue() == b.doubleValue();
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
            if (e.op.type == TokenType.PIPE) {
                if (!(right instanceof UserFunction function)) throw new RuntimeError(e.op, "Pipe target is not a function.");
                List<Object> arguments = new ArrayList<>();
                arguments.add(left);
                return function.call(this, arguments);
            }
            return evaluateBinary(left, e.op.type, right, e.op);
        }

        throw new RuntimeError("Unsupported expression: " + expr.getClass().getSimpleName());
    }

    private Object evaluateBinary(Object left, TokenType operator, Object right, Token token) {
        if (operator == TokenType.PERCENT && (!(left instanceof Integer) || !(right instanceof Integer))) {
            throw new RuntimeError(token, "Remainder operands must be integers.");
        }
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
        return stringify(value, java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
    }

    private String stringify(Object value, java.util.Set<Object> active) {
        if (value == null) return "none";
        if (value instanceof Double d && d == 0.0) return "0.0";
        boolean aggregate = value instanceof List<?> || value instanceof TupleValue;
        if (aggregate && !active.add(value)) return "<cycle>";
        try {
            if (value instanceof List<?> items) {
                List<String> parts = new ArrayList<>();
                for (Object item : items) parts.add(collectionValue(item, active));
                return "[" + String.join(", ", parts) + "]";
            }
            if (value instanceof TupleValue tuple) {
                List<String> parts = new ArrayList<>();
                for (int i = 0; i < tuple.values.size(); i++) {
                    String name = null;
                    for (Map.Entry<String, Integer> entry : tuple.names.entrySet()) {
                        if (entry.getValue() == i) { name = entry.getKey(); break; }
                    }
                    parts.add((name == null ? "" : name + " := ") + collectionValue(tuple.at(i), active));
                }
                return "{" + String.join(", ", parts) + "}";
            }
            return value.toString();
        } finally {
            if (aggregate) active.remove(value);
        }
    }

    private String collectionValue(Object value, java.util.Set<Object> active) {
        if (value instanceof String str) return "\"" + str.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        return stringify(value, active);
    }

    private List<Object> requireArray(Object object, Token at) {
        if (!(object instanceof List<?> array)) throw new RuntimeError(at, "Index assignment requires an array.");
        @SuppressWarnings("unchecked") List<Object> result = (List<Object>) array;
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
