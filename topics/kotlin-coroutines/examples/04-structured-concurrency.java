import visual.VisualCoroutines;

public class Playground {
    public static void main(String[] args) {
        VisualCoroutines rt = VisualCoroutines.runtime(2, 4);

        // suspend fun loadDashboard() = coroutineScope {
        //     launch { loadUser() }
        //     launch { loadOrders() }
        //     launch { loadPrices() }
        // }
        rt.scope("loadDashboard");
        String user = rt.launchIn("loadDashboard", "IO", "loadUser");
        String orders = rt.launchIn("loadDashboard", "IO", "loadOrders");
        String prices = rt.launchIn("loadDashboard", "IO", "loadPrices");

        rt.suspendAt(user, "httpGet(/user)");
        rt.suspendAt(orders, "httpGet(/orders)");
        rt.suspendAt(prices, "httpGet(/prices)");

        // The closing brace of coroutineScope { } is a suspension point.
        rt.joinScope("loadDashboard");

        rt.resume(user);
        rt.complete(user);
        rt.resume(orders);
        rt.complete(orders);
        rt.resume(prices);
        rt.complete(prices);

        // Only now may loadDashboard() return.
        rt.joinScope("loadDashboard");

        rt.report();
        System.out.println("The function cannot outlive its children, and they cannot outlive it.");
    }
}
