import visual.VisualHungService;

public class Playground {
    public static void main(String[] args) {
        VisualHungService incident = VisualHungService.alarm(
                "payments-api", "every request times out; the access log stopped 4 minutes ago");

        // The reflex. It works — which is exactly why it is so hard to argue with.
        incident.restartFirst("kubectl rollout restart deploy/payments-api");

        // Twenty minutes later somebody asks the obvious question.
        incident.capture("a thread dump from the hung process", "jcmd <pid> Thread.print");
        incident.capture("a heap histogram from the hung process", "jcmd <pid> GC.class_histogram");

        incident.review();

        System.out.println("Service: restored in 2 minutes.");
        System.out.println("Cause:   unknown, and now unknowable.");
        System.out.println("Forecast: the same outage, tonight, with the same amount of evidence.");
    }
}
