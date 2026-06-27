import visual.VisualMemory;

public class Playground {
    public static void main(String[] args) {
        VisualMemory mem = new VisualMemory();

        mem.newObject("request", "Request", "path=/pay", "user=Ann");

        // main() -> controller() -> service() -> repository()
        mem.call("controller", "requestParam", "Request", "request");
        mem.primitive("httpStatus", "int", "200");

        mem.call("service", "serviceRequest", "Request", "requestParam");
        mem.primitive("attempt", "int", "1");

        mem.call("repository", "queryRequest", "Request", "serviceRequest");
        mem.primitive("rows", "int", "2");

        mem.ret();
        mem.ret();
        mem.ret();
    }
}
