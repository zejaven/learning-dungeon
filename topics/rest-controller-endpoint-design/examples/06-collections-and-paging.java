import visual.VisualRestController;

public class Playground {
    public static void main(String[] args) {
        VisualRestController api =
                VisualRestController.forResource("EmployeeController", "/employees", "Employee");

        // "Return the list" is a design decision with no upper bound in it.
        api.handler("GET", "/employees", "-", "List<EmployeeView>", 200);
        api.review();

        // Paging, a capped page size, sorting and filters -- all on one endpoint.
        api.paging("/employees", 20, 100, "department", "status");
        api.call("GET", "/employees?department=finance&page=2&size=50");

        api.review();
        System.out.println("One collection endpoint answers every filter, sort and page.");
    }
}
