import visual.VisualRestController;

public class Playground {
    public static void main(String[] args) {
        // One controller owns one resource. Employee is the persistence type
        // behind it -- the API speaks its own types instead.
        VisualRestController api =
                VisualRestController.forResource("EmployeeController", "/employees", "Employee");

        // Every operation is one handler method: method + path, what binds in,
        // what goes out, and the status that says what happened.
        api.handler("GET", "/employees", "-", "List<EmployeeView>", 200);
        api.paging("/employees", 20, 100, "department", "status");
        api.handler("GET", "/employees/{id}", "-", "EmployeeView", 200);
        api.handler("POST", "/employees", "CreateEmployeeRequest", "EmployeeView", 201);
        api.handler("PUT", "/employees/{id}", "UpdateEmployeeRequest", "EmployeeView", 200);
        api.handler("DELETE", "/employees/{id}", "-", "-", 204);

        api.review();
        System.out.println("Six handlers over one resource, and every row reads the same way.");
    }
}
