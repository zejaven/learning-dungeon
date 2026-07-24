import type { ReactNode } from 'react';
import ReactMarkdown, { defaultUrlTransform } from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { routeForQuestion } from '@app/engine/router';
import { MermaidBlock } from './MermaidBlock';

/**
 * Renders an AI markdown answer (the same renderer used for topic
 * explanations). Safe to call on partial text while streaming — react-markdown
 * re-parses the growing string on each delta.
 *
 * Fenced ```mermaid``` blocks render as diagrams (see {@link MermaidBlock}); all
 * other code blocks stay as plain code. We intercept at the `pre` level so the
 * diagram isn't left nested inside a <pre>.
 *
 * Cross-topic links use a `topic:<id>` / `catalog:<id>` href, rendered as in-app
 * navigation to that question (`#/q/<id>`). Other links open in a new tab.
 *
 * `className` defaults to the boxed, height-capped streaming look used in dialogs.
 * Pass `"markdown"` for full-width, full-height prose (e.g. the theory panel).
 *
 * `assetBase` resolves relative image paths (e.g. `images/x.png` in a topic
 * explanation) against a base URL such as `/api/topics/<id>/assets`; absolute
 * URLs and other schemes pass through untouched.
 */
export function Markdown({
  children,
  className = 'md-stream markdown',
  assetBase,
}: {
  children: string;
  className?: string;
  assetBase?: string;
}) {
  return (
    <div className={className}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        // Keep our internal schemes; sanitize everything else as usual.
        urlTransform={(url) =>
          /^(topic|catalog):/i.test(url) ? url : defaultUrlTransform(url)
        }
        components={{
          pre(props) {
            const mermaid = extractMermaid(props.children);
            if (mermaid != null) return <MermaidBlock code={mermaid} />;
            return <pre>{props.children}</pre>;
          },
          img(props) {
            const src = props.src ?? '';
            const relative = src && !/^([a-z][a-z0-9+.-]*:|\/|#)/i.test(src);
            const resolved = assetBase && relative ? `${assetBase}/${src}` : src;
            return <img src={resolved} alt={props.alt ?? ''} loading="lazy" />;
          },
          a(props) {
            const href = props.href ?? '';
            const m = /^(?:topic|catalog):(.+)$/i.exec(href);
            if (m) {
              return (
                <a className="md-xref" href={`#${routeForQuestion(m[1])}`}>
                  {props.children}
                </a>
              );
            }
            return (
              <a href={href} target="_blank" rel="noreferrer">
                {props.children}
              </a>
            );
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
