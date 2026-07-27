import visual.VisualIncidentTriage;

public class Playground {
    public static void main(String[] args) {
        // The version that feels fastest and is not: straight to a change on production.
        VisualIncidentTriage guessed = VisualIncidentTriage.reported(
                "employee-api", "GET /employees", "nothing works");
        guessed.fix("wrap the mapper in a try/catch and redeploy");
        guessed.verify("the page the Product Owner had open", true);
        guessed.review();

        // The version that closes the incident: every phase, in order.
        VisualIncidentTriage handled = VisualIncidentTriage.reported(
                "employee-api", "GET /employees", "nothing works");
        handled.clarify("which exact request?", "GET /employees?department=operations, since 09:12");
        handled.reproduce("GET /employees?department=operations", true, "500 employee.internal_error");
        handled.readSignal("5xx rate on GET /employees", "0.1% -> 18%", true);
        handled.scope("GET /employees for 6 of 41 departments", 18, "the HR web client and the payroll sync job");
        handled.mitigate("roll back to the previous release", "5xx rate back to 0.1%");
        handled.confirm("employee.manager is dereferenced for the 1,400 rows the 02:00 import left with "
                        + "manager_id = NULL",
                "every failing traceId returns a row from that import");

        // A fix aimed at where the data came from, not at the request that fails.
        handled.fix("stop the nightly import from creating employees without a manager");
        handled.verify("GET /employees?department=operations", false);

        // The smallest change that makes the failing request succeed.
        handled.fix("render a missing manager as absent in EmployeeView instead of dereferencing it");
        handled.verify("GET /employees?department=operations", true);

        handled.guard("a test for an employee row with manager_id = NULL");
        handled.guard("an alert on the 5xx rate, firing at deploy + 5 minutes");
        handled.review();

        System.out.println("Verified means the request that failed now succeeds, where it failed.");
    }
}
