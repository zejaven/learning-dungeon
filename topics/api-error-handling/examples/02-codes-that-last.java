import visual.VisualErrorContract;

public class Playground {
    public static void main(String[] args) {
        VisualErrorContract api = VisualErrorContract.forApi("employee-api", "employee");

        // A message used as an identifier: any rewording breaks every client.
        api.define("Employee not found", "EmployeeNotFoundException", 404, false);

        // No namespace: three services in one client all say "not_found".
        api.define("email_taken", "EmailAlreadyUsedException", 409, false);

        // The same two failures, named so they can survive a decade.
        api.define("employee.not_found", "EmployeeNotFoundException", 404, false);
        api.define("employee.email_taken", "EmailAlreadyUsedException", 409, false);

        api.review();
        System.out.println("A code is an identifier, not a sentence — the sentence goes in 'detail'.");
    }
}
