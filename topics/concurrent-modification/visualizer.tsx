import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { tl, useLang, type Localized } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to visualize fail-fast iteration and CopyOnWriteArrayList.',
    ru: 'Запустите код, чтобы увидеть fail-fast итерацию и CopyOnWriteArrayList.',
  },
  liveList: { en: 'live list', ru: 'живой список' },
  snapshot: { en: 'iterator snapshot', ru: 'snapshot iterator' },
  iterator: { en: 'iterator', ru: 'iterator' },
  recentActions: { en: 'recent actions', ru: 'последние действия' },
  modCount: { en: 'modCount', ru: 'modCount' },
  expected: { en: 'expectedModCount', ru: 'expectedModCount' },
  cursor: { en: 'cursor', ru: 'cursor' },
  inactive: { en: 'no active iterator', ru: 'нет активного iterator' },
  matches: { en: 'matches — iteration valid', ru: 'совпадает — итерация валидна' },
  mismatch: { en: 'mismatch — next() would throw CME', ru: 'не совпадает — next() бросит CME' },
  stableSnapshot: { en: 'frozen snapshot — never throws', ru: 'замороженный snapshot — не бросает' },
};

const MODE_LABELS: Record<string, Localized> = {
  FAIL_FAST: { en: 'fail-fast ArrayList', ru: 'fail-fast ArrayList' },
  COPY_ON_WRITE: { en: 'CopyOnWriteArrayList', ru: 'CopyOnWriteArrayList' },
};

const ACTION_LABELS: Record<string, Localized> = {
  CREATE: { en: 'create', ru: 'создан' },
  ADD: { en: 'add', ru: 'добавил' },
  REMOVE: { en: 'remove', ru: 'удалил' },
  COW_ADD: { en: 'add (copy)', ru: 'добавил (копия)' },
  COW_REMOVE: { en: 'remove (copy)', ru: 'удалил (копия)' },
  ITERATOR: { en: 'iterator', ru: 'iterator' },
  NEXT: { en: 'next', ru: 'next' },
  COW_SNAPSHOT_READ: { en: 'snapshot read', ru: 'чтение snapshot' },
  ITER_REMOVE: { en: 'iterator.remove', ru: 'iterator.remove' },
  COW_ITER_REMOVE: { en: 'remove (unsupported)', ru: 'remove (не поддерживается)' },
  CME: { en: 'ConcurrentModificationException', ru: 'ConcurrentModificationException' },
  DONE: { en: 'done', ru: 'готово' },
};

interface ElementSnapshot {
  index: number;
  value: string;
}

interface IteratorSnapshot {
  active: boolean;
  expectedModCount: number;
  cursor: number;
  lastReturned: number;
  snapshot: string[];
  stale: boolean;
}

interface HistoryItem {
  actor: string;
  action: string;
  detail: string;
}

interface ListState {
  name: string;
  mode: string;
  modCount: number;
  elements: ElementSnapshot[];
  iterator: IteratorSnapshot;
  history: HistoryItem[];
}

