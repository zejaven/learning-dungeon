package com.interviewlearning.structure;

import com.interviewlearning.structure.StructureDtos.ClassGraph;
import com.interviewlearning.topics.TopicDtos.ProjectFile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureAnalyzerTest {

    private final StructureAnalyzer analyzer = new StructureAnalyzer();

    @Test
    void detectsStrategyStructure() {
        ClassGraph g = analyzer.analyze(List.of(
                new ProjectFile("PaymentStrategy.java", "interface PaymentStrategy { void pay(int amount); }"),
                new ProjectFile("CardStrategy.java", "class CardStrategy implements PaymentStrategy { public void pay(int a) {} }"),
                new ProjectFile("CashStrategy.java", "class CashStrategy implements PaymentStrategy { public void pay(int a) {} }"),
                new ProjectFile("Context.java", "class Context { private PaymentStrategy strategy; void set(PaymentStrategy s){ strategy = s; } }")
        ));

        assertEquals(4, g.nodes().size(), "four declared types");
        assertTrue(g.nodes().stream().anyMatch(n -> n.name().equals("PaymentStrategy") && n.kind().equals("interface")),
                "PaymentStrategy is an interface node");

        long impls = g.edges().stream()
                .filter(e -> e.kind().equals("implements") && e.to().equals("PaymentStrategy"))
                .count();
        assertEquals(2, impls, "two implementations of the strategy interface");

        assertTrue(g.edges().stream().anyMatch(e ->
                        e.kind().equals("association") && e.from().equals("Context") && e.to().equals("PaymentStrategy")),
                "Context holds a PaymentStrategy field (association)");
    }

    @Test
    void unwrapsGenericFieldTypesAndDropsExternalTypes() {
        ClassGraph g = analyzer.analyze(List.of(
                new ProjectFile("Observer.java", "interface Observer {}"),
                new ProjectFile("Subject.java", "import java.util.List; class Subject { private List<Observer> observers; }")
        ));

        assertTrue(g.edges().stream().anyMatch(e ->
                        e.kind().equals("association") && e.from().equals("Subject") && e.to().equals("Observer")),
                "List<Observer> field counts as an association to Observer");
        // `List` is not a project type, so no node/edge for it.
        assertTrue(g.nodes().stream().noneMatch(n -> n.name().equals("List")), "external type List is not a node");
    }

    @Test
    void skipsUnparseableSources() {
        ClassGraph g = analyzer.analyze(List.of(
                new ProjectFile("Good.java", "class Good {}"),
                new ProjectFile("Broken.java", "class Broken { this is not java")
        ));
        assertTrue(g.nodes().stream().anyMatch(n -> n.name().equals("Good")));
        assertTrue(g.nodes().stream().noneMatch(n -> n.name().equals("Broken")));
    }
}
