package runtime;

import lexer.Token;

public class RuntimeError extends RuntimeException {
    private final Token token;

    public RuntimeError(Token token, String message) {
        super(message);
        this.token = token;
    }

    public RuntimeError(String message) {
        super(message);
        this.token = null;
    }

    public Token getToken() {
        return token;
    }

    @Override
    public String getMessage() {
        if (token == null) {
            return "Runtime error: " + super.getMessage();
        }

        return "Runtime error at "
                + token.line + ":" + token.column
                + " — " + super.getMessage();
    }
}
