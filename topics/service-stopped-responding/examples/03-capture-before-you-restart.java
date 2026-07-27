import visual.VisualHungService;

public class Playground {
    public static void main(String[] args) {
        VisualHungService incident = VisualHungService.alarm(
                "payments-api", "sockets accepted, no response written; 5 pods, all silent");

        // Twenty seconds of commands, run while the process is still hung.
        incident.capture("3 thread dumps, 5 seconds apart",
                "jcmd <pid> Thread.print > dump-1.txt (repeat at +5s and +10s)");
        incident.capture("a heap histogram",
                "jcmd <pid> GC.class_histogram | head -40");
        incident.capture("the GC log of the last hour",
                "kubectl cp pod:/var/log/gc.log ./gc.log — it was already being written");
        incident.capture("a snapshot of the pool and queue gauges",
                "curl /actuator/metrics/tomcat.threads.busy and hikaricp.connections.pending");

        // Only now: restore service, and deliberately keep one patient alive.
        incident.restore("restart 4 of the 5 pods; drain the fifth but leave it running",
                "traffic recovers in 40 seconds and one hung process survives for study");

        incident.review();

        System.out.println("Cost of the capture: about 20 seconds.");
        System.out.println("Bought with it: three dumps, a histogram, a GC log and a live hung process.");
    }
}
