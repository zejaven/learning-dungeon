import visual.VisualHungService;

public class Playground {
    public static void main(String[] args) {
        VisualHungService incident = VisualHungService.alarm(
                "payments-api", "accepting sockets, answering nothing; 3 dumps in hand");

        // Do not read 200 stacks. Group them by their top frame and count.
        incident.threads("http-nio-8080-exec", 198, "RUNNABLE",
                "SocketInputStream.socketRead0 <- ProviderClient.charge — identical in all 3 dumps");
        incident.threads("http-nio-8080-exec (idle)", 2, "WAITING",
                "ThreadPoolExecutor.getTask — parked, waiting for work that cannot be handed to them");
        incident.threads("scheduler / background", 6, "TIMED_WAITING",
                "Thread.sleep — healthy, and proof the JVM itself is running fine");

        // Then check every bounded thing a request passes through.
        incident.pool("the Tomcat worker pool", 200, 200, 1500);
        incident.pool("the HikariCP connection pool", 3, 20, 0);

        incident.confirm("the payments provider stopped answering, and our client has no read timeout",
                "198 of 200 workers on the same frame, same host, in all three dumps; the DB pool is 3/20 idle");

        System.out.println("The database was never involved: 3 of 20 connections in use, nobody queueing.");
        System.out.println("The outage is 198 workers holding a socket open against somebody else's server.");
    }
}
