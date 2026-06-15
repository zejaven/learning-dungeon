import type { ReactNode } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { MermaidBlock } from './MermaidBlock';

/**
 * Renders a Claude markdown answer (the same renderer used for topic
 * explanations). Safe to call on partial text while streaming — react-markdown
 * re-parses the growing string on each delta.
 *
 * Fenced ```mermaid``` blocks render as diagrams (see {@link MermaidBlock}); all
 * other code blocks stay as plain code. We intercept at the `pre` level so the
 * diagram isn't left nested inside a <pre>.
 *
 * `className` defaults to the boxed, height-capped streaming look used in dialogs.
 * Pass `"markdown"` for full-width, full-height prose (e.g. the theory panel).
 */
export function Markdown({
  children,
  className = 'md-stream markdown',
}: {
  children: string;
  className?: string;
}) {
  return (
    <div className={className}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          pre(props) {
            const mermaid = extractMermaid(props.children);
            if (mermaid != null) return <MermaidBlock code={mermaid} />;
            return <pre>{props.children}</pre>;
          },
        }}
      >
        {children}
      </ReactMarkdown>
    </div>
  );
}

/**
 * If a <pre>'s child is a ```mermaid``` code element, returns its source text;
 * otherwise null. react-markdown renders fenced code as `pre > code.language-*`.
 */
function extractMermaid(children: ReactNode): string | null {
  const child = Array.isArray(children) ? children[0] : children;
  if (!child || typeof child !== 'object' || !('props' in child)) return null;
  const props = (child as { props?: { className?: string; children?: ReactNode } }).props;
  if (!props || !/\blanguage-mermaid\b/.test(props.className ?? '')) return null;
  return String(props.children ?? '').replace(/\n$/, '');
}
