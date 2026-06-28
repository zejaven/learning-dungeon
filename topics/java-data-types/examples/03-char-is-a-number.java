import visual.VisualDataTypes;

public class Playground {
    public static void main(String[] args) {
        VisualDataTypes t = new VisualDataTypes();

        // char looks like a letter, but it is really an unsigned 16-bit number:
        // the Unicode/ASCII code of the character.
        t.primitive("c", "char", "A");

        // 'A' is the code 65. Doing arithmetic on a char promotes it to int,
        // so 'A' + 1 is 66 (the int), not the char 'B'.
        t.charIsANumber("c");
    }
}
