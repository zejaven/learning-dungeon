import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { LinkedNodes, type LinkedNode } from '@app/primitives/LinkedNodes';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  map: { en: 'map', ru: 'мапа' },
  capacity: { en: 'capacity', ru: 'ёмкость' },
  loadFactor: { en: 'load factor', ru: 'коэф. загрузки' },
  threshold: { en: 'threshold', ru: 'порог' },
  size: { en: 'size', ru: 'размер' },
  longestChain: { en: 'longest chain', ru: 'самая длинная цепочка' },
  hash: { en: 'hash', ru: 'хэш' },
  bucket: { en: 'bucket', ru: 'бакет' },
  runHint: {
    en: 'Run the code to see which bucket get() checks.',
    ru: 'Запустите код, чтобы увидеть, какой бакет проверяет get().',
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

export default function HashMapLookupComplexityVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as HashMapState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const longestChain = state.buckets.reduce((max, bucket) => Math.max(max, bucket.nodes.length), 0);
  const cells: ArrayCell[] = state.buckets.map((bucket) => ({
    key: bucket.index,
    label: `${tl(LABELS.bucket, lang)} ${bucket.index}`,
    highlighted: highlight.has(`bucket:${bucket.index}`),
    content: <LinkedNodes nodes={nodesFor(bucket, highlight, lang)} />,
  }));

  return (
    <div style={wrapStyle}>
      <div style={titleStyle}>
        {tl(LABELS.map, lang)}: <code>{state.name}</code>
      </div>
      <div style={statsStyle}>
        <Stat label={tl(LABELS.capacity, lang)} value={state.capacity} />
        <Stat label={tl(LABELS.loadFactor, lang)} value={state.loadFactor} />
        <Stat label={tl(LABELS.threshold, lang)} value={state.threshold} />
        <Stat label={tl(LABELS.size, lang)} value={state.size} highlight={state.size > state.threshold} />
        <Stat label={tl(LABELS.longestChain, lang)} value={longestChain} highlight={longestChain > 1} />
      </div>
      <ArrayGrid cells={cells} />
    </div>
  );
}

function nodesFor(bucket: Bucket, highlight: Set<string>, lang: Lang): LinkedNode[] {
  return bucket.nodes.map((node) => ({
    id: `${bucket.index}-${node.key}`,
    title: `${node.key} -> ${node.value}`,
    subtitle: `${tl(LABELS.hash, lang)} ${node.hash}`,
    highlighted: highlight.has(`node:${node.key}`),
  }));
}

function Stat({ label, value, highlight }: { label: string; value: number; highlight?: boolean }) {
  return (
    <div style={statStyle}>
      <div style={statLabelStyle}>{label}</div>
      <div style={{ ...statValueStyle, color: highlight ? 'var(--accent)' : 'var(--text)' }}>{value}</div>
    </div>
  );
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 12 };
const titleStyle: CSSProperties = { fontSize: 13, fontWeight: 700, opacity: 0.8 };
const statsStyle: CSSProperties = { display: 'flex', gap: 16, flexWrap: 'wrap' };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 18, fontWeight: 700, fontFamily: 'monospace' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
