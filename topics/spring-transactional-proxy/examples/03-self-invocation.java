import visual.VisualTxProxy;

public class Playground {
    public static void main(String[] args) {
        VisualTxProxy app = new VisualTxProxy("UserService");

        // register() is NOT @Transactional. It calls this.saveUser() directly.
        // That internal call goes straight to the target object and bypasses the
        // proxy, so the @Transactional on saveUser() is silently ignored: the
        // write runs with no transaction at all. This is the classic trap.
        app.proxyCall("register", false)
                .selfInvoke("saveUser", true)
                .persist("user-7", "Alice")
                .returnNormally();

        System.out.println("Self-invocation bypassed the proxy: no transaction.");
    }
}
