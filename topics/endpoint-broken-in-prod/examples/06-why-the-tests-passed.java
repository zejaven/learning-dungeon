import visual.VisualIncidentTriage;

public class Playground {
    public static void main(String[] args) {
        VisualIncidentTriage incident = VisualIncidentTriage.reported(
                "employee-api", "GET /employees", "nothing works");

        incident.reproduce("GET /employees?department=operations", true, "500 employee.internal_error");
        incident.confirm("employee.manager is dereferenced for rows the 02:00 import left with manager_id = NULL",
                "every failing traceId returns a row from that import");

        // The second half of the answer, and the half that is about engineering.
        incident.testGap("every fixture employee had a manager - the fixtures were written by hand, "
                + "by the person who also wrote the mapper");

        // Three kinds of follow-up, and only these three are worth writing down.
        incident.guard("a test for an employee row with manager_id = NULL, taken from the import's own output");
        incident.guard("an alert on the 5xx rate of each endpoint, firing at deploy + 5 minutes");
        incident.guard("a staging database restored weekly from a production snapshot");

        incident.review();
        System.out.println("A green suite never claimed the endpoint works in production.");
    }
}
