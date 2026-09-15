package lexer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Lexer {
    private final String source;
    private int pos = 0;    //current position
    private int line = 1;
    private int column = 1;

    private final List<Token> tokens = new ArrayList<>(); //ready tokens
    private final List<String> errors = new ArrayList<>(); //errors

    private static final Map<String, TokenType> KEYWORDS = Map.ofEntries(
            Map.entry("var",    TokenType.VAR),
            Map.entry("if",     TokenType.IF),
            Map.entry("then",   TokenType.THEN),
            Map.entry("else",   TokenType.ELSE),
            Map.entry("end",    TokenType.END),
            Map.entry("while",  TokenType.WHILE),
            Map.entry("for",    TokenType.FOR),
            Map.entry("in",     TokenType.IN),
            Map.entry("loop",   TokenType.LOOP),
            Map.entry("exit",   TokenType.EXIT),
            Map.entry("return", TokenType.RETURN),
            Map.entry("print",  TokenType.PRINT),
            Map.entry("func",   TokenType.FUNC),
            Map.entry("is",     TokenType.IS),
            Map.entry("true",   TokenType.TRUE),
            Map.entry("false",  TokenType.FALSE),
            Map.entry("none",   TokenType.NONE),
            Map.entry("and",    TokenType.AND),
            Map.entry("or",     TokenType.OR),
            Map.entry("xor",    TokenType.XOR),
            Map.entry("not",    TokenType.NOT),
            Map.entry("int",    TokenType.TYPE_INT),
            Map.entry("real",   TokenType.TYPE_REAL),
            Map.entry("bool",   TokenType.TYPE_BOOL),
            Map.entry("string", TokenType.TYPE_STRING)
    );

    public Lexer(String source) {
        this.source = source;
    }

    public List<Token> tokenize() {
        while (!isAtEnd()) {  //обработка символов
            skipWhitespaceAndComments();
            if (isAtEnd()) break;

            char c = peek();

            if (Character.isDigit(c)) {
                number();
            } else if (Character.isLetter(c) || c == '_') {
                identifierOrKeyword();
            } else if (c == '"' || c == '\'') {
                string(c);
            } else {
                symbol();
            }
        }
        tokens.add(new Token(TokenType.EOF, "", null, line, column));
        return tokens;
    }

    private void skipWhitespaceAndComments() {
        while (!isAtEnd()) {
            char c = peek();

            if (c == ' ' || c == '\t' || c == '\r') {
                advance();
            } else if (c == '\n') {
                // NEWLINE token
                tokens.add(new Token(TokenType.NEWLINE, "\\n", null, line, column));
                advance();
            } else if (c == '/' && peekNext() == '/') {
                // inline comments
                while (!isAtEnd() && peek() != '\n') advance();
            } else if (c == '/' && peekNext() == '*') {
                // few lines comments
                advance(); advance();
                while (!isAtEnd() && !(peek() == '*' && peekNext() == '/')) {
                    advance();
                }
                if (!isAtEnd()) {
                    advance(); advance();
                } else {
                    error("Unterminated a few lines comment");
                }
            } else {
                break;
            }
        }
    }


    private void number() {
        int start = pos;
        while (!isAtEnd() && Character.isDigit(peek())) advance();

        boolean isReal = false;
        if (!isAtEnd() && peek() == '.' && Character.isDigit(peekNext())) {
            isReal = true;
            advance();
            while (!isAtEnd() && Character.isDigit(peek())) advance();
        }

        String text = source.substring(start, pos);

        if (isReal) {
            addToken(TokenType.REAL, text, Double.parseDouble(text));
        } else {
            addToken(TokenType.INT, text, Integer.parseInt(text));
        }
    }


    private void identifierOrKeyword() {
        int start = pos;
        while (!isAtEnd() && (Character.isLetterOrDigit(peek()) || peek() == '_')) {
            advance();
        }
        String text = source.substring(start, pos);

        TokenType type = KEYWORDS.getOrDefault(text, TokenType.IDENT);
        addToken(type, text, text);
    }


    private void string(char quote) {
        int startLine = line;
        int startCol = column;
        advance(); // open "
        StringBuilder sb = new StringBuilder();
        while (!isAtEnd() && peek() != quote) {
            if (peek() == '\\') {
                advance();
                if (isAtEnd()) break;
                char esc = advance();
                switch (esc) {
                    case 'n':  sb.append('\n'); break;
                    case 't':  sb.append('\t'); break;
                    case 'r':  sb.append('\r'); break;
                    case '\\': sb.append('\\'); break;
                    case '"':  sb.append('"');  break;
                    case '\'': sb.append('\''); break;
                    default:
                        error("Unknown escape sequence: \\" + esc);
                        sb.append(esc);
                }
            } else {
                sb.append(advance());
            }
        }
        if (isAtEnd()) {
            error("Unterminated string starting at " + startLine + ":" + startCol);
        } else {
            advance(); // close "
        }
        addToken(TokenType.STRING, sb.toString(), sb.toString());
    }

    private void symbol() {
        char c = advance();
        switch (c) {
            case '(': addToken(TokenType.LPAREN, "(", null); break;
            case ')': addToken(TokenType.RPAREN, ")", null); break;
            case '[': addToken(TokenType.LBRACKET, "[", null); break;
            case ']': addToken(TokenType.RBRACKET, "]", null); break;
            case '{': addToken(TokenType.LBRACE, "{", null); break;
            case '}': addToken(TokenType.RBRACE, "}", null); break;
            case ',': addToken(TokenType.COMMA, ",", null); break;
            case ';': addToken(TokenType.SEMICOLON, ";", null); break;

            case '.':
                if (match('.')) addToken(TokenType.RANGE, "..", null);
                else            addToken(TokenType.DOT, ".", null);
                break;

            case ':':
                if (match('=')) addToken(TokenType.ASSIGN, ":=", null);
                else            addToken(TokenType.COLON, ":", null);
                break;

            case '+':
                if (match('+'))      addToken(TokenType.INC, "++", null);
                else if (match('=')) addToken(TokenType.PLUS_ASSIGN, "+=", null);
                else                 addToken(TokenType.PLUS, "+", null);
                break;

            case '-':
                if (match('-'))      addToken(TokenType.DEC, "--", null);
                else if (match('=')) addToken(TokenType.MINUS_ASSIGN, "-=", null);
                else                 addToken(TokenType.MINUS, "-", null);
                break;

            case '*': addToken(TokenType.STAR, "*", null); break;

            case '/':
                if (match('=')) addToken(TokenType.NEQ, "/=", null);
                else            addToken(TokenType.SLASH, "/", null);
                break;

            case '%': addToken(TokenType.PERCENT, "%", null); break;

            case '=':
                if (match('>')) addToken(TokenType.ARROW, "=>", null);
                else            addToken(TokenType.EQ, "=", null);
                break;

            case '<':
                if (match('=')) addToken(TokenType.LE, "<=", null);
                else            addToken(TokenType.LT, "<", null);
                break;

            case '>':
                if (match('=')) addToken(TokenType.GE, ">=", null);
                else            addToken(TokenType.GT, ">", null);
                break;

            case '|':
                if (match('>')) addToken(TokenType.PIPE, "|>", null);
                else            error("Unexpected character '|'");
                break;

            default:
                error("Unexpected character '" + c + "'");
        }
    }




    private boolean isAtEnd() {
        return pos >= source.length();
    }

    //returns the current character but does not advance the position
    private char peek() {
        return source.charAt(pos);
    }

    //returns the next to  current character but does not advance the position
    private char peekNext() {
        return (pos + 1 < source.length()) ? source.charAt(pos + 1) : '\0';
    }

    //line and column update
    private char advance() {
        char c = source.charAt(pos++);
        if (c == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }
        return c;
    }


    private boolean match(char expected) {
        if (!isAtEnd() && source.charAt(pos) == expected) {
            advance();
            return true;
        }
        return false;
    }

    //create new Token and add in tokens
    private void addToken(TokenType type, String lexeme, Object value) {
        tokens.add(new Token(type, lexeme, value, line, column - lexeme.length()));
    }

    private void error(String message) {
        errors.add("Lexer error at " + line + ":" + column + " — " + message);
    }
    public List<String> getErrors() {
        return errors;
    }
}