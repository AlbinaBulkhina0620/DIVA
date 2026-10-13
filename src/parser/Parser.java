package parser;

import ast.Expr;
import ast.Stmt;
import java.util.ArrayList;
import java.util.List;
import lexer.Token;
import lexer.TokenType;

public class Parser {
    private final List<Token> tokens;
    private int current = 0;
    private final List<String> errors = new ArrayList<>();

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public Stmt.Program parse() {
        List<Stmt> statements = new ArrayList<>();
        skipNewlines();
        while (!isAtEnd()) {
            Stmt stmt = declaration();
            if (stmt != null) statements.add(stmt);
            skipNewlines();
        }
        return new Stmt.Program(statements);
    }

    // -----------------------------------------
    // Declarations & Statements
    // -----------------------------------------

    private Stmt declaration() {
        try {
            if (match(TokenType.VAR)) return varDeclaration();
            if (match(TokenType.FUNC)) return funcDeclaration();
            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private Stmt varDeclaration() {
        Token name = consume(TokenType.IDENT, "Expect variable name.");
        Token type = null;
        if (match(TokenType.COLON)) type = consumeType();
        
        Expr init = null;
        if (match(TokenType.ASSIGN)) init = expression();
        
        consumeEndOfStatement();
        return new Stmt.VarDeclStmt(name, type, init);
    }

    private Stmt funcDeclaration() {
        Token name = consume(TokenType.IDENT, "Expect function name.");
        consume(TokenType.LPAREN, "Expect '(' after function name.");
        
        List<Token> params = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            do {
                params.add(consume(TokenType.IDENT, "Expect parameter name."));
                if (match(TokenType.COLON)) consumeType();
            } while (match(TokenType.COMMA));
        }
        consume(TokenType.RPAREN, "Expect ')' after parameters.");
        
        Token returnType = null;
        if (match(TokenType.COLON)) returnType = consumeType();
        
        consume(TokenType.IS, "Expect 'is' before function body.");
        skipNewlines();
        
        List<Stmt> body = new ArrayList<>();
        while (!check(TokenType.END) && !isAtEnd()) {
            Stmt stmt = declaration();
            if (stmt != null) body.add(stmt);
            skipNewlines();
        }
        consume(TokenType.END, "Expect 'end' after function body.");
        
        return new Stmt.FuncDeclStmt(name, params, returnType, new Stmt.BlockStmt(body));
    }

    private Token consumeType() {
        if (check(TokenType.TYPE_INT) || check(TokenType.TYPE_REAL) || 
            check(TokenType.TYPE_BOOL) || check(TokenType.TYPE_STRING) || 
            check(TokenType.TYPE_FUNC) || check(TokenType.IDENT)) {
            return advance();
        }
        throw error(peek(), "Expect type name.");
    }

    private Stmt statement() {
        if (match(TokenType.IF)) return ifStatement();
        if (match(TokenType.WHILE)) return whileStatement();
        if (match(TokenType.FOR)) return forStatement();
        if (match(TokenType.LOOP)) return loopStatement();
        if (match(TokenType.EXIT)) return exitStatement();
        if (match(TokenType.RETURN)) return returnStatement();
        if (match(TokenType.PRINT)) return printStatement();
        if (match(TokenType.LBRACE)) return blockStatement();
        return expressionStatement();
    }

    private Stmt ifStatement() {
        Expr condition = expression();
        consume(TokenType.THEN, "Expect 'then' after if condition.");
        skipNewlines();
        
        List<Stmt> thenBranch = new ArrayList<>();
        while (!check(TokenType.ELSE) && !check(TokenType.END) && !isAtEnd()) {
            Stmt stmt = declaration();
            if (stmt != null) thenBranch.add(stmt);
            skipNewlines();
        }
        
        List<Stmt> elseBranch = null;
        if (match(TokenType.ELSE)) {
            skipNewlines();
            elseBranch = new ArrayList<>();
            while (!check(TokenType.END) && !isAtEnd()) {
                Stmt stmt = declaration();
                if (stmt != null) elseBranch.add(stmt);
                skipNewlines();
            }
        }
        consume(TokenType.END, "Expect 'end' after if statement.");
        return new Stmt.IfStmt(condition, new Stmt.BlockStmt(thenBranch), elseBranch != null ? new Stmt.BlockStmt(elseBranch) : null);
    }

    private Stmt whileStatement() {
        Expr condition = expression();
        skipNewlines();
        List<Stmt> body = parseBlockUntil(TokenType.END, "while loop");
        return new Stmt.WhileStmt(condition, new Stmt.BlockStmt(body));
    }

    private Stmt forStatement() {
        Token var = consume(TokenType.IDENT, "Expect variable name after 'for'.");
        consume(TokenType.IN, "Expect 'in' after for variable.");
        Expr iterable = expression();
        skipNewlines();
        List<Stmt> body = parseBlockUntil(TokenType.END, "for loop");
        return new Stmt.ForStmt(var, iterable, new Stmt.BlockStmt(body));
    }

    private Stmt loopStatement() {
        skipNewlines();
        List<Stmt> body = parseBlockUntil(TokenType.END, "loop");
        return new Stmt.LoopStmt(new Stmt.BlockStmt(body));
    }

    private Stmt exitStatement() {
        Expr value = (isStatementTerminator()) ? null : expression();
        consumeEndOfStatement();
        return new Stmt.ExitStmt(value);
    }

    private Stmt returnStatement() {
        Expr value = (isStatementTerminator()) ? null : expression();
        consumeEndOfStatement();
        return new Stmt.ReturnStmt(value);
    }

    private Stmt printStatement() {
        Expr value = expression();
        consumeEndOfStatement();
        return new Stmt.PrintStmt(value);
    }

    private Stmt blockStatement() {
        skipNewlines();
        List<Stmt> statements = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            Stmt stmt = declaration();
            if (stmt != null) statements.add(stmt);
            skipNewlines();
        }
        consume(TokenType.RBRACE, "Expect '}' after block.");
        return new Stmt.BlockStmt(statements);
    }

