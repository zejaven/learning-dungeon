import visual.VisualMemory;

public class Playground {
    public static void main(String[] args) {
        VisualMemory mem = new VisualMemory();

        mem.newObject("profile", "Profile", "name=Sam", "visits=1");

        // The callee gets a separate parameter slot, but it contains a copy of
        // the reference to the same heap object.
        mem.call("recordVisit", "profileParam", "Profile", "profile");
        mem.mutateField("profileParam", "visits", "2");

        mem.ret();
    }
}
