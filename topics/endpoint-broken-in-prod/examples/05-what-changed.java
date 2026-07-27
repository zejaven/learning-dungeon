import visual.VisualIncidentTriage;

public class Playground {
    public static void main(String[] args) {
        VisualIncidentTriage incident = VisualIncidentTriage.reported(
                "employee-api", "GET /employees", "nothing works");

        incident.reproduce("GET /employees?department=operations", true, "500 employee.internal_error");
        incident.scope("GET /employees for 6 of 41 departments", 18, "the HR web client and the payroll sync job");
        incident.mitigate("roll back to the previous release", "5xx rate back to 0.1%");

        // The question is not "what is wrong with the code" - the code passed.
        // It is "what is different", and the shortlist is short.
        incident.suspect("configuration or a secret", "prod has its own values and nobody diffs them");
        incident.ruleOut("configuration or a secret", "the config map is byte-identical to last week's");

        incident.suspect("the HR web client's own release", "clients ship too, and they ship against you");
        incident.ruleOut("the HR web client's own release", "their bundle hash has not changed in two weeks");

        incident.suspect("the payroll dependency", "a slow or failing neighbour looks like your bug");
        incident.ruleOut("the payroll dependency", "its error rate and p99 are flat across the whole window");

        incident.suspect("yesterday's deploy", "it is the strongest prior, but it does not explain 09:12");
        incident.suspect("the nightly HR import", "it runs at 02:00, and something with its own clock fits");

        // A cause has to explain the timing, not just the error.
        incident.confirm("the release renders employee.manager, and the 02:00 import created 1,400 "
                        + "contractors with manager_id = NULL",
                "every failing traceId returns a row from that import; the engineering page has none");

        incident.review();
        System.out.println("The code did not change overnight. Something around it did.");
    }
}
