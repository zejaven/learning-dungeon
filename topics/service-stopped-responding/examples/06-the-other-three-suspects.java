import visual.VisualHungService;

public class Playground {
    public static void main(String[] args) {
        // Suspect 1: a JVM that is alive, busy, and collecting garbage instead of serving.
        VisualHungService a = VisualHungService.alarm(
                "reporting-api", "no responses, CPU pinned at 100%, nothing in the error log");
        a.gc(78, 97);
        a.resource("container CPU", "100% of the quota, throttled 4.2s per 10s window", true);
        a.confirm("the heap is full of live objects, so every full GC reclaims almost nothing",
                "78% of wall clock in pauses and a heap still 97% full right after a full collection");

        // Suspect 2: two threads that will wait for each other for ever.
        VisualHungService b = VisualHungService.alarm(
                "orders-api", "no responses, CPU at 2%, memory flat, nothing in the error log");
        b.deadlock("http-nio-8080-exec-12", "inventory-scheduler-1",
                "the order monitor and the inventory monitor, taken in opposite orders");
        b.threads("http-nio-8080-exec", 200, "BLOCKED",
                "waiting to lock the order monitor held by http-nio-8080-exec-12");

        // Suspect 3: the causes that live below your code and never reach a stack trace.
        VisualHungService c = VisualHungService.alarm(
                "media-api", "no responses; the last log line is 6 minutes old");
        c.resource("disk on /var/log", "100% full", true);
        c.resource("open file descriptors", "65530 of 65536", true);
        c.resource("heap", "38% used, GC pauses 1% of wall clock", false);
        c.confirm("the disk is full, so the log write in every request blocks and nothing completes",
                "no exception anywhere — writing the exception is the thing that fails");

        System.out.println("Three services, three silences, three different reads:");
        System.out.println("  CPU at 100% + no responses -> GC log before anything else");
        System.out.println("  CPU at 2%   + no responses -> thread dump, and check its deadlock section");
        System.out.println("  no logs at all             -> look below the JVM: disk, descriptors, the node");
    }
}
