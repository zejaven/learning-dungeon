import visual.VisualInjection;

public class Playground {
    public static void main(String[] args) {
        VisualInjection app = VisualInjection.app();

        // A sortable table: the column name arrives in the query string. No
        // database supports "ORDER BY ?" -- a ? stands for a value, and a
        // column name is part of the plan the database needs in order to
        // parse the statement at all. So this really is concatenated.
        app.sortByColumnConcatenated("name");
        app.sortByColumnConcatenated("name; DROP TABLE users --");

        // The fix for the part a placeholder cannot cover: map the input to a
        // value you wrote yourself. Nothing is escaped or repaired -- an
        // unknown column simply never reaches the statement.
        app.sortByColumnAllowlisted("role");
        app.sortByColumnAllowlisted("name; DROP TABLE users --");

        app.report();
        System.out.println("No placeholder for identifiers -- use an allowlist instead.");
    }
}
