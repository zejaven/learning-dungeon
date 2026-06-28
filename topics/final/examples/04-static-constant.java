import visual.VisualFinal;

public class Playground {
    public static void main(String[] args) {
        VisualFinal f = new VisualFinal();

        // `static final` makes a class-level constant: one shared value for the
        // whole class, fixed once. By convention its name is UPPER_SNAKE_CASE.
        f.constant("PI", "double", "3.14159");
        f.constant("MAX_USERS", "int", "100");
    }
}
