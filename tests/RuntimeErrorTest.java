import runtime.RuntimeError;

public class RuntimeErrorTest {
    public static void main(String[] args) {
        try {
            throw new RuntimeError("Division by zero.");
        } catch (RuntimeError error) {
            System.out.println(error.getMessage());
        }
    }
}
