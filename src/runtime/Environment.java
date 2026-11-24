package runtime;

import java.util.HashMap;
import java.util.Map;

public class Environment {
    private static final class Cell {
        Object value;
        Cell(Object value) { this.value = value; }
    }
    private final Map<String, Cell> values = new HashMap<>();
    private final Environment enclosing;

    public Environment() {
        this.enclosing = null;
    }

    public Environment(Environment enclosing) {
        this.enclosing = enclosing;
    }

    public void define(String name, Object value) {
        if (values.containsKey(name)) {
            throw new RuntimeError(
                    "Variable '" + name + "' is already defined.");
        }
        values.put(name, new Cell(value));
    }

    public Object get(String name) {
        if (values.containsKey(name)) {
            return values.get(name).value;
        }
        if (enclosing != null) {
            return enclosing.get(name);
        }
        throw new RuntimeError(
                "Undefined variable '" + name + "'.");
    }

    public void assign(String name, Object value) {
        if (values.containsKey(name)) {
            values.get(name).value = value;
            return;
        }
        if (enclosing != null) {
            enclosing.assign(name, value);
            return;
        }
        throw new RuntimeError(
                "Undefined variable '" + name + "'.");
    }

    // Freeze the visible bindings, while sharing their mutable storage.
    Environment capture() {
        Environment captured = new Environment(enclosing == null ? null : enclosing.capture());
        captured.values.putAll(values);
        return captured;
    }

    void captureBinding(String name, Environment source) {
        values.put(name, source.values.get(name));
    }
}
