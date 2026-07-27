import visual.VisualCoroutines;

public class Playground {
    public static void main(String[] args) {
        VisualCoroutines rt = VisualCoroutines.runtime(2, 4);

        // A coroutine sitting at a suspension point stops the moment you cancel it.
        String download = rt.launch("IO", "download");
        rt.suspendAt(download, "readChunk()");
        rt.cancel(download);

        // while (true) { hash(block) } — no suspension point, no check, no way out.
        String hash = rt.launch("Default", "hashEverything");
        rt.cpuWork(hash, "round 1 of 3");
        rt.cancel(hash);
        rt.cpuWork(hash, "round 2 of 3");
        rt.cpuWork(hash, "round 3 of 3");
        rt.complete(hash);

        // The same loop with ensureActive() in it.
        String proper = rt.launch("Default", "hashCooperatively");
        rt.cooperativeCpuWork(proper, "round 1 of 3");
        rt.cancel(proper);
        rt.cooperativeCpuWork(proper, "round 2 of 3");

        rt.report();
        System.out.println("cancel() sets a flag. One of these loops reads it.");
    }
}
