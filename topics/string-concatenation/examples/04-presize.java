import visual.VisualStringConcat;

public class Playground {
    public static void main(String[] args) {
        VisualStringConcat scene = new VisualStringConcat();

        // If you know (or can estimate) the final size, pre-size the builder:
        // new StringBuilder(capacity). The buffer is allocated once, big enough
        // for everything, so it NEVER grows and leaves zero garbage buffers.
        scene.builderLoop("ab", 8, 64);

        // For a million pieces, presizing avoids every reallocation and copy.
        System.out.println("Pre-sizing removes the grow-and-copy steps entirely.");
    }
}
