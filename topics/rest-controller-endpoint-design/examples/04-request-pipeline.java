import visual.VisualRestController;

public class Playground {
    public static void main(String[] args) {
        VisualRestController api =
                VisualRestController.forResource("EmployeeController", "/employees", "Employee");

        api.handler("GET", "/employees/{id}", "-", "EmployeeView", 200);
        api.handler("POST", "/employees", "CreateEmployeeRequest", "EmployeeView", 201);
        api.validation("POST", "/employees", "name: not blank", "salary: positive", "email: well-formed");

        // A handler method has exactly four jobs. Watch them run in order.
        api.call("GET", "/employees/42");
        api.call("POST", "/employees");

        api.review();
        System.out.println("Bind, validate, delegate, map to a status -- that is the whole method.");
    }
}
