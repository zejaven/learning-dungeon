import { useEffect, useRef, useState, type RefObject } from 'react';
import { ui, useLang } from '@app/i18n';

interface Pos {
  x: number;
  y: number;
  /** Mouse: above the pointer. Touch: under the selection, clear of its handles. */
  below: boolean;
}

/** A touch selection settles after its handles stop moving; wait that long. */
const TOUCH_SETTLE_MS = 400;
/** Room for the selection handles drawn under the last line. */
const HANDLE_CLEARANCE_PX = 28;

/**
 * Floating "ask AI about this" affordance. Watches for a non-empty text
 * selection inside `containerRef` and renders a small button next to where the
 * user finished selecting; clicking it hands the selected text to `onAsk`
 * (which opens the assistant with the text pre-quoted).
 *
 * A mouse selection ends with `mouseup`. A touch selection (phone, VR headset)
 * is made with a long press and dragged handles, which send no `mouseup`, so
 * for those the button follows `selectionchange` once it settles and sits
 * under the selection instead of under a pointer that does not exist.
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
    // How the last selection was started; a mouse one is left to mouseup.
    let lastPointer = 'mouse';
    let settle: number | undefined;

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
        setPos({ x: mx, y: my, below: false });
      }, 0);
    }

    function onPointerDown(e: PointerEvent) {
      lastPointer = e.pointerType || 'mouse';
    }

    function onSelectionChange() {
      const sel = window.getSelection();
      window.clearTimeout(settle);
      if (!sel || sel.isCollapsed) {
        setPos(null);
        return;
      }
      if (lastPointer === 'mouse') return;
      setPos(null); // hide while the handles are still moving
      settle = window.setTimeout(() => {
        const text = selectedTextInContainer();
        const current = window.getSelection();
        if (!text || !current || current.rangeCount === 0) return;
        const rects = current.getRangeAt(0).getClientRects();
        const last = rects[rects.length - 1] ?? current.getRangeAt(0).getBoundingClientRect();
        textRef.current = text;
        setPos({
          x: Math.min(last.right, window.innerWidth - 48),
          y: Math.min(last.bottom + HANDLE_CLEARANCE_PX, window.innerHeight - 40),
          below: true,
        });
      }, TOUCH_SETTLE_MS);
    }

    function onScroll() {
      // Anchored to viewport coordinates, so a stale position after scroll is
      // worse than just hiding.
      setPos(null);
    }

    document.addEventListener('pointerdown', onPointerDown, true);
    document.addEventListener('mouseup', onMouseUp);
    document.addEventListener('selectionchange', onSelectionChange);
    window.addEventListener('scroll', onScroll, true);
    return () => {
      window.clearTimeout(settle);
      document.removeEventListener('pointerdown', onPointerDown, true);
      document.removeEventListener('mouseup', onMouseUp);
      document.removeEventListener('selectionchange', onSelectionChange);
      window.removeEventListener('scroll', onScroll, true);
    };
  }, [containerRef]);

  if (!pos) return null;

  return (
    <button
      className={`selection-ask-btn${pos.below ? ' below' : ''}`}
      style={pos.below ? { left: pos.x - 24, top: pos.y } : { left: pos.x + 6, top: pos.y - 6 }}
      title={ui('askAboutSelection', lang)}
      // pointerdown (not click) so we fire before the tap or click collapses
      // the selection, which would unmount this button first; preventDefault
      // keeps the selection intact meanwhile. The pointerdown listener on the
      // document runs before this one and does not hide the button.
      onPointerDown={(e) => {
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
