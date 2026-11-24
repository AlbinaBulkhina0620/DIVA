package runtime;

import java.util.HashMap;
import java.util.Map;

public class Environment {
    private final Map<String, Object> values = new HashMap<>();
    private final Environment enclosing;

    public Environment() {
        this.enclosing = null;
    }

    public Environment(Environment enclosing) {
        this.enclosing = enclosing;
    }

    public void define(String name, Object value) {
        if (values.containsKey(name)) {
            throw new RuntimeException(
                    "Variable '" + name + "' is already defined.");
        }
        values.put(name, value);
    }

    public Object get(String name) {
        if (values.containsKey(name)) {
            return values.get(name);
        }
        if (enclosing != null) {
            return enclosing.get(name);
        }
        throw new RuntimeException(
                "Undefined variable '" + name + "'.");
    }

    public void assign(String name, Object value) {
        if (values.containsKey(name)) {
            values.put(name, value);
            return;
        }
        if (enclosing != null) {
            enclosing.assign(name, value);
            return;
        }
        throw new RuntimeException(
                "Undefined variable '" + name + "'.");
    }
}