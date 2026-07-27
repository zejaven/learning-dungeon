import visual.VisualRestController;

public class Playground {
    public static void main(String[] args) {
        VisualRestController api =
                VisualRestController.forResource("EmployeeController", "/employees", "Employee");

        api.handler("POST", "/employees", "CreateEmployeeRequest", "EmployeeView", 201);
        api.validation("POST", "/employees", "name: not blank", "salary: positive");
        api.handler("GET", "/employees/{id}", "-", "EmployeeView", 200);

        // A bad body is rejected at the edge: the service is never reached.
        api.callInvalid("POST", "/employees", "salary", "must be positive");

        // A missing row is the service's answer, mapped to HTTP in one place.
        api.callNotFound("GET", "/employees/9999");

        api.review();
        System.out.println("400 is the shape being wrong; 404 is the world being different.");
    }
}
