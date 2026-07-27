import visual.VisualApiRoutes;

public class Playground {
    public static void main(String[] args) {
        VisualApiRoutes api = VisualApiRoutes.forService("Employee API");

        api.expose("GET", "/employees/{id}");
        api.expose("PUT", "/employees/{id}");
        api.expose("DELETE", "/employees/{id}");

        // The URL names a thing; the method names what to do with it.
        api.request("GET", "/employees/42");

        // The thing exists, but this is not something it does. The server can
        // only say that -- and list what it does do -- because the two halves
        // are separate.
        api.request("PATCH", "/employees/42");

        // Nothing is registered under this name at all: a different answer, for
        // a different reason.
        api.request("GET", "/employees/42/salary");

        api.review();
        System.out.println("405 says 'wrong verb', 404 says 'wrong noun'. Both answers need both halves.");
    }
}
