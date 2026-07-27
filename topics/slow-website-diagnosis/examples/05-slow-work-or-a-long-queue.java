import visual.VisualLatencyHunt;

public class Playground {
    public static void main(String[] args) {
        VisualLatencyHunt hunt = VisualLatencyHunt.reported(
                "shop.example.com", "the checkout page", "checkout is slow in the evening");

        hunt.clarify("when exactly?", "19:00-22:00 every day; the same click is instant at night");

        hunt.measure("waiting for the first byte (TTFB)", 2900, "the network panel, measured at 20:15");
        hunt.measure("everything the browser does after that", 400, "the network panel");
        hunt.split();

        // The one comparison that decides which half of the world you are in.
        hunt.underLoad(240, 2900);

        // Then walk the resources: how busy, who is waiting in line, what is refusing.
        hunt.resource("CPU across the 3 instances", "34% average, no throttling", false);
        hunt.resource("GC pause time", "0.4% of wall clock", false);
        hunt.resource("the JDBC connection pool", "10/10 in use, 47 waiting, 2.4s average wait", true);
        hunt.resource("the database itself", "18% CPU, no lock waits", false);

        hunt.confirm("a 10-connection pool in front of a database that is 82% idle",
                "the handler needs 240ms once it holds a connection; the other 2.6s is queueing for one");

        hunt.review();
        System.out.println("Nothing here is slow code. Profiling the handler would have shown nothing.");
    }
}
