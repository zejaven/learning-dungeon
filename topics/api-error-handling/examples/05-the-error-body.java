import visual.VisualErrorContract;

public class Playground {
    public static void main(String[] args) {
        VisualErrorContract api = VisualErrorContract.forApi("employee-api", "employee");
        api.define("employee.validation_failed", "MethodArgumentNotValidException", 400, false);
        api.define("employee.not_found", "EmployeeNotFoundException", 404, false);

        // One code is not enough here: the client has to paint the right input red.
        api.callInvalid("POST", "/employees", "email: must not be blank", "salary: must be positive");

        // A different failure, the identical body shape.
        api.callFails("GET", "/employees/9999", "EmployeeNotFoundException");

        System.out.println("One body shape, parsed once by the client, works on every endpoint.");
    }
}
