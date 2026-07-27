import visual.VisualErrorContract;

public class Playground {
    public static void main(String[] args) {
        VisualErrorContract api = VisualErrorContract.forApi("employee-api", "employee");
        api.define("employee.not_found", "EmployeeNotFoundException", 404, false);

        // The baseline: a request that works needs none of the contract.
        api.call("GET", "/employees/42");

        // The handler catches nothing; one @RestControllerAdvice maps the exception.
        api.callFails("GET", "/employees/9999", "EmployeeNotFoundException");

        // The same failure, caught inside the handler and reported as a success.
        api.swallow("GET", "/employees/9999", "EmployeeNotFoundException", "no employee 9999");

        System.out.println("Map exceptions in one place; never answer a failure with 200.");
    }
}
