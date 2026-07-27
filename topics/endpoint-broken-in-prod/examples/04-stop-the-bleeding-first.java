import visual.VisualIncidentTriage;

public class Playground {
    public static void main(String[] args) {
        // The wrong order: a critical incident, and the first hour spent on hypotheses.
        VisualIncidentTriage slow = VisualIncidentTriage.reported(
                "employee-api", "GET /employees", "nothing works");
        slow.scope("GET /employees for 6 of 41 departments", 18, "the HR web client and the payroll sync job");
        slow.suspect("yesterday's deploy", "the timing lines up");
        slow.suspect("the nightly HR import", "it runs at 02:00 and writes employees");
        slow.review();

        // The right order: the same measurement, then the rollback, then the thinking.
        VisualIncidentTriage fast = VisualIncidentTriage.reported(
                "employee-api", "GET /employees", "nothing works");
        fast.scope("GET /employees for 6 of 41 departments", 18, "the HR web client and the payroll sync job");

        // The release added a read, not a migration, so rolling back cannot strand any data.
        fast.mitigate("roll back to the previous release",
                "5xx rate back to 0.1%; the manager column disappears from the list again");
        fast.suspect("yesterday's deploy", "the timing lines up");
        fast.review();

        System.out.println("Restoring service and fixing the bug are two different jobs.");
    }
}
