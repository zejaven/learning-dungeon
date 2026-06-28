import visual.VisualFinal;

public class Playground {
    public static void main(String[] args) {
        VisualFinal f = new VisualFinal();

        // A final reference to a mutable object, e.g.
        // `final List<String> list = new ArrayList<>();`. The handle is locked.
        f.reference("list", "List", "[a]");

        // final locks the BINDING, not the OBJECT: the list contents can still
        // change, because `add` mutates the object the handle points to.
        f.mutateObject("list", "add(\"b\")", "[a, b]");

        // Re-pointing the handle to a different list does NOT compile, though.
        f.reassignBlocked("list", "new ArrayList<>()");
    }
}
