import visual.VisualTxProxy;

public class Playground {
    public static void main(String[] args) {
        // This object is created with `new UserService()` instead of being
        // taken from the Spring container. The container never wrapped it, so
        // there is no proxy. Calling a @Transactional method on it does nothing
        // transactional: the annotation is just inert metadata.
        VisualTxProxy app = new VisualTxProxy("UserService");

        app.directCall("saveUser", true)
                .persist("user-7", "Alice");

        System.out.println("No Spring bean, no proxy, no transaction.");
    }
}
