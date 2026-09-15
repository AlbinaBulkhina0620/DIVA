import lexer.Lexer;
import lexer.Token;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        String source = """
                var x := 42 + 3.14
                print "Hello"
                if x > 10 then
                    x += 1
                end
                """;

        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.tokenize();

        for (Token t : tokens) {
            System.out.println(t);
        }

        if (!lexer.getErrors().isEmpty()) {
            System.out.println("\n--- Errors ---");
            for (String e : lexer.getErrors()) {
                System.out.println(e);
            }
        }
    }
}