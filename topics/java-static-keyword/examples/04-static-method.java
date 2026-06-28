import visual.VisualStatic;

public class Playground {
    public static void main(String[] args) {
        VisualStatic parser = new VisualStatic("IdParser");

        parser.staticField("prefix", "USR");

        // Static method: selected through the class, no object receiver, no this.
        parser.callStatic("parse(String)", "uses arguments and static configuration");

        parser.newInstance("customParser", "format=short");

        // Instance method: selected on one object, so it receives this.
        parser.callInstance("customParser", "format()", "uses this.format");
    }
}
