import visual.VisualApiRoutes;

public class Playground {
    public static void main(String[] args) {
        VisualApiRoutes api = VisualApiRoutes.forService("Employee API");

        // Two URLs, because this API talks about exactly two things: the
        // collection of employees and one employee inside it. Every operation is
        // a method on one of them -- the path never says what is being done.
        api.expose("GET", "/employees");            // read the collection
        api.expose("POST", "/employees");           // add one to the collection
        api.expose("GET", "/employees/{id}");       // read one
        api.expose("PUT", "/employees/{id}");       // replace one
        api.expose("DELETE", "/employees/{id}");    // remove one

        // A client that has seen the read already knows how to delete.
        api.request("GET", "/employees/42");
        api.request("DELETE", "/employees/42");

        api.review();
        System.out.println("Five operations, two URLs: nouns in the path, verbs as methods.");
    }
}
