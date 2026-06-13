import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { LinkedNodes, type LinkedNode } from '@app/primitives/LinkedNodes';
import { tl, useLang } from '@app/i18n';

const LABELS = {
  capacity: { en: 'capacity', ru: 'ёмкость' },
  loadFactor: { en: 'load factor', ru: 'коэф. загрузки' },
  threshold: { en: 'threshold', ru: 'порог' },
  size: { en: 'size', ru: 'размер' },
  hash: { en: 'hash', ru: 'хэш' },
  runHint: {
    en: 'Run the code to visualize the map.',
    ru: 'Запустите код, чтобы визуализировать мапу.',
  },
};

interface BucketNode {
  key: string;
  value: string;
  hash: number;
}
interface Bucket {
  index: number;
  nodes: BucketNode[];
}
interface HashMapState {
  name: string;
  capacity: number;
  loadFactor: number;
  threshold: number;
  size: number;
  buckets: Bucket[];
}

export default function HashMapVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as HashMapState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);

  const cells: ArrayCell[] = state.buckets.map((bucket) => {
    const nodes: LinkedNode[] = bucket.nodes.map((n) => ({
      id: `${bucket.index}-${n.key}`,
      title: `${n.key} → ${n.value}`,
      subtitle: `${tl(LABELS.hash, lang)} ${n.hash}`,
      highlighted: highlight.has(`node:${n.key}`),
    }));
    return {
      key: bucket.index,
      label: `[${bucket.index}]`,
      highlighted: highlight.has(`bucket:${bucket.index}`),
      content: <LinkedNodes nodes={nodes} />,
    };
  });

  return (
    <div style={wrapStyle}>
      <div style={statsStyle}>
        <Stat label={tl(LABELS.capacity, lang)} value={state.capacity} />
        <Stat label={tl(LABELS.loadFactor, lang)} value={state.loadFactor} />
        <Stat label={tl(LABELS.threshold, lang)} value={state.threshold} />
        <Stat
          label={tl(LABELS.size, lang)}
          value={state.size}
          highlight={state.size > state.threshold}
        />
      </div>
      <ArrayGrid cells={cells} />
    </div>
  );
}

function Stat({ label, value, highlight }: { label: string; value: number; highlight?: boolean }) {
  return (
    <div style={statStyle}>
      <div style={statLabelStyle}>{label}</div>
      <div style={{ ...statValueStyle, color: highlight ? '#ff8a65' : '#e6edf3' }}>{value}</div>
    </div>
  );
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 12 };
const statsStyle: CSSProperties = { display: 'flex', gap: 16 };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 18, fontWeight: 700, fontFamily: 'monospace' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