export default function ConcurrentModificationVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as ListState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const isCow = state.mode === 'COPY_ON_WRITE';
  const iter = state.iterator;
  const mode = MODE_LABELS[state.mode] ?? { en: state.mode, ru: state.mode };

  const liveCells: ArrayCell[] = state.elements.map((el) => ({
    key: el.index,
    label: `[${el.index}]`,
    highlighted: highlight.has(`element:${el.index}`),
    content: (
      <div style={valueWrapStyle}>
        <strong style={valueStyle}>{el.value}</strong>
        {iter.active && !isCow && iter.cursor - 1 === el.index && iter.lastReturned === el.index && (
          <span style={cursorBadge}>{tl(LABELS.cursor, lang)}</span>
        )}
      </div>
    ),
  }));

  const snapshotCells: ArrayCell[] = iter.snapshot.map((value, index) => ({
    key: index,
    label: `[${index}]`,
    highlighted: highlight.has(`snapshot:${index}`),
    content: (
      <div style={valueWrapStyle}>
        <strong style={valueStyle}>{value}</strong>
        {index < iter.cursor && <span style={readBadge}>✓</span>}
      </div>
    ),
  }));

  return (
    <div style={wrapStyle}>
      <div style={headerStyle}>
        <span style={nameStyle}>{state.name}</span>
        <span style={modePill}>{tl(mode, lang)}</span>
      </div>

      <section style={sectionStyle}>
        <div style={titleStyle}>{tl(LABELS.liveList, lang)}</div>
        <ArrayGrid cells={liveCells} />
      </section>

      <section style={{ ...iterBoxStyle, ...(iter.stale ? iterStaleStyle : {}) }}>
        <div style={titleStyle}>{tl(LABELS.iterator, lang)}</div>
        {!iter.active ? (
          <div style={metaStyle}>{tl(LABELS.inactive, lang)}</div>
        ) : isCow ? (
          <>
            <div style={counterRow}>
              <span style={chipStyle}>{tl(LABELS.cursor, lang)}: <strong>{iter.cursor}</strong></span>
            </div>
            <div style={titleStyle}>{tl(LABELS.snapshot, lang)}</div>
            <ArrayGrid cells={snapshotCells} />
            <div style={okNoteStyle}>{tl(LABELS.stableSnapshot, lang)}</div>
          </>
        ) : (
          <>
            <div style={counterRow}>
              <span style={{ ...chipStyle, ...(highlight.has('modCount') ? chipHotStyle : {}) }}>
                {tl(LABELS.modCount, lang)}: <strong>{state.modCount}</strong>
              </span>
              <span style={chipStyle}>
                {tl(LABELS.expected, lang)}: <strong>{iter.expectedModCount}</strong>
              </span>
              <span style={chipStyle}>{tl(LABELS.cursor, lang)}: <strong>{iter.cursor}</strong></span>
            </div>
            <div style={iter.stale ? badNoteStyle : okNoteStyle}>
              {tl(iter.stale ? LABELS.mismatch : LABELS.matches, lang)}
            </div>
          </>
        )}
      </section>

      <section style={sectionStyle}>
        <div style={titleStyle}>{tl(LABELS.recentActions, lang)}</div>
        <div style={historyStyle}>
          {state.history.map((item, index) => {
            const action = ACTION_LABELS[item.action] ?? { en: item.action, ru: item.action };
            return (
              <div key={`${item.actor}-${item.action}-${index}`} style={historyItemStyle}>
                <span style={actorStyle}>{item.actor}</span>
                <span>{tl(action, lang)}</span>
                {item.detail && <span style={monoStyle}>{item.detail}</span>}
              </div>
            );
          })}
        </div>
      </section>
    </div>
  );
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 12 };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
const headerStyle: CSSProperties = { display: 'flex', alignItems: 'center', gap: 10 };
const nameStyle: CSSProperties = { fontFamily: 'monospace', fontWeight: 700, fontSize: 14 };
const modePill: CSSProperties = {
  fontSize: 12,
  padding: '2px 8px',
  borderRadius: 999,
  background: 'var(--viz-badge)',
  fontFamily: 'monospace',
};
const sectionStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 6 };
const titleStyle: CSSProperties = { fontSize: 12, fontWeight: 700, opacity: 0.7 };
const valueWrapStyle: CSSProperties = { display: 'flex', alignItems: 'center', gap: 8 };
const valueStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 16 };
const cursorBadge: CSSProperties = {
  fontSize: 11,
  padding: '1px 6px',
  borderRadius: 4,
  background: 'var(--viz-active)',
  boxShadow: 'inset 2px 0 0 var(--accent)',
};
const readBadge: CSSProperties = { fontSize: 12, opacity: 0.6 };
const iterBoxStyle: CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: 8,
  padding: 10,
  borderRadius: 8,
  background: 'var(--viz-box)',
  border: '1px solid transparent',
};
const iterStaleStyle: CSSProperties = { border: '1px solid var(--accent)' };
const counterRow: CSSProperties = { display: 'flex', flexWrap: 'wrap', gap: 8 };
const chipStyle: CSSProperties = {
  fontSize: 13,
  fontFamily: 'monospace',
  padding: '2px 8px',
  borderRadius: 6,
  background: 'var(--viz-badge)',
};
const chipHotStyle: CSSProperties = { boxShadow: 'inset 0 0 0 1px var(--accent)' };
const okNoteStyle: CSSProperties = { fontSize: 12, opacity: 0.75 };
const badNoteStyle: CSSProperties = { fontSize: 12, fontWeight: 700, color: 'var(--accent)' };
const metaStyle: CSSProperties = { fontSize: 12, opacity: 0.6 };
const historyStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 4 };
const historyItemStyle: CSSProperties = {
  display: 'flex',
  gap: 8,
  alignItems: 'center',
  padding: '4px 8px',
  borderRadius: 6,
  background: 'var(--viz-box)',
  fontSize: 13,
};
const actorStyle: CSSProperties = { fontFamily: 'monospace', fontWeight: 700, minWidth: 56 };
const monoStyle: CSSProperties = { fontFamily: 'monospace', opacity: 0.85 };
