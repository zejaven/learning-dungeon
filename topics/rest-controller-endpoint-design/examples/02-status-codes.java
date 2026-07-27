import visual.VisualRestController;

public class Playground {
    public static void main(String[] args) {
        VisualRestController api =
                VisualRestController.forResource("EmployeeController", "/employees", "Employee");

        // The status is part of the design, not a detail of the framework.
        api.handler("POST", "/employees", "CreateEmployeeRequest", "EmployeeView", 200);
        api.handler("DELETE", "/employees/{id}", "-", "DeleteReport", 204);

        // Re-declaring the same method + path replaces the design decision.
        api.handler("POST", "/employees", "CreateEmployeeRequest", "EmployeeView", 201);
        api.handler("DELETE", "/employees/{id}", "-", "-", 204);

        api.call("POST", "/employees");
        api.review();
        System.out.println("201 says a thing was created; 204 says there is nothing to read.");
    }
}
