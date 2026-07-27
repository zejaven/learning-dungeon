import visual.VisualInjection;

public class Playground {
    public static void main(String[] args) {
        VisualInjection app = VisualInjection.app();

        // The trap that survives code review:
        //   conn.prepareStatement("SELECT ... WHERE name = '" + name + "' ...")
        // prepareStatement really is called. The + ran first, so the value is
        // already part of the SQL text by the time the driver sees it, and a
        // PreparedStatement with no ? in it has no parameters to keep out.
        app.preparedButConcatenated("' OR '1'='1");

        // The same value through an actual placeholder, for contrast.
        app.findByNameBound("' OR '1'='1");

        app.report();
        System.out.println("The protection is the placeholder, not the class name.");
    }
}
