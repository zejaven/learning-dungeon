import { useEffect, useRef, useState } from 'react';
import { useTheme } from '@app/engine/themeStore';

/**
 * Renders one ```mermaid``` code block as an SVG diagram. Mermaid is large, so it
 * is imported lazily the first time a diagram appears — pages without diagrams
 * never pay for it. Invalid or still-streaming source falls back to showing the
 * raw code instead of crashing, and re-renders into a diagram once it parses.
 */

type Mermaid = typeof import('mermaid')['default'];

let mermaidPromise: Promise<Mermaid> | null = null;

function loadMermaid(): Promise<Mermaid> {
  if (!mermaidPromise) {
    mermaidPromise = import('mermaid').then((mod) => mod.default);
  }
  return mermaidPromise;
}

let renderSeq = 0;

export function MermaidBlock({ code }: { code: string }) {
  const [svg, setSvg] = useState<string | null>(null);
  const idRef = useRef(`mmd-${++renderSeq}`);
  const theme = useTheme((s) => s.theme);

  useEffect(() => {
    let cancelled = false;
    const source = code.trim();
    if (!source) {
      setSvg(null);
      return;
    }
    (async () => {
      try {
        const mermaid = await loadMermaid();
        // Re-init per render so a theme toggle re-colors the diagram.
        mermaid.initialize({
          startOnLoad: false,
          securityLevel: 'strict', // sanitize; content is Claude-generated
          theme: theme === 'light' ? 'default' : 'dark',
          fontFamily: 'inherit',
        });
        // Validate first so partial/invalid source doesn't throw inside render
        // (and doesn't leave error nodes in the DOM).
        await mermaid.parse(source);
        const { svg: out } = await mermaid.render(`${idRef.current}-${++renderSeq}`, source);
        if (!cancelled) setSvg(out);
      } catch {
        if (!cancelled) setSvg(null); // fall back to raw code
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [code, theme]);

  if (svg) {
    // eslint-disable-next-line react/no-danger
    return <div className="mermaid-diagram" dangerouslySetInnerHTML={{ __html: svg }} />;
  }
  return (
    <pre className="mermaid-fallback">
      <code>{code}</code>
    </pre>
  );
}
