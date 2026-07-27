import visual.VisualIncidentTriage;

public class Playground {
    public static void main(String[] args) {
        VisualIncidentTriage incident = VisualIncidentTriage.reported(
                "employee-api", "GET /employees", "nothing works");

        incident.clarify("which exact request?", "GET /employees, from the HR web client");

        // First attempt: your request, your account, your department. It works.
        // That is not "there is no bug" — it is "the bug needs something I did not send".
        incident.reproduce("GET /employees?department=engineering as an admin token",
                false, "200 OK, 34 employees");

        // So go and get THEIR request: the traceId, the parameters, the token.
        incident.clarify("which department were they on?", "operations, which is the page that fails");

        // Second attempt, with their parameters. Now the incident is yours.
        incident.reproduce("GET /employees?department=operations as an admin token",
                true, "500 employee.internal_error");

        incident.review();
        System.out.println("Reproduce first. Everything after a reproduction is cheaper.");
    }
}
