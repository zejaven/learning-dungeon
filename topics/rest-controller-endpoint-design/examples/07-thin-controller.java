import visual.VisualRestController;

public class Playground {
    public static void main(String[] args) {
        VisualRestController api =
                VisualRestController.forResource("EmployeeController", "/employees", "Employee");

        // A sub-resource: the thing that changes has its own URL and its own body.
        api.handler("PUT", "/employees/{id}/salary", "SalaryChangeRequest", "SalaryView", 200);
        api.validation("PUT", "/employees/{id}/salary", "amount: positive", "reason: not blank");

        // The classic mistake: the rule ends up in the method that speaks HTTP.
        api.businessLogic("PUT", "/employees/{id}/salary", "grade caps, the 20% raise limit and the audit record");
        api.review();

        // The fix: the controller keeps the HTTP part and nothing else.
        api.delegate("PUT", "/employees/{id}/salary");
        api.call("PUT", "/employees/42/salary");

        api.review();
        System.out.println("A controller is an adapter: HTTP in, a service call out, a status back.");
    }
}
