import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { LinkedNodes, type LinkedNode } from '@app/primitives/LinkedNodes';
import { tl, useLang, type Localized } from '@app/i18n';

const LABELS = {
  title: { en: 'HashSet-style buckets', ru: 'Бакеты в стиле HashSet' },
  capacity: { en: 'capacity', ru: 'ёмкость' },
  size: { en: 'size', ru: 'размер' },
  lastOperation: { en: 'last operation', ru: 'последняя операция' },
  result: { en: 'result', ru: 'результат' },
  left: { en: 'left', ru: 'левый' },
  right: { en: 'right', ru: 'правый' },
  value: { en: 'value', ru: 'значение' },
  equals: { en: 'equals()', ru: 'equals()' },
  hashCode: { en: 'hashCode', ru: 'hashCode' },
  storedHash: { en: 'stored hash', ru: 'сохранённый hash' },
  currentHash: { en: 'current hash', ru: 'текущий hash' },
  bucket: { en: 'bucket', ru: 'бакет' },
  leftEqualsRight: { en: 'left.equals(right)', ru: 'left.equals(right)' },
  rightEqualsLeft: { en: 'right.equals(left)', ru: 'right.equals(left)' },
  runHint: {
    en: 'Run the code to visualize the equals/hashCode contract.',
    ru: 'Запустите код, чтобы визуализировать контракт equals/hashCode.',
  },
};

const RESULTS: Record<string, Localized> = {
  'contract-ok': { en: 'contract OK', ru: 'контракт соблюдён' },
  'contract-broken': { en: 'contract broken', ru: 'контракт нарушен' },
  'not-equal': { en: 'not equal', ru: 'не равны' },
  added: { en: 'added', ru: 'добавлено' },
  'duplicate-rejected': { en: 'duplicate rejected', ru: 'дубликат отклонён' },
  'equal-in-another-bucket': { en: 'equal value in another bucket', ru: 'равное значение в другом бакете' },
  found: { en: 'found', ru: 'найдено' },
  missing: { en: 'missing', ru: 'не найдено' },
  symmetric: { en: 'symmetric', ru: 'симметрично' },
  'symmetry-broken': { en: 'symmetry broken', ru: 'симметрия нарушена' },
};

interface BucketNode {
  id: string;
  label: string;
  storedHash: number;
  currentHash: number;
  storedBucket: number;
}

interface Bucket {
  index: number;
  nodes: BucketNode[];
}

interface LastOp {
  kind: string;
  left?: string;
  right?: string;
  value?: string;
  equal?: boolean;
  leftHash?: number;
  rightHash?: number;
  hashesMatch?: boolean;
  leftEqualsRight?: boolean;
  rightEqualsLeft?: boolean;
  hash?: number;
  bucket?: number;
  result?: string;
}

interface EqualityState {
  name: string;
  capacity: number;
  size: number;
  lastOp: LastOp;
  buckets: Bucket[];
}

export default function EqualityContractVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as EqualityState | undefined;

  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const cells: ArrayCell[] = state.buckets.map((bucket) => {
    const nodes: LinkedNode[] = bucket.nodes.map((node) => {
      const hashChanged = node.storedHash !== node.currentHash;
      return {
        id: node.id,
        title: node.label,
        subtitle: hashChanged
          ? `${tl(LABELS.storedHash, lang)} ${node.storedHash}; ${tl(LABELS.currentHash, lang)} ${node.currentHash}`
          : `${tl(LABELS.hashCode, lang)} ${node.storedHash}`,
        highlighted: highlight.has(`node:${node.id}`),
      };
    });

    return {
      key: bucket.index,
      label: `[${bucket.index}]`,
      highlighted: highlight.has(`bucket:${bucket.index}`),
      content: <LinkedNodes nodes={nodes} />,
    };
  });

  return (
    <div style={wrapStyle}>
      <div style={headerStyle}>
        <div style={titleStyle}>{tl(LABELS.title, lang)}</div>
        <div style={statsStyle}>
          <Stat label={tl(LABELS.capacity, lang)} value={String(state.capacity)} />
          <Stat label={tl(LABELS.size, lang)} value={String(state.size)} />
        </div>
      </div>
      <LastOperation op={state.lastOp} lang={lang} />
      <ArrayGrid cells={cells} />
    </div>
  );
}

function LastOperation({ op, lang }: { op: LastOp; lang: 'en' | 'ru' }) {
  const rows = operationRows(op, lang);
  return (
    <div style={opStyle}>
      <div style={opTitleStyle}>{tl(LABELS.lastOperation, lang)}: {op.kind}</div>
      <div style={rowsStyle}>
        {rows.map((row) => (
          <div key={row.label} style={rowStyle}>
            <span style={rowLabelStyle}>{row.label}</span>
            <span style={rowValueStyle}>{row.value}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function operationRows(op: LastOp, lang: 'en' | 'ru') {
  const rows: Array<{ label: string; value: string }> = [];
  const push = (label: Localized, value: unknown) => {
    if (value !== undefined && value !== null && value !== '') {
      rows.push({ label: tl(label, lang), value: String(value) });
    }
  };

  push(LABELS.left, op.left);
  push(LABELS.right, op.right);
  push(LABELS.value, op.value);
  push(LABELS.equals, op.equal);
  push(LABELS.leftEqualsRight, op.leftEqualsRight);
  push(LABELS.rightEqualsLeft, op.rightEqualsLeft);
  push(LABELS.hashCode, hashPair(op));
  push(LABELS.bucket, op.bucket);
  push(LABELS.result, op.result ? resultText(op.result, lang) : undefined);
  return rows;
}

function hashPair(op: LastOp) {
  if (op.leftHash !== undefined && op.rightHash !== undefined) {
    return `${op.leftHash} / ${op.rightHash}`;
  }
  return op.hash;
}

function resultText(result: string, lang: 'en' | 'ru') {
  return RESULTS[result] ? tl(RESULTS[result], lang) : result;
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div style={statStyle}>
      <div style={statLabelStyle}>{label}</div>
      <div style={statValueStyle}>{value}</div>
    </div>
  );
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 12 };
const headerStyle: CSSProperties = { display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12 };
const titleStyle: CSSProperties = { fontSize: 13, fontWeight: 700, opacity: 0.8 };
const statsStyle: CSSProperties = { display: 'flex', gap: 16, alignItems: 'flex-start', flexWrap: 'wrap' };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 16, fontWeight: 700, fontFamily: 'monospace' };
const opStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 6,
  padding: '8px 10px',
  background: 'var(--viz-box)',
};
const opTitleStyle: CSSProperties = { fontSize: 12, fontWeight: 700, marginBottom: 6 };
const rowsStyle: CSSProperties = { display: 'flex', flexWrap: 'wrap', gap: '6px 14px' };
const rowStyle: CSSProperties = { display: 'flex', gap: 5, alignItems: 'baseline', fontSize: 12 };
const rowLabelStyle: CSSProperties = { opacity: 0.6 };
const rowValueStyle: CSSProperties = { fontFamily: 'monospace', fontWeight: 700 };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
