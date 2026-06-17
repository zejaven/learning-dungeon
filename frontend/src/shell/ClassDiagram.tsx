import { graphToMermaid } from '@app/engine/structure';
import { useStore } from '@app/engine/store';
import { ui, useLang } from '@app/i18n';
import { MermaidBlock } from './MermaidBlock';

/**
 * Renders the analyzed class graph of a structural topic as a Mermaid class
 * diagram. Empty until the first "Analyze" run.
 */
export function ClassDiagram() {
  const graph = useStore((s) => s.graph);
  const lang = useLang((s) => s.lang);

  if (!graph || graph.nodes.length === 0) {
    return <p className="home-hint">{ui('analyzeHint', lang)}</p>;
  }
  return <MermaidBlock code={graphToMermaid(graph)} />;
}
