import visual.VisualErrorContract;

public class Playground {
    public static void main(String[] args) {
        // The namespace is the domain that owns these codes.
        VisualErrorContract api = VisualErrorContract.forApi("employee-api", "employee");

        // One row per failure the API can have: code, exception, status, retryable.
        api.define("employee.not_found", "EmployeeNotFoundException", 404, false);
        api.define("employee.email_taken", "EmailAlreadyUsedException", 409, false);
        api.define("employee.salary_below_minimum", "SalaryBelowMinimumException", 422, false);
        api.define("employee.payroll_unavailable", "PayrollTimeoutException", 503, true);

        api.review();
        System.out.println("The status is the class of failure; the code is the exact case.");
    }
}
