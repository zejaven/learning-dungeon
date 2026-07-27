import visual.VisualLatencyHunt;

public class Playground {
    public static void main(String[] args) {
        VisualLatencyHunt hunt = VisualLatencyHunt.reported(
                "shop.example.com", "the whole site", "everything got slower this month");

        // The most common real answer to this question is "I could not find out".
        hunt.missingSignal("any per-request timing on the server");
        hunt.missingSignal("a latency graph that goes back further than 24 hours");
        hunt.missingSignal("a request id shared by the gateway, the app and the database log");

        // So the first task is not a fix, it is a measurement - and the cheap
        // layer was there the whole time.
        hunt.measure("everything inside the application", 2900, "the access log, which had the durations all along");
        hunt.measure("everything outside it", 200, "the browser panel minus the access-log duration");
        hunt.split();

        // Meanwhile, the reflex answer to "slow" gets built anyway.
        hunt.guess("double the number of instances");
        hunt.fix("double the number of instances", 0);
        hunt.remeasure(2980);

        hunt.review();
        System.out.println("More instances do nothing for a request that is slow with nobody on the site.");
    }
}
