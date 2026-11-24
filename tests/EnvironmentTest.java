import runtime.Environment;

public class EnvironmentTest {
    public static void main(String[] args) {
        Environment global = new Environment();

        global.define("x", 10);
        global.define("name", "Alice");

        System.out.println(global.get("x"));

        global.assign("x", 20);
        System.out.println(global.get("x"));

        Environment local = new Environment(global);
        local.define("y", 30);

        System.out.println(local.get("x"));
        System.out.println(local.get("y"));
    }
}