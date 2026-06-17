package com.interviewlearning.structure;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.interviewlearning.structure.StructureDtos.ClassGraph;
import com.interviewlearning.structure.StructureDtos.ClassNode;
import com.interviewlearning.structure.StructureDtos.Edge;
import com.interviewlearning.topics.TopicDtos.ProjectFile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses a set of Java sources into a class-relationship graph used to draw a
 * class diagram and check structural missions. Best-effort: unparseable files
 * are skipped, and only edges between types declared in the project are kept (so
 * references to {@code java.*} types don't clutter the diagram).
 */
@Service
public class StructureAnalyzer {

    /** Builds the graph from the given project files. */
    public ClassGraph analyze(List<ProjectFile> files) {
        // Pass 1: collect declared types (so we can keep only intra-project edges).
        Map<String, ClassNode> nodes = new LinkedHashMap<>();
        List<TypeDeclaration<?>> decls = new ArrayList<>();
        JavaParser parser = new JavaParser();
        for (ProjectFile f : files) {
            if (f.content() == null || f.content().isBlank()) {
                continue;
            }
            parser.parse(f.content()).getResult().ifPresent(cu -> collectTypes(cu, nodes, decls));
        }

        // Pass 2: edges between known nodes.
        Set<Edge> edges = new LinkedHashSet<>();
        for (TypeDeclaration<?> decl : decls) {
            if (decl instanceof ClassOrInterfaceDeclaration cid) {
                String from = cid.getNameAsString();
                cid.getExtendedTypes().forEach(t -> addEdge(edges, nodes, from, t.getNameAsString(), "extends"));
                cid.getImplementedTypes().forEach(t -> addEdge(edges, nodes, from, t.getNameAsString(), "implements"));
                for (FieldDeclaration field : cid.getFields()) {
                    // A field of a project type is a has-a (association). Unwrap
                    // generics/arrays/collections to their argument types, so
                    // `List<Strategy>` and `Strategy[]` both count as Strategy.
                    field.getVariables().forEach(v ->
                            v.getType().findAll(ClassOrInterfaceType.class)
                                    .forEach(ct -> addEdge(edges, nodes, from, ct.getNameAsString(), "association")));
                }
            }
        }
        return new ClassGraph(new ArrayList<>(nodes.values()), new ArrayList<>(edges));
    }

    private void collectTypes(CompilationUnit cu, Map<String, ClassNode> nodes, List<TypeDeclaration<?>> decls) {
        for (TypeDeclaration<?> t : cu.findAll(TypeDeclaration.class)) {
            String name = t.getNameAsString();
            String kind = kindOf(t);
            nodes.putIfAbsent(name, new ClassNode(name, kind));
            decls.add(t);
        }
    }

    private String kindOf(TypeDeclaration<?> t) {
        if (t instanceof ClassOrInterfaceDeclaration cid) {
            if (cid.isInterface()) {
                return "interface";
            }
            return cid.isAbstract() ? "abstractClass" : "class";
        }
        if (t instanceof EnumDeclaration) {
            return "enum";
        }
        return "class";
    }

    private void addEdge(Set<Edge> edges, Map<String, ClassNode> nodes, String from, String to, String kind) {
        if (from.equals(to) || !nodes.containsKey(from) || !nodes.containsKey(to)) {
            return;
        }
        edges.add(new Edge(from, to, kind));
    }
}
