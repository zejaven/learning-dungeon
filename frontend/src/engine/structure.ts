import type { ClassGraph, StructureRule } from './traceTypes';

/**
 * Evaluates a structure mission's predicates against the analyzed class graph.
 * A mission passes when EVERY rule holds. Supported rule kinds:
 *
 *  - interfaceWithImpls { minImplementations?, name? }
 *      an interface (optionally named) with ≥ N implementations
 *  - composition { targetKind?, ownerKind?, name? }
 *      some class has a field (association) to a node of targetKind / named `name`
 *  - edge { relation: extends|implements|association, from?, to? }
 *      such an edge exists
 *  - nodeExists { nodeKind: interface|class|abstractClass|enum, name? }
 */
export function evaluateStructureMission(rules: StructureRule[], graph: ClassGraph): boolean {
  if (!rules || rules.length === 0) return false;
  return rules.every((r) => evalRule(r, graph));
}

function kindOf(graph: ClassGraph, name: string): string | undefined {
  return graph.nodes.find((n) => n.name === name)?.kind;
}

function evalRule(rule: StructureRule, graph: ClassGraph): boolean {
  switch (rule.kind) {
    case 'interfaceWithImpls': {
      const min = Number(rule.minImplementations ?? 1);
      const name = rule.name as string | undefined;
      const interfaces = graph.nodes.filter((n) => n.kind === 'interface' && (!name || n.name === name));
      return interfaces.some(
        (i) => graph.edges.filter((e) => e.kind === 'implements' && e.to === i.name).length >= min,
      );
    }
    case 'composition': {
      const targetKind = rule.targetKind as string | undefined;
      const ownerKind = rule.ownerKind as string | undefined;
      const name = rule.name as string | undefined;
      return graph.edges.some((e) => {
        if (e.kind !== 'association') return false;
        if (name && e.to !== name) return false;
        if (targetKind && kindOf(graph, e.to) !== targetKind) return false;
        if (ownerKind && kindOf(graph, e.from) !== ownerKind) return false;
        return true;
      });
    }
    case 'edge': {
      const relation = rule.relation as string | undefined;
      const from = rule.from as string | undefined;
      const to = rule.to as string | undefined;
      return graph.edges.some(
        (e) =>
          (!relation || e.kind === relation) && (!from || e.from === from) && (!to || e.to === to),
      );
    }
    case 'nodeExists': {
      const nodeKind = rule.nodeKind as string | undefined;
      const name = rule.name as string | undefined;
      return graph.nodes.some((n) => (!nodeKind || n.kind === nodeKind) && (!name || n.name === name));
    }
    default:
      return false;
  }
}

/** Renders the class graph as a Mermaid `classDiagram` source string. */
export function graphToMermaid(graph: ClassGraph): string {
  if (!graph || graph.nodes.length === 0) return '';
  const lines = ['classDiagram'];
  for (const n of graph.nodes) {
    lines.push(`  class ${n.name}`);
    if (n.kind === 'interface') lines.push(`  <<interface>> ${n.name}`);
    else if (n.kind === 'abstractClass') lines.push(`  <<abstract>> ${n.name}`);
    else if (n.kind === 'enum') lines.push(`  <<enumeration>> ${n.name}`);
  }
  for (const e of graph.edges) {
    if (e.kind === 'extends') lines.push(`  ${e.to} <|-- ${e.from}`);
    else if (e.kind === 'implements') lines.push(`  ${e.to} <|.. ${e.from}`);
    else lines.push(`  ${e.from} --> ${e.to}`);
  }
  return lines.join('\n');
}