    private Stmt expressionStatement() {
        Expr expr = expression();
        if (check(TokenType.ASSIGN) || check(TokenType.PLUS_ASSIGN) || check(TokenType.MINUS_ASSIGN)) {
            Token op = advance();
            Expr value = expression();
            consumeEndOfStatement();
            return new Stmt.AssignStmt(expr, op, value);
        }
        consumeEndOfStatement();
        return new Stmt.ExprStmt(expr);
    }

    // Helper for parsing blocks ending with a specific token (like END)
    private List<Stmt> parseBlockUntil(TokenType terminator, String blockName) {
        List<Stmt> body = new ArrayList<>();
        while (!check(terminator) && !isAtEnd()) {
            Stmt stmt = declaration();
            if (stmt != null) body.add(stmt);
            skipNewlines();
        }
        consume(terminator, "Expect 'end' after " + blockName + ".");
        return body;
    }

    // -----------------------------------------
    // Expressions
    // -----------------------------------------

    private Expr expression() { return pipe(); }

    private Expr pipe() {
        Expr expr = or();
        while (match(TokenType.PIPE)) expr = new Expr.BinaryExpr(expr, previous(), or());
        return expr;
    }

    private Expr or() {
        Expr expr = xor();
        while (match(TokenType.OR)) expr = new Expr.BinaryExpr(expr, previous(), xor());
        return expr;
    }

    private Expr xor() {
        Expr expr = and();
        while (match(TokenType.XOR)) expr = new Expr.BinaryExpr(expr, previous(), and());
        return expr;
    }

    private Expr and() {
        Expr expr = not();
        while (match(TokenType.AND)) expr = new Expr.BinaryExpr(expr, previous(), not());
        return expr;
    }

    private Expr not() {
        if (match(TokenType.NOT)) return new Expr.UnaryExpr(previous(), not());
        return equality();
    }

    private Expr equality() {
        Expr expr = comparison();
        while (match(TokenType.EQ, TokenType.NEQ)) expr = new Expr.BinaryExpr(expr, previous(), comparison());
        return expr;
    }

    private Expr comparison() {
        Expr expr = range();
        while (match(TokenType.LT, TokenType.GT, TokenType.LE, TokenType.GE)) expr = new Expr.BinaryExpr(expr, previous(), range());
        return expr;
    }

    private Expr range() {
        Expr expr = addition();
        if (match(TokenType.RANGE)) expr = new Expr.BinaryExpr(expr, previous(), addition());
        return expr;
    }

    private Expr addition() {
        Expr expr = multiplication();
        while (match(TokenType.PLUS, TokenType.MINUS)) expr = new Expr.BinaryExpr(expr, previous(), multiplication());
        return expr;
    }

    private Expr multiplication() {
        Expr expr = unary();
        while (match(TokenType.STAR, TokenType.SLASH, TokenType.PERCENT)) expr = new Expr.BinaryExpr(expr, previous(), unary());
        return expr;
    }

    private Expr unary() {
        if (match(TokenType.MINUS, TokenType.NOT)) return new Expr.UnaryExpr(previous(), unary());
        return call();
    }

