import visual.VisualErrorContract;

public class Playground {
    public static void main(String[] args) {
        VisualErrorContract api = VisualErrorContract.forApi("employee-api", "employee");
        api.define("employee.not_found", "EmployeeNotFoundException", 404, false);

        // Nothing maps this one, so the catch-all answers: 500, generic code.
        api.callFails("POST", "/employees/42/payroll", "PayrollTimeoutException");

        // Naming it turns an incident into an instruction the client can follow.
        api.define("employee.payroll_unavailable", "PayrollTimeoutException", 503, true);
        api.callFails("POST", "/employees/42/payroll", "PayrollTimeoutException");

        api.review();
        System.out.println("Unmapped exceptions are 500s you have not classified yet.");
    }
}
