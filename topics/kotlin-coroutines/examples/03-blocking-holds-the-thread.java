import visual.VisualCoroutines;

public class Playground {
    public static void main(String[] args) {
        // Dispatchers.Default has one thread per core. Here: two.
        VisualCoroutines rt = VisualCoroutines.runtime(2, 4);

        String first = rt.launch("Default", "report-1");
        String second = rt.launch("Default", "report-2");
        String prices = rt.launch("Default", "renderPrices");

        // Neither of these is a suspend function, so neither of them suspends.
        rt.blockingCall(first, "jdbc.executeQuery(...)");
        rt.blockingCall(second, "Thread.sleep(500)");

        rt.complete(first);
        rt.complete(second);
        rt.complete(prices);

        // The fix is not "add threads" — it is "block a thread that is meant for it".
        String third = rt.launch("Default", "report-3");
        rt.withContext(third, "IO", "jdbc.executeQuery(...)");
        rt.blockingCall(third, "jdbc.executeQuery(...)");
        rt.withContext(third, "Default", "render the rows");
        rt.complete(third);

        rt.report();
        System.out.println("Suspending returns the thread. Blocking keeps it, whatever the code looks like.");
    }
}
