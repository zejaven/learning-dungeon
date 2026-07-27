import visual.VisualHungService;

public class Playground {
    public static void main(String[] args) {
        VisualHungService incident = VisualHungService.alarm(
                "payments-api", "accepting sockets, answering nothing, for the second time this month");

        // The plausible-sounding change, shipped with nothing confirmed.
        incident.fix("double the worker pool from 200 to 400 and add 4 more pods");
        incident.verify("curl --max-time 5 /api/payments/42", false);

        // Stop guessing, restore service with something reversible, then diagnose properly.
        incident.restore("feature-flag the provider call off; queue the charges for later settlement",
                "the checkout path answers again in 30 seconds, minus one non-critical step");

        // Back to the evidence, which had been sitting in the dumps the whole time.
        incident.threads("http-nio-8080-exec", 396, "RUNNABLE",
                "SocketInputStream.socketRead0 <- ProviderClient.charge — now with twice as many victims");
        incident.confirm("ProviderClient is built with no read timeout, so one slow dependency owns every worker",
                "396 of 400 workers on the same frame; the provider's status page confirms a 40-minute outage");

        incident.fix("a 2s read timeout on ProviderClient, its own 20-thread bulkhead, and a circuit breaker "
                + "that fails fast to the 'retry later' path");
        incident.verify("curl --max-time 5 /api/payments/42, then the same call with the provider blackholed", true);

        incident.guard("alert on successful requests per second and p99 latency, not on process liveness");
        incident.guard("readiness touches the provider; liveness does not, so a slow provider drains us "
                + "instead of restarting us");
        incident.guard("a committed capture script: 3 thread dumps, a histogram and the gauges in one command");

        incident.review();

        System.out.println("The bug was somebody else's outage. The defect was ours: no timeout, no bulkhead.");
        System.out.println("Fix the thing that broke AND the mechanism that let it take everything with it.");
    }
}
