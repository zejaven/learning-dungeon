import visual.VisualRestController;

public class Playground {
    public static void main(String[] args) {
        VisualRestController api =
                VisualRestController.forResource("EmployeeController", "/employees", "Employee");

        // The shortcut: let the JPA entity be the API's type in both directions.
        api.handler("POST", "/employees", "Employee", "Employee", 201);
        api.handler("GET", "/employees/{id}", "-", "Employee", 200);

        // The fix: two small classes the API owns. The request type carries only
        // what a client is allowed to send, the response type only what it may see.
        api.handler("POST", "/employees", "CreateEmployeeRequest", "EmployeeView", 201);
        api.handler("GET", "/employees/{id}", "-", "EmployeeView", 200);

        api.review();
        System.out.println("The table is a schema; the DTO is a contract. They change for different reasons.");
    }
}
