import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { LinkedNodes, type LinkedNode } from '@app/primitives/LinkedNodes';
import { tl, useLang } from '@app/i18n';

const LABELS = {
  objects: { en: 'Heap objects', ru: 'Объекты в куче' },
  references: { en: 'References', ru: 'Ссылки' },
  queue: { en: 'ReferenceQueue', ru: 'ReferenceQueue' },
  queueEmpty: { en: '(empty)', ru: '(пусто)' },
  memoryOk: { en: 'memory: ok', ru: 'память: норма' },
  memoryLow: { en: 'memory: LOW', ru: 'память: НЕХВАТКА' },
  collected: { en: 'collected', ru: 'собран' },
  cleared: { en: 'cleared', ru: 'очищена' },
  runHint: {
    en: 'Run the code to visualize references and GC.',
    ru: 'Запустите код, чтобы увидеть ссылки и работу GC.',
  },
  reach: {
    strong: { en: 'strongly reachable', ru: 'сильно достижим' },
    soft: { en: 'softly reachable', ru: 'мягко достижим' },
    weak: { en: 'weakly reachable', ru: 'слабо достижим' },
    phantom: { en: 'phantom-reachable', ru: 'фантомно достижим' },
    unreachable: { en: 'unreachable', ru: 'недостижим' },
    collected: { en: 'collected', ru: 'собран' },
  },
} as const;

type Kind = 'strong' | 'soft' | 'weak' | 'phantom';
type Reach = keyof typeof LABELS.reach;

interface HeapObject {
  id: string;
  label: string;
  collected: boolean;
  reachability: Reach;
}
interface Reference {
  name: string;
  kind: Kind;
  target: string;
  cleared: boolean;
  queued: boolean;
}
interface RefHeapState {
  memoryLow: boolean;
  objects: HeapObject[];
  references: Reference[];
  queue: string[];
}

const KIND_COLOR: Record<Kind, string> = {
  strong: '#4caf50',
  soft: '#ffb74d',
  weak: '#64b5f6',
  phantom: '#b39ddb',
};

export default function RefHeapVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as RefHeapState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);

  const cells: ArrayCell[] = state.objects.map((o) => ({
    key: o.id,
    label: o.id,
    highlighted: highlight.has(`obj:${o.id}`),
    content: (
      <div style={{ ...objBoxStyle, ...(o.collected ? collectedStyle : {}) }}>
        <span style={objLabelStyle}>{o.label}</span>
        <span style={reachTagStyle}>{tl(LABELS.reach[o.reachability], lang)}</span>
      </div>
    ),
  }));

  const queueNodes: LinkedNode[] = state.queue.map((name) => ({
    id: `q-${name}`,
    title: name,
    highlighted: highlight.has(`ref:${name}`),
  }));

  return (
    <div style={wrapStyle}>
      <div style={{ ...badgeStyle, ...(state.memoryLow ? badgeLowStyle : {}) }}>
        {state.memoryLow ? tl(LABELS.memoryLow, lang) : tl(LABELS.memoryOk, lang)}
      </div>

      <Section title={tl(LABELS.references, lang)}>
        <div style={refRowStyle}>
          {state.references.map((r) => (
            <RefChip
              key={r.name}
              kind={r.kind}
              cleared={r.cleared}
              highlighted={highlight.has(`ref:${r.name}`)}
              text={`${r.name} (${r.kind}) → ${r.target}`}
              clearedLabel={tl(LABELS.cleared, lang)}
            />
          ))}
          {state.references.length === 0 && <span style={mutedStyle}>—</span>}
        </div>
      </Section>

      <Section title={tl(LABELS.objects, lang)}>
        <ArrayGrid cells={cells} />
      </Section>

      <Section title={tl(LABELS.queue, lang)}>
        {queueNodes.length > 0 ? (
          <LinkedNodes nodes={queueNodes} />
        ) : (
          <span style={mutedStyle}>{tl(LABELS.queueEmpty, lang)}</span>
        )}
      </Section>
    </div>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div style={sectionStyle}>
      <div style={sectionTitleStyle}>{title}</div>
      {children}
    </div>
  );
}

function RefChip({
  kind,
  cleared,
  highlighted,
  text,
  clearedLabel,
}: {
  kind: Kind;
  cleared: boolean;
  highlighted: boolean;
  text: string;
  clearedLabel: string;
}) {
  return (
    <span
      style={{
        ...chipStyle,
        borderColor: KIND_COLOR[kind],
        opacity: cleared ? 0.4 : 1,
        textDecoration: cleared ? 'line-through' : 'none',
        boxShadow: highlighted ? `0 0 0 2px ${KIND_COLOR[kind]}66` : 'none',
      }}
    >
      <span style={{ ...dotStyle, background: KIND_COLOR[kind] }} />
      {text}
      {cleared ? ` · ${clearedLabel}` : ''}
    </span>
  );
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 12 };
const sectionStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 6 };
const sectionTitleStyle: CSSProperties = { fontSize: 11, opacity: 0.6, textTransform: 'uppercase', letterSpacing: 0.5 };
const refRowStyle: CSSProperties = { display: 'flex', flexWrap: 'wrap', gap: 6 };
const chipStyle: CSSProperties = {
  display: 'inline-flex',
  alignItems: 'center',
  gap: 6,
  border: '1px solid var(--border)',
  borderRadius: 6,
  padding: '3px 8px',
  fontSize: 12,
  fontFamily: 'monospace',
  background: 'var(--viz-box)',
};
const dotStyle: CSSProperties = { width: 8, height: 8, borderRadius: '50%', flexShrink: 0 };
const objBoxStyle: CSSProperties = { display: 'flex', flexDirection: 'column', lineHeight: 1.2 };
const objLabelStyle: CSSProperties = { fontWeight: 600, fontSize: 13 };
const reachTagStyle: CSSProperties = { fontSize: 11, opacity: 0.7 };
const collectedStyle: CSSProperties = { opacity: 0.4, textDecoration: 'line-through' };
const badgeStyle: CSSProperties = {
  alignSelf: 'flex-start',
  fontSize: 12,
  fontFamily: 'monospace',
  padding: '2px 10px',
  borderRadius: 12,
  background: 'var(--viz-box)',
  border: '1px solid var(--border)',
};
const badgeLowStyle: CSSProperties = { color: 'var(--accent)', borderColor: 'var(--accent)', fontWeight: 700 };
const mutedStyle: CSSProperties = { fontSize: 12, opacity: 0.5 };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
