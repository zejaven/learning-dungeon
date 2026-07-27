import visual.VisualErrorContract;

public class Playground {
    public static void main(String[] args) {
        VisualErrorContract api = VisualErrorContract.forApi("employee-api", "employee");

        // An error announced with a success status: nothing in the path notices it.
        api.define("employee.validation_failed", "MethodArgumentNotValidException", 200, false);

        // Retry advice that contradicts the status, in both directions.
        api.define("employee.email_taken", "EmailAlreadyUsedException", 409, true);
        api.define("employee.payroll_unavailable", "PayrollTimeoutException", 503, false);

        // The same three rows, with the status and the advice agreeing.
        api.define("employee.validation_failed", "MethodArgumentNotValidException", 400, false);
        api.define("employee.email_taken", "EmailAlreadyUsedException", 409, false);
        api.define("employee.payroll_unavailable", "PayrollTimeoutException", 503, true);

        api.review();
        System.out.println("Machines read the status line; only your own client reads the body.");
    }
}
