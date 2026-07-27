import visual.VisualCoroutines;

public class Playground {
    public static void main(String[] args) {
        VisualCoroutines rt = VisualCoroutines.runtime(2, 4);

        // 1. coroutineScope: the children stand or fall together.
        rt.scope("checkout");
        String card = rt.launchIn("checkout", "IO", "chargeCard");
        String receipt = rt.launchIn("checkout", "IO", "sendReceipt");
        rt.suspendAt(receipt, "smtp.send(...)");
        rt.fail(card, "PaymentDeclined");

        // 2. supervisorScope: independent children, so a failure stays local.
        rt.supervisorScope("dashboard");
        String widgetA = rt.launchIn("dashboard", "IO", "weatherWidget");
        String widgetB = rt.launchIn("dashboard", "IO", "newsWidget");
        rt.suspendAt(widgetB, "httpGet(/news)");
        rt.fail(widgetA, "TimeoutException");

        // 3. GlobalScope: no parent at all, so nothing ever cancels it.
        rt.launchGlobal("Default", "pollMetrics");
        rt.scope("screen");
        String screen = rt.launchIn("screen", "IO", "loadScreen");
        rt.suspendAt(screen, "httpGet(/screen)");
        rt.cancel("screen");

        rt.resume(widgetB);
        rt.complete(widgetB);

        rt.report();
        System.out.println("Same launch call three times. The parent Job decided what each one means.");
    }
}
