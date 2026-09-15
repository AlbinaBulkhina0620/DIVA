package lexer;

public enum TokenType {
    //key words
    VAR, IF, THEN, ELSE, END,
    WHILE, FOR, IN, LOOP, EXIT,
    RETURN, PRINT, FUNC, IS,
    TRUE, FALSE, NONE,
    AND, OR, XOR, NOT,

    //types
    TYPE_INT, TYPE_REAL, TYPE_BOOL, TYPE_STRING, TYPE_FUNC,

    //literals
    INT, REAL, STRING, IDENT,

    //operators
    ASSIGN,
    PLUS, MINUS, STAR, SLASH, PERCENT,
    EQ, NEQ,
    LT, GT, LE, GE,
    PIPE,   //  |>
    PLUS_ASSIGN, MINUS_ASSIGN,   // +=  -=
    INC, DEC,      //++ --
    ARROW, // => (lambda)
    RANGE, // ..

    //delimiters
    LPAREN, RPAREN,     // ( )
    LBRACKET, RBRACKET, // [ ]
    LBRACE, RBRACE,     // { }
    COMMA, SEMICOLON,
    DOT, COLON,

    //special symbols
    NEWLINE,
    EOF
}