    private Expr call() {
        Expr expr = primary();
        while (true) {
            if (match(TokenType.LPAREN)) expr = finishCall(expr);
            else if (match(TokenType.LBRACKET)) {
                Expr index = expression();
                consume(TokenType.RBRACKET, "Expect ']' after index.");
                expr = new Expr.IndexExpr(expr, index);
            } else if (match(TokenType.DOT)) {
                // Разрешаем IDENT (для полей структур) или INT/REAL (для индексов кортежей, например a.1.2)
                if (check(TokenType.IDENT) || check(TokenType.INT) || check(TokenType.REAL)) {
                    Token name = advance();
                    expr = new Expr.MemberExpr(expr, name);
                } else {
                    throw error(peek(), "Expect property name or tuple index after '.'.");
                }
            } else if (match(TokenType.INC, TokenType.DEC)) {
                expr = new Expr.PostfixExpr(expr, previous());
            } else break;
        }
        return expr;
    }

    private Expr finishCall(Expr callee) {
        List<Expr> args = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            do { args.add(expression()); } while (match(TokenType.COMMA));
        }
        consume(TokenType.RPAREN, "Expect ')' after arguments.");
        return new Expr.CallExpr(callee, args);
    }

    private Expr primary() {
        if (match(TokenType.INT)) return new Expr.IntExpr(previous());
        if (match(TokenType.REAL)) return new Expr.RealExpr(previous());
        if (match(TokenType.STRING)) return new Expr.StringExpr(previous());
        if (match(TokenType.TRUE)) return new Expr.BoolExpr(previous(), true);
        if (match(TokenType.FALSE)) return new Expr.BoolExpr(previous(), false);
        if (match(TokenType.NONE)) return new Expr.NoneExpr(previous());
        if (match(TokenType.IDENT)) return new Expr.IdentExpr(previous());
        
        if (match(TokenType.LPAREN)) {
            if (isLambda()) return lambda();
            Expr expr = expression();
            consume(TokenType.RPAREN, "Expect ')' after expression.");
            return expr;
        }
        throw error(peek(), "Expect expression.");
    }

    private boolean isLambda() {
        int backtrack = current;
        try {
            // Check for empty params: () =>
            if (match(TokenType.RPAREN)) {
                return check(TokenType.ARROW);
            }
            
            // Check for non-empty params: (x, y) => or (x: int, y: int) =>
            if (!check(TokenType.IDENT)) return false;
            advance();
            if (match(TokenType.COLON)) advance();
            while (match(TokenType.COMMA)) {
                if (!check(TokenType.IDENT)) return false;
                advance();
                if (match(TokenType.COLON)) advance();
            }
            if (!check(TokenType.RPAREN)) return false;
            advance();
            return check(TokenType.ARROW);
        } finally {
            current = backtrack;
        }
    }

    private Expr lambda() {
        List<Token> params = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            do {
                params.add(consume(TokenType.IDENT, "Expect parameter name."));
                if (match(TokenType.COLON)) consumeType();
            } while (match(TokenType.COMMA));
        }
        consume(TokenType.RPAREN, "Expect ')' after lambda parameters.");
        consume(TokenType.ARROW, "Expect '=>' after lambda parameters.");
        return new Expr.LambdaExpr(params, expression());
    }

    // -----------------------------------------
    // Utility and Error Handling
    // -----------------------------------------

    private boolean match(TokenType... types) {
        for (TokenType type : types) if (check(type)) { advance(); return true; }
        return false;
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() { return peek().type == TokenType.EOF; }
    private Token peek() { return tokens.get(current); }
    private Token previous() { return tokens.get(current - 1); }

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();
        throw error(peek(), message);
    }

    private ParseError error(Token token, String message) {
        errors.add("Parse error at " + token.line + ":" + token.column + " — " + message);
        return new ParseError();
    }

    private void synchronize() {
        advance();
        while (!isAtEnd()) {
            if (previous().type == TokenType.NEWLINE || previous().type == TokenType.SEMICOLON) return;
            switch (peek().type) {
                case VAR: case IF: case WHILE: case FOR: case LOOP:
                case EXIT: case RETURN: case PRINT: case FUNC: return;
            }
            advance();
        }
    }

    private void skipNewlines() { while (match(TokenType.NEWLINE)) {} }

    private boolean isStatementTerminator() {
        return check(TokenType.NEWLINE) || check(TokenType.SEMICOLON) || 
               check(TokenType.END) || check(TokenType.ELSE) || isAtEnd();
    }

    private void consumeEndOfStatement() {
        if (match(TokenType.SEMICOLON) || match(TokenType.NEWLINE)) return;
        if (isAtEnd() || check(TokenType.ELSE) || check(TokenType.END) || check(TokenType.RBRACE)) return;
        throw error(peek(), "Expect end of statement (newline or semicolon).");
    }

    public List<String> getErrors() { return errors; }
}