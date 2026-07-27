import visual.VisualApiRoutes;

public class Playground {
    public static void main(String[] args) {
        VisualApiRoutes api = VisualApiRoutes.forService("Employee API");

        api.expose("GET", "/getEmployeeById");
        api.expose("GET", "/getAllEmployees");
        api.expose("POST", "/createEmployee");
        api.expose("POST", "/updateEmployee");
        api.expose("POST", "/deleteEmployee");

        // Split every name into "what" and "to which thing". The thing stays in
        // the path; the what becomes the method. Names that described the same
        // thing now land on the same URL.
        api.rename("GET", "/getEmployeeById", "GET", "/employees/{id}");
        api.rename("GET", "/getAllEmployees", "GET", "/employees");
        api.rename("POST", "/createEmployee", "POST", "/employees");
        api.rename("POST", "/updateEmployee", "PUT", "/employees/{id}");
        api.rename("POST", "/deleteEmployee", "DELETE", "/employees/{id}");

        api.request("GET", "/employees/42");

        api.review();
        System.out.println("Five names became two URLs; the vocabulary is now the domain's, not the code's.");
    }
}
