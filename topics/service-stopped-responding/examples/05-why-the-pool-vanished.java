import visual.VisualHungService;

public class Playground {
    public static void main(String[] args) {
        VisualHungService incident = VisualHungService.alarm(
                "payments-api", "the provider went from 50ms to a hang; we went from fine to dead in seconds");

        incident.pool("the Tomcat worker pool", 200, 200, 1500);

        // Yesterday: the provider answered in 50ms and 200 workers were vastly more than enough.
        incident.capacity(200, 50, 120);

        // Today: the same 200 workers against a call that no longer returns within 8s.
        incident.capacity(200, 8000, 120);

        // The reflex fix, priced before anybody builds it.
        incident.capacity(400, 8000, 120);

        // The actual lever is service time, and a timeout is what puts a ceiling on it.
        incident.capacity(200, 1000, 120);

        System.out.println("Concurrency = arrival rate x service time. Only one of those is yours.");
        System.out.println("Doubling the pool bought 1.6 more seconds and doubled the load on a dying provider.");
        System.out.println("A 1s timeout multiplied capacity by 8 and cost one line of configuration.");
    }
}
