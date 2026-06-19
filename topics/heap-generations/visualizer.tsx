import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang } from '@app/i18n';

const LABELS = {
  tenuring: { en: 'tenuring threshold', ru: 'порог продвижения' },
  minorGc: { en: 'minor GCs', ru: 'minor GC' },
  majorGc: { en: 'major GCs', ru: 'major GC' },
  young: { en: 'Young generation', ru: 'Молодое поколение' },
  oldGen: { en: 'Old generation', ru: 'Старое поколение' },
  eden: { en: 'where objects are born', ru: 'где рождаются объекты' },
  survivorActive: { en: 'survivor (in use)', ru: 'survivor (активный)' },
  survivorEmpty: { en: 'survivor (empty)', ru: 'survivor (пустой)' },
  old: { en: 'tenured, long-lived', ru: 'tenured, долгоживущие' },
  age: { en: 'age', ru: 'возраст' },
  runHint: {
    en: 'Run the code to visualize the heap.',
    ru: 'Запустите код, чтобы визуализировать кучу.',
  },
};

interface HeapObject {
  id: string;
  label: string;
  age: number;
  dead: boolean;
}
interface Region {
  id: string;
  label: string;
  kind: 'eden' | 'survivor' | 'old';
  capacity: number;
  active: boolean;
  objects: HeapObject[];
}
interface HeapState {
  name: string;
  tenuringThreshold: number;
  minorGcCount: number;
  majorGcCount: number;
  regions: Region[];
}

export default function HeapGenerationsVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as HeapState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);

  const roleFor = (r: Region): string => {
    if (r.kind === 'eden') return tl(LABELS.eden, lang);
    if (r.kind === 'old') return tl(LABELS.old, lang);
    return r.active ? tl(LABELS.survivorActive, lang) : tl(LABELS.survivorEmpty, lang);
  };

  const cellFor = (r: Region): ArrayCell => {
    const boxes: Box[] = r.objects.map((o) => ({
      id: o.id,
      title: o.label,
      subtitle: `${tl(LABELS.age, lang)} ${o.age}`,
      highlighted: highlight.has(`obj:${o.id}`),
      dim: o.dead,
    }));
    const count = r.capacity >= 0 ? `${r.objects.length}/${r.capacity}` : `${r.objects.length}`;
    return {
      key: r.id,
      label: `${r.label} · ${count}`,
      highlighted: highlight.has(`region:${r.id}`),
      content: (
        <div style={regionBodyStyle}>
          <span style={roleStyle}>{roleFor(r)}</span>
          <BoxGroup boxes={boxes} />
        </div>
      ),
    };
  };

  const young = state.regions.filter((r) => r.kind !== 'old');
  const oldRegions = state.regions.filter((r) => r.kind === 'old');

  return (
    <div style={wrapStyle}>
      <div style={statsStyle}>
        <Stat label={tl(LABELS.tenuring, lang)} value={state.tenuringThreshold} />
        <Stat label={tl(LABELS.minorGc, lang)} value={state.minorGcCount} />
        <Stat label={tl(LABELS.majorGc, lang)} value={state.majorGcCount} />
      </div>

      <div style={groupStyle}>
        <div style={groupTitleStyle}>{tl(LABELS.young, lang)}</div>
        <ArrayGrid cells={young.map(cellFor)} />
      </div>

      <div style={groupStyle}>
        <div style={groupTitleStyle}>{tl(LABELS.oldGen, lang)}</div>
        <ArrayGrid cells={oldRegions.map(cellFor)} />
      </div>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <div style={statStyle}>
      <div style={statLabelStyle}>{label}</div>
      <div style={statValueStyle}>{value}</div>
    </div>
  );
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 14 };
const statsStyle: CSSProperties = { display: 'flex', gap: 16 };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = {
  fontSize: 18,
  fontWeight: 700,
  fontFamily: 'monospace',
  color: 'var(--text)',
};
const groupStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 6 };
const groupTitleStyle: CSSProperties = {
  fontSize: 12,
  fontWeight: 700,
  textTransform: 'uppercase',
  letterSpacing: 0.5,
  opacity: 0.55,
};
const regionBodyStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 3, width: '100%' };
const roleStyle: CSSProperties = { fontSize: 11, opacity: 0.5, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
