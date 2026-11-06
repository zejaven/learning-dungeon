import { useEffect, useLayoutEffect, useRef, type ReactNode } from 'react';
import { ui, useLang } from '@app/i18n';

export interface PanZoomBox {
  x: number;
  y: number;
  w: number;
  h: number;
}

const MIN_SCALE = 0.25;
const MAX_SCALE = 2;
/** Below this the small text is not drawn: it could not be read anyway. */
const FAR_SCALE = 0.45;
/** A press that moved further than this is a drag, not a click. */
const DRAG_PX = 4;
const MARGIN = 24;

/**
 * A surface of `width`×`height` that can be dragged and zoomed: the wheel zooms
 * at the cursor, dragging pans, two fingers pinch, and the buttons do it without
 * either. `focus` is the box to bring to the middle whenever `focusKey` changes;
 * with no focus the whole surface is fitted.
 *
 * The content moves through a single CSS transform, so a drag re-renders nothing.
 *
 * The pointer is captured only once a press has moved past {@link DRAG_PX}.
 * Capturing it on press would retarget the click to the surface, and the buttons
 * inside could never be pressed. The click ending a drag is then swallowed in the
 * capture phase, or letting go over a button would press it.
 */
export function PanZoom({
  width,
  height,
  focus,
  focusKey,
  children,
}: {
  width: number;
  height: number;
  focus: PanZoomBox | null;
  focusKey: string | null;
  children: ReactNode;
}) {
  const lang = useLang((s) => s.lang);
  const rootRef = useRef<HTMLDivElement>(null);
  const worldRef = useRef<HTMLDivElement>(null);
  const view = useRef({ x: MARGIN, y: MARGIN, k: 1 });
  const placed = useRef(false);

  function apply() {
    const { x, y, k } = view.current;
    worldRef.current!.style.transform = `translate(${x}px, ${y}px) scale(${k})`;
    rootRef.current!.classList.toggle('far', k < FAR_SCALE);
  }

  function zoomAt(cx: number, cy: number, factor: number) {
    const v = view.current;
    const k = Math.min(MAX_SCALE, Math.max(MIN_SCALE, v.k * factor));
    v.x = cx - (cx - v.x) * (k / v.k);
    v.y = cy - (cy - v.y) * (k / v.k);
    v.k = k;
    apply();
  }

  function zoomAtMiddle(factor: number) {
    const r = rootRef.current!.getBoundingClientRect();
    zoomAt(r.width / 2, r.height / 2, factor);
  }

  function fit() {
    const r = rootRef.current!.getBoundingClientRect();
    const k = Math.max(MIN_SCALE, Math.min(1, (r.width - MARGIN * 2) / width, (r.height - MARGIN * 2) / height));
    view.current = { k, x: (r.width - width * k) / 2, y: Math.max(MARGIN, (r.height - height * k) / 2) };
    apply();
  }

  function center(box: PanZoomBox) {
    const r = rootRef.current!.getBoundingClientRect();
    // The first placement picks a readable scale; later ones respect the user's.
    const k = placed.current ? view.current.k : 1;
    view.current = { k, x: r.width / 2 - (box.x + box.w / 2) * k, y: r.height / 2 - (box.y + box.h / 2) * k };
    apply();
  }

  useLayoutEffect(() => {
    if (focus) center(focus);
    else if (!placed.current) fit();
    placed.current = true;
    // Only a new key moves the view: a re-render must not undo the user's drag.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [focusKey]);

  useEffect(() => {
    const root = rootRef.current!;
    const pointers = new Map<number, { x: number; y: number }>();
    let start = { x: 0, y: 0, vx: 0, vy: 0 };
    let pinch: { distance: number; k: number } | null = null;
    let dragged = false;

    const rest = (x: number, y: number) => {
      start = { x, y, vx: view.current.x, vy: view.current.y };
    };
    const distance = (a: { x: number; y: number }, b: { x: number; y: number }) => Math.hypot(a.x - b.x, a.y - b.y);

    const onWheel = (ev: WheelEvent) => {
      ev.preventDefault();
      const r = root.getBoundingClientRect();
      zoomAt(ev.clientX - r.left, ev.clientY - r.top, Math.exp(-ev.deltaY * 0.0015));
    };
    const onDown = (ev: PointerEvent) => {
      if (pointers.size === 0) dragged = false;
      if ((ev.target as Element).closest('.panzoom-ui')) return;
      pointers.set(ev.pointerId, { x: ev.clientX, y: ev.clientY });
      if (pointers.size === 1) {
        rest(ev.clientX, ev.clientY);
      } else if (pointers.size === 2) {
        const [a, b] = [...pointers.values()];
        pinch = { distance: distance(a, b), k: view.current.k };
        dragged = true;
        for (const id of pointers.keys()) root.setPointerCapture(id);
      }
    };
    const onMove = (ev: PointerEvent) => {
      if (!pointers.has(ev.pointerId)) return;
      pointers.set(ev.pointerId, { x: ev.clientX, y: ev.clientY });
      if (pointers.size === 2 && pinch) {
        const [a, b] = [...pointers.values()];
        const r = root.getBoundingClientRect();
        const factor = (pinch.k * (distance(a, b) / pinch.distance)) / view.current.k;
        zoomAt((a.x + b.x) / 2 - r.left, (a.y + b.y) / 2 - r.top, factor);
        return;
      }
      const dx = ev.clientX - start.x;
      const dy = ev.clientY - start.y;
      if (!dragged) {
        if (Math.hypot(dx, dy) <= DRAG_PX) return;
        dragged = true;
        root.setPointerCapture(ev.pointerId);
        root.classList.add('panning');
      }
      view.current.x = start.vx + dx;
      view.current.y = start.vy + dy;
      apply();
    };
    const onUp = (ev: PointerEvent) => {
      if (!pointers.delete(ev.pointerId)) return;
      pinch = null;
      const left = [...pointers.values()][0];
      if (left) rest(left.x, left.y); // the finger still down carries on panning
      else root.classList.remove('panning');
    };
    const onClickCapture = (ev: MouseEvent) => {
      if (!dragged) return;
      dragged = false;
      ev.stopPropagation();
      ev.preventDefault();
    };

    // Not React props: a React wheel handler is passive and cannot stop the page
    // from scrolling.
    root.addEventListener('wheel', onWheel, { passive: false });
    root.addEventListener('pointerdown', onDown);
    root.addEventListener('pointermove', onMove);
    root.addEventListener('pointerup', onUp);
    root.addEventListener('pointercancel', onUp);
    root.addEventListener('click', onClickCapture, true);
    return () => {
      root.removeEventListener('wheel', onWheel);
      root.removeEventListener('pointerdown', onDown);
      root.removeEventListener('pointermove', onMove);
      root.removeEventListener('pointerup', onUp);
      root.removeEventListener('pointercancel', onUp);
      root.removeEventListener('click', onClickCapture, true);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="panzoom" ref={rootRef}>
      <div className="panzoom-world" ref={worldRef} style={{ width, height }}>
        {children}
      </div>
      <div className="panzoom-ui panzoom-controls">
        <button title={ui('mapZoomIn', lang)} onClick={() => zoomAtMiddle(1.25)}>
          +
        </button>
        <button title={ui('mapZoomOut', lang)} onClick={() => zoomAtMiddle(0.8)}>
          −
        </button>
        <button onClick={fit}>{ui('mapFit', lang)}</button>
      </div>
    </div>
  );
}
