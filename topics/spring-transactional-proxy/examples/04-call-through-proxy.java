import visual.VisualTxProxy;

public class Playground {
    public static void main(String[] args) {
        VisualTxProxy app = new VisualTxProxy("UserService");

        // The fix for the self-invocation trap: call the @Transactional method
        // through the proxy reference (a controller calling the injected bean,
        // or the bean calling itself via an injected self-reference). Now the
        // proxy intercepts saveUser(), opens a transaction, and commits it.
        app.proxyCall("saveUser", true)
                .persist("user-7", "Alice")
                .returnNormally();

        System.out.println("Called through the proxy: the transaction works.");
    }
}
