import visual.VisualErrorContract;

public class Playground {
    public static void main(String[] args) {
        VisualErrorContract api = VisualErrorContract.forApi("employee-api", "employee");
        api.define("employee.email_taken", "EmailAlreadyUsedException", 409, false);

        // The default error attributes hand the caller the database's own words.
        api.leakInternals("POST", "/employees", "DataIntegrityViolationException",
                "ERROR: duplicate key value violates unique constraint \"uk_employee_email\"");

        // The same collision, caught in the service and rethrown in the domain's words.
        api.callFails("POST", "/employees", "EmailAlreadyUsedException");

        api.review();
        System.out.println("The client gets a code and a traceId; the stack trace stays in the log.");
    }
}
