import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { LinkedNodes, type LinkedNode } from '@app/primitives/LinkedNodes';
import { tl, useLang } from '@app/i18n';

const LABELS = {
  requested: { en: 'requested', ru: 'запрошен' },
  active: { en: 'active loader', ru: 'активный загрузчик' },
  empty: { en: 'no classes loaded', ru: 'классы не загружены' },
  none: { en: '—', ru: '—' },
  runHint: {
    en: 'Run the code to visualize class loading.',
    ru: 'Запустите код, чтобы увидеть загрузку классов.',
  },
  phase: {
    request: { en: 'request received', ru: 'запрос получен' },
    delegate: { en: 'delegating to parent', ru: 'делегирование родителю' },
    cache: { en: 'served from cache', ru: 'выдано из кэша' },
    define: { en: 'class defined', ru: 'класс определён' },
    notfound: { en: 'class not found', ru: 'класс не найден' },
    idle: { en: 'ready', ru: 'готово' },
  } as Record<string, { en: string; ru: string }>,
};

interface Loader {
  name: string;
  level: number;
  knows: string[];
  loaded: string[];
}
interface ClassLoaderState {
  requested: string | null;
  active: string | null;
  phase: string;
  loaders: Loader[];
}

export default function ClassLoaderVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as ClassLoaderState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const phaseLabel = LABELS.phase[state.phase] ?? LABELS.phase.idle;

  const cells: ArrayCell[] = state.loaders.map((loader) => {
    const nodes: LinkedNode[] = loader.loaded.map((cls) => ({
      id: `${loader.name}-${cls}`,
      title: cls,
      highlighted: highlight.has(`class:${cls}`),
    }));
    return {
      key: loader.name,
      label: loader.name,
      highlighted: highlight.has(`loader:${loader.name}`),
      content:
        nodes.length > 0 ? (
          <LinkedNodes nodes={nodes} />
        ) : (
          <span style={emptyStyle}>{tl(LABELS.empty, lang)}</span>
        ),
    };
  });

  return (
    <div style={wrapStyle}>
      <div style={statsStyle}>
        <Stat label={tl(LABELS.requested, lang)} value={state.requested ?? tl(LABELS.none, lang)} />
        <Stat label={tl(LABELS.active, lang)} value={state.active ?? tl(LABELS.none, lang)} />
        <Stat label={tl(phaseLabel, lang)} value="" />
      </div>
      <ArrayGrid cells={cells} />
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div style={statStyle}>
      <div style={statLabelStyle}>{label}</div>
      {value && <div style={statValueStyle}>{value}</div>}
    </div>
  );
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 12 };
const statsStyle: CSSProperties = { display: 'flex', gap: 20, alignItems: 'flex-end' };
const statStyle: CSSProperties = { textAlign: 'left' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 16, fontWeight: 700, fontFamily: 'monospace' };
const emptyStyle: CSSProperties = { fontSize: 12, opacity: 0.4, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
