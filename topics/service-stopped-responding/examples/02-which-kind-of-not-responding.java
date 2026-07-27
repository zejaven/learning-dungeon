import visual.VisualHungService;

public class Playground {
    public static void main(String[] args) {
        // Incident A: probe outside in, one dumb command per layer.
        VisualHungService a = VisualHungService.alarm(
                "payments-api", "clients report timeouts on POST /api/payments");

        a.probe("instances", "kubectl get pods -l app=payments-api", "ok", "5 pods Running, all 5 behaving the same way");
        a.probe("dns", "dig +short payments-api.internal", "ok", "resolves to the service IP");
        a.probe("tcp", "nc -vz payments-api.internal 8080", "ok", "connected in 3ms");
        a.probe("health", "curl -s -o /dev/null -w '%{http_code}' /actuator/health", "ok", "200 UP in 4ms");
        a.probe("endpoint", "curl --max-time 5 /api/payments/42", "timeout", "connected, 0 bytes of response after 5s");
        a.probe("inside", "kubectl exec pod -- curl --max-time 5 localhost:8080/api/payments/42", "timeout", "identical from inside the container");
        a.classify();

        // Incident B: the same sentence from the reporter, a completely different failure.
        VisualHungService b = VisualHungService.alarm(
                "search-api", "clients report timeouts on GET /api/search");

        b.probe("dns", "dig +short search-api.internal", "ok", "resolves to the service IP");
        b.probe("tcp", "nc -vz search-api.internal 8080", "refused", "Connection refused");
        b.classify();

        // Incident C: the luckiest version, because it hands you a control group.
        VisualHungService c = VisualHungService.alarm(
                "orders-api", "roughly half the requests time out, the rest are fine");

        c.probe("instances", "for each pod: curl --max-time 3 localhost:8080/api/orders/1", "partial", "3 pods answer in 20ms, 2 never answer");
        c.classify();

        System.out.println("One sentence from the reporter, three failure modes, zero shared next steps:");
        System.out.println("  payments-api: alive, accepting sockets, answering nothing -> read the threads");
        System.out.println("  search-api:   nothing on the port -> read the exit code and the restart count");
        System.out.println("  orders-api:   2 sick pods next to 3 healthy ones -> diff them before anything else");
    }
}
