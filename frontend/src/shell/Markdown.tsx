import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

/**
 * Renders a Claude markdown answer (the same renderer used for topic
 * explanations). Safe to call on partial text while streaming — react-markdown
 * re-parses the growing string on each delta.
 */
export function Markdown({ children }: { children: string }) {
  return (
    <div className="md-stream markdown">
      <ReactMarkdown remarkPlugins={[remarkGfm]}>{children}</ReactMarkdown>
    </div>
  );
}
