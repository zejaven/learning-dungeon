import visual.VisualApiRoutes;

public class Playground {
    public static void main(String[] args) {
        VisualApiRoutes api = VisualApiRoutes.forService("Employee API (RPC style)");

        // The same five operations, named as procedure calls. Each one invents a
        // URL, and each URL answers exactly one method.
        api.expose("GET", "/getEmployeeById");
        api.expose("POST", "/createEmployee");
        api.expose("POST", "/updateEmployeeSalary");
        api.expose("POST", "/deleteEmployee");

        // "The filter body did not fit in a URL, so we made the read a POST."
        api.expose("POST", "/getEmployees");

        // Added a year later by someone who assumed the surface was RESTful.
        api.expose("GET", "/employee/{id}");

        // Nothing here is guessable: knowing one endpoint tells you nothing
        // about the next.
        api.request("DELETE", "/employees/42");

        api.review();
        System.out.println("Six URLs for one resource, and none of them predicts another.");
    }
}
