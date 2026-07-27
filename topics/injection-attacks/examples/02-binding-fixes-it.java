import visual.VisualInjection;

public class Playground {
    public static void main(String[] args) {
        VisualInjection app = VisualInjection.app();

        // The same two inputs, this time through a statement with a ? in it:
        //   PreparedStatement ps = conn.prepareStatement(
        //       "SELECT id, name, role FROM users WHERE name = ? AND active = TRUE");
        //   ps.setString(1, name);
        //
        // Watch the order of the events. The SQL text reaches the database
        // and is parsed BEFORE the value exists, and the value then arrives
        // through a different part of the protocol. Nothing is escaped and
        // nothing is filtered -- there is simply no moment at which the
        // payload could have become part of the grammar.
        app.findByNameBound("alice");
        app.findByNameBound("' OR '1'='1");

        app.report();
        System.out.println("The payload is now just a name nobody has, so it matches nothing.");
    }
}
