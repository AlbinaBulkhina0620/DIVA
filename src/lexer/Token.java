package lexer;

public class Token {
    public final TokenType type;    //VAR, INT, PLUS..
    public final String lexeme;     //var, 42, :=..
    public final Object value;
    public final int line;
    public final int column;

    public Token (TokenType type, String lexeme, Object value, int line, int column){
        this.type = type;
        this.lexeme = lexeme;
        this.value = value;
        this.line = line;
        this.column = column;
    }

    // method for printing a token to the console(not effect on lexer)
    @Override
    public String toString() {
        if (value != null) {
            return type + "('" + lexeme + "', value=" + value + ") at " + line + ":" + column;
        }
        return type + "('" + lexeme + "') at " + line + ":" + column;
    }
}