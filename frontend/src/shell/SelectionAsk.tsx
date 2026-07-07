import { useEffect, useRef, useState, type RefObject } from 'react';
import { ui, useLang } from '@app/i18n';

interface Pos {
  x: number;
  y: number;
}

/**
 * Floating "ask AI about this" affordance. Watches for a non-empty text
 * selection inside `containerRef` and renders a small button next to where the
 * user finished selecting; clicking it hands the selected text to `onAsk`
 * (which opens the assistant with the text pre-quoted).
 */
export function SelectionAsk({
  containerRef,
  onAsk,
}: {
  containerRef: RefObject<HTMLElement | null>;
  onAsk: (text: string) => void;
}) {
  const lang = useLang((s) => s.lang);
  const [pos, setPos] = useState<Pos | null>(null);
  const textRef = useRef('');

  useEffect(() => {
    function selectedTextInContainer(): string | null {
      const sel = window.getSelection();
      const container = containerRef.current;
      if (!sel || sel.isCollapsed || sel.rangeCount === 0 || !container) return null;
      const text = sel.toString().trim();
      if (!text) return null;
      const range = sel.getRangeAt(0);
      if (!container.contains(range.commonAncestorContainer)) return null;
      return text;
    }

    function onMouseUp(e: MouseEvent) {
      const mx = e.clientX;
      const my = e.clientY;
      // Defer so the browser has finalized the selection.
      setTimeout(() => {
        const text = selectedTextInContainer();
        if (!text) {
          setPos(null);
          return;
        }
        textRef.current = text;
        setPos({ x: mx, y: my });
      }, 0);
    }

    function onSelectionChange() {
      const sel = window.getSelection();
      if (!sel || sel.isCollapsed) setPos(null);
    }

    function onScroll() {
      // Anchored to viewport coordinates, so a stale position after scroll is
      // worse than just hiding.
      setPos(null);
    }

    document.addEventListener('mouseup', onMouseUp);
    document.addEventListener('selectionchange', onSelectionChange);
    window.addEventListener('scroll', onScroll, true);
    return () => {
      document.removeEventListener('mouseup', onMouseUp);
      document.removeEventListener('selectionchange', onSelectionChange);
      window.removeEventListener('scroll', onScroll, true);
    };
  }, [containerRef]);

  if (!pos) return null;

  return (
    <button
      className="selection-ask-btn"
      style={{ left: pos.x + 6, top: pos.y - 6 }}
      title={ui('askAboutSelection', lang)}
      // mousedown (not click) so we fire before the click collapses the
      // selection; preventDefault keeps the selection intact meanwhile.
      onMouseDown={(e) => {
        e.preventDefault();
        e.stopPropagation();
        const text = textRef.current;
        setPos(null);
        if (text) onAsk(text);
      }}
    >
      💬
    </button>
  );
}
