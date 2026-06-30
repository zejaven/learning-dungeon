import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { tl, useLang, type Localized } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to visualize what keeps objects reachable from a GC root.',
    ru: 'Запустите код, чтобы увидеть, что держит объекты достижимыми от GC root.',
  },
  gcRoots: { en: 'GC roots', ru: 'GC roots' },
  heap: { en: 'heap', ru: 'куча' },
  recentActions: { en: 'recent actions', ru: 'последние действия' },
  live: { en: 'live objects', ru: 'живых объектов' },
  leaked: { en: 'leaked', ru: 'утекло' },
  heldBy: { en: 'held by', ru: 'держат' },
  noHolders: { en: 'no references', ru: 'нет ссылок' },
  collected: { en: 'collected', ru: 'собран GC' },
  leakedTag: { en: 'LEAK', ru: 'УТЕЧКА' },
};

const KIND_LABELS: Record<string, Localized> = {
  LONG_LIVED: { en: 'long-lived root', ru: 'долгоживущий root' },
  SCOPE: { en: 'method scope', ru: 'область метода' },
};

const ACTION_LABELS: Record<string, Localized> = {
  MODEL_CREATED: { en: 'scene created', ru: 'сцена создана' },
  ROOT_DECLARED: { en: 'long-lived root', ru: 'долгоживущий root' },
  ALLOCATE: { en: 'allocated', ru: 'создан' },
  REFERENCE_ADDED: { en: 'reference added', ru: 'добавлена ссылка' },
  REFERENCE_REMOVED: { en: 'reference removed', ru: 'ссылка убрана' },
  SCOPE_EXIT: { en: 'scope ended', ru: 'область завершена' },
  GC_RUN: { en: 'GC ran', ru: 'запущен GC' },
  GC_COLLECTED: { en: 'collected', ru: 'собран' },
  LEAK_DETECTED: { en: 'leak', ru: 'утечка' },
};

interface RootSnapshot {
  name: string;
  kind: string;
}

interface ObjectSnapshot {
  id: string;
  label: string;
  holders: string[];
  collected: boolean;
  leaked: boolean;
  escaped: boolean;
}

interface HistoryItem {
  action: string;
  target: string;
  detail: string;
}

interface LeakState {
  name: string;
  liveCount: number;
  leakCount: number;
  roots: RootSnapshot[];
  objects: ObjectSnapshot[];
  history: HistoryItem[];
}

export default function MemoryLeakVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as LeakState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);

  const rootCells: ArrayCell[] = state.roots.map((root) => {
    const kind = KIND_LABELS[root.kind] ?? { en: root.kind, ru: root.kind };
    const longLived = root.kind === 'LONG_LIVED';
    return {
      key: root.name,
      label: root.name,
      highlighted: highlight.has(`root:${root.name}`),
      content: (
        <span style={{ ...rootKindStyle, color: longLived ? 'var(--accent)' : undefined }}>
          {tl(kind, lang)}
        </span>
      ),
    };
  });

  const objectCells: ArrayCell[] = state.objects.map((obj) => {
    const holders =
      obj.holders.length > 0 ? obj.holders.join(', ') : tl(LABELS.noHolders, lang);
    return {
      key: obj.id,
      label: obj.id,
      highlighted: highlight.has(`object:${obj.id}`),
      content: (
        <div
          style={{
            ...objectRowStyle,
            ...(obj.collected ? collectedStyle : {}),
            ...(obj.leaked && !obj.collected ? leakedStyle : {}),
          }}
        >
          <strong style={objectLabelStyle}>{obj.label}</strong>
          <span style={metaStyle}>
            {tl(LABELS.heldBy, lang)}: {holders}
          </span>
          {obj.collected && <span style={tagStyle}>{tl(LABELS.collected, lang)}</span>}
          {obj.leaked && !obj.collected && (
            <span style={leakTagStyle}>{tl(LABELS.leakedTag, lang)}</span>
          )}
        </div>
      ),
    };
  });

  return (
    <div style={wrapStyle}>
      <div style={statsStyle}>
        <span>
          {tl(LABELS.live, lang)}: <strong>{state.liveCount}</strong>
        </span>
        <span style={{ color: state.leakCount > 0 ? 'var(--danger, #e06c75)' : undefined }}>
          {tl(LABELS.leaked, lang)}: <strong>{state.leakCount}</strong>
        </span>
      </div>
      <section style={sectionStyle}>
        <div style={titleStyle}>{tl(LABELS.gcRoots, lang)}</div>
        <ArrayGrid cells={rootCells} />
      </section>
      <section style={sectionStyle}>
        <div style={titleStyle}>{tl(LABELS.heap, lang)}</div>
        <ArrayGrid cells={objectCells} />
      </section>
      <section style={sectionStyle}>
        <div style={titleStyle}>{tl(LABELS.recentActions, lang)}</div>
        <div style={historyStyle}>
          {state.history.map((item, index) => {
            const action = ACTION_LABELS[item.action] ?? { en: item.action, ru: item.action };
            return (
              <div key={`${item.action}-${item.target}-${index}`} style={historyItemStyle}>
                <span style={monoStyle}>{tl(action, lang)}</span>
                <span>{item.target}</span>
                {item.detail && <span style={metaStyle}>{item.detail}</span>}
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
const statsStyle: CSSProperties = { display: 'flex', gap: 20, fontSize: 14 };
const sectionStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 6 };
const titleStyle: CSSProperties = { fontSize: 12, fontWeight: 700, opacity: 0.7 };
const rootKindStyle: CSSProperties = { fontSize: 13 };
const objectRowStyle: CSSProperties = {
  display: 'flex',
  flexWrap: 'wrap',
  alignItems: 'center',
  gap: '4px 12px',
  fontSize: 13,
};
const collectedStyle: CSSProperties = { opacity: 0.4, textDecoration: 'line-through' };
const leakedStyle: CSSProperties = { color: 'var(--danger, #e06c75)' };
const objectLabelStyle: CSSProperties = { fontFamily: 'monospace' };
const metaStyle: CSSProperties = { fontSize: 12, opacity: 0.7 };
const tagStyle: CSSProperties = {
  fontSize: 11,
  padding: '1px 6px',
  borderRadius: 4,
  background: 'var(--viz-badge)',
};
const leakTagStyle: CSSProperties = {
  fontSize: 11,
  fontWeight: 700,
  padding: '1px 6px',
  borderRadius: 4,
  background: 'var(--danger, #e06c75)',
  color: '#fff',
};
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
const monoStyle: CSSProperties = { fontFamily: 'monospace', fontWeight: 700, minWidth: 110 };
