import visual.VisualApiRoutes;

public class Playground {
    public static void main(String[] args) {
        VisualApiRoutes api = VisualApiRoutes.forService("Admin API");

        api.expose("GET", "/employees/{id}");

        // Born as a link on an admin page: a link can only issue a GET, so the
        // action was written into the path instead.
        api.expose("GET", "/employees/{id}/delete");

        // It works. That is the problem -- so does the browser's prefetch, the
        // crawler that indexed the admin page, and the client that retried after
        // a timeout.
        api.request("GET", "/employees/42/delete");

        // The fix is not a better word in the path.
        api.rename("GET", "/employees/{id}/delete", "DELETE", "/employees/{id}");

        api.request("GET", "/employees/42/delete");
        api.request("DELETE", "/employees/42");

        api.review();
        System.out.println("Deleting is a method, not a segment: GET must be safe to send by accident.");
    }
}
