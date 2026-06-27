import visual.VisualMemory;

public class Playground {
    public static void main(String[] args) {
        VisualMemory mem = new VisualMemory();

        mem.newObject("payload", "Payload", "size=small");

        mem.call("parsePayload", "input", "Payload", "payload");
        mem.primitive("temporaryBufferSize", "int", "256");
        mem.newObject("token", "Token", "value=abc");

        // token was referenced only from parsePayload(); after return it is garbage.
        mem.ret();
    }
}
