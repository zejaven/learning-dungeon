import visual.VisualInjection;

public class Playground {
    public static void main(String[] args) {
        // A lookup endpoint: find the user with this name, if the account is
        // active. The query is built the way almost every first version is
        // built -- with a + between the SQL we wrote and the value somebody
        // else typed.
        VisualInjection app = VisualInjection.app();

        // A normal caller types a normal name. One row comes back.
        app.findByNameConcatenated("alice");

        // The next caller types a quote. Same endpoint, same template, same
        // code path -- but that quote closes the string literal, so the rest
        // of what they typed is read as SQL rather than as a name.
        app.findByNameConcatenated("' OR '1'='1");

        app.report();
        System.out.println("Same code, two inputs, and only one of them stayed a value.");
    }
}
