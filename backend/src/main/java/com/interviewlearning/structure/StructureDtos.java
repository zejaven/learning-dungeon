package com.interviewlearning.structure;

import java.util.List;

/** The class-relationship graph extracted from a structural topic's sources. */
public final class StructureDtos {

    private StructureDtos() {
    }

    /** A declared type. {@code kind}: class | interface | abstractClass | enum. */
    public record ClassNode(String name, String kind) {
    }

    /** A relationship. {@code kind}: extends | implements | association (has-a). */
    public record Edge(String from, String to, String kind) {
    }

    public record ClassGraph(List<ClassNode> nodes, List<Edge> edges) {
    }
}
