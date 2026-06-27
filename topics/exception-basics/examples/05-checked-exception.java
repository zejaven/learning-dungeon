import visual.VisualException;

public class Playground {
    public static void main(String[] args) {
        // A checked exception (FileNotFoundException is an IOException). The
        // compiler forces loadFile to declare it and the caller to handle it.
        VisualException vm = new VisualException();

        vm.call("main", "IOException", false);   // catch (IOException e)
        vm.call("loadFile");                      // declares: throws IOException

        vm.throwException("FileNotFoundException", "data.txt not found");
    }
}
