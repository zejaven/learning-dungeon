import visual.VisualIncidentTriage;

public class Playground {
    public static void main(String[] args) {
        VisualIncidentTriage incident = VisualIncidentTriage.reported(
                "employee-api", "GET /employees", "nothing works");

        // Read the boring signals first: each one halves the search space.
        incident.readSignal("5xx rate on GET /employees", "0.1% -> 18%", true);
        incident.readSignal("4xx rate on GET /employees", "flat at 2.1%", false);
        incident.readSignal("p99 latency", "210ms -> 205ms", false);
        incident.readSignal("payroll dependency error rate", "flat at 0.0%", false);

        // The timestamp is evidence too: the release was 15h40m before the first failure.
        incident.readSignal("first failing request", "09:12 today; the release went out at 17:40 yesterday", true);

        // And the signal that does not exist is why a human reported this, a day late.
        incident.missingSignal("an alert on the 5xx rate of this endpoint");

        incident.review();
        System.out.println("The graphs knew before the Product Owner did - if anyone had asked them.");
    }
}
