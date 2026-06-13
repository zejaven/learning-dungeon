import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { LinkedNodes, type LinkedNode } from '@app/primitives/LinkedNodes';

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
  const state = event?.state as HashMapState | undefined;
  if (!state) {
    return <div style={hintStyle}>Run the code to visualize the map.</div>;
  }

  const highlight = new Set(event?.highlight ?? []);

  const cells: ArrayCell[] = state.buckets.map((bucket) => {
    const nodes: LinkedNode[] = bucket.nodes.map((n) => ({
      id: `${bucket.index}-${n.key}`,
      title: `${n.key} → ${n.value}`,
      subtitle: `hash ${n.hash}`,
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
        <Stat label="capacity" value={state.capacity} />
        <Stat label="load factor" value={state.loadFactor} />
        <Stat label="threshold" value={state.threshold} />
        <Stat label="size" value={state.size} highlight={state.size > state.threshold} />
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
