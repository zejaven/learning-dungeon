import visual.VisualIncidentTriage;

public class Playground {
    public static void main(String[] args) {
        VisualIncidentTriage incident = VisualIncidentTriage.reported(
                "employee-api", "GET /employees", "nothing works");

        // The reflex that costs the most: answering a question nobody asked.
        incident.dismiss("it passed all the tests, it works on my machine");

        // The same half hour, spent on questions instead. Ask them together.
        incident.clarify("which exact request?", "GET /employees?department=operations");
        incident.clarify("who sees it?", "the HR web client; the engineering department page is fine");
        incident.clarify("what comes back?", "500 with code employee.internal_error, traceId 9f2c41a0b7de");
        incident.clarify("how often?", "every single time, since this morning");

        // The question nobody can answer yet is still worth writing down.
        incident.unanswered("did anything work between the release at 17:40 and 09:12 today?");

        incident.review();
        System.out.println("A report is a claim. Triage starts by turning it into facts.");
    }
}
