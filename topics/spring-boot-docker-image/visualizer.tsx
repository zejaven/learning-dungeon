import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to watch a Dockerfile turn into layers, and to see what each rebuild actually costs.',
    ru: 'Запустите код, чтобы увидеть, как Dockerfile превращается в слои и во что на самом деле обходится каждая пересборка.',
  },
  contextTitle: { en: 'build context sent to the daemon', ru: 'контекст сборки, отправляемый демону' },
  contextOf: { en: 'of', ru: 'из' },
  ignoredTag: { en: 'ignored', ru: 'исключено' },
  layersTitle: { en: 'layers, in Dockerfile order', ru: 'слои, в порядке Dockerfile' },
  noLayers: { en: 'nothing built yet', ru: 'пока ничего не собрано' },
  cached: { en: 'CACHED', ru: 'ИЗ КЕША' },
  built: { en: 'built', ru: 'собран' },
  metadata: { en: 'metadata', ru: 'метаданные' },
  discarded: { en: 'discarded with its stage', ru: 'выброшен вместе с этапом' },
  stage: { en: 'stage', ru: 'этап' },
  finalStage: { en: 'this stage becomes the image', ru: 'этот этап и становится образом' },
  imageTitle: { en: 'image', ru: 'образ' },
  runsAs: { en: 'runs as', ru: 'работает от' },
  pushTitle: { en: 'last push', ru: 'последняя заливка' },
  uploaded: { en: 'uploaded', ru: 'загружено' },
  skipped: { en: 'already in the registry', ru: 'уже в реестре' },
  containerTitle: { en: 'container', ru: 'контейнер' },
  memoryLimit: { en: 'memory limit', ru: 'лимит памяти' },
  heap: { en: 'JVM heap', ru: 'куча JVM' },
  statBuilds: { en: 'builds', ru: 'сборок' },
  statBuilt: { en: 'layers rebuilt', ru: 'слоёв пересобрано' },
  statCached: { en: 'layers cached', ru: 'слоёв из кеша' },
  statContext: { en: 'context sent', ru: 'контекста отправлено' },
  statUploaded: { en: 'pushed', ru: 'залито' },
  mb: { en: 'MB', ru: 'МБ' },
  under1: { en: '<1', ru: '<1' },
};

interface ContextEntry {
  path: string;
  sizeMb: number;
  ignored: boolean;
}
interface BuildContext {
  totalMb: number;
  sentMb: number;
  entries: ContextEntry[];
}
interface LayerView {
  id: string;
  instruction: string;
  sizeMb: number;
  status: 'built' | 'cached';
  metadata: boolean;
  stage: string;
  inFinalImage: boolean;
}
interface ImageView {
  tag: string;
  sizeMb: number;
  user: string;
  entrypoint: string;
}
interface PushView {
  pushed: boolean;
  uploadedMb: number;
  skippedMb: number;
}
interface ContainerView {
  tag: string;
  user: string;
  memoryLimitMb: number;
  heapMb: number;
  heapPercent: number;
}
interface Stats {
  builds: number;
  layersBuilt: number;
  layersCached: number;
  contextSentMb: number;
  contextSavedMb: number;
  uploadedMb: number;
  skippedMb: number;
}
interface DockerState {
  tag: string;
  context: BuildContext;
  stages: string[];
  layers: LayerView[];
  image: ImageView | null;
  push: PushView;
  container: ContainerView | null;
  stats: Stats;
}

export default function DockerBuildVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as DockerState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const multiStage = state.stages.length > 1;

  return (
    <div style={wrapStyle}>
      <div style={sectionLabelStyle}>
        {tl(LABELS.contextTitle, lang)} — {mb(state.context.sentMb, lang)}{' '}
        {tl(LABELS.contextOf, lang)} {mb(state.context.totalMb, lang)}
      </div>
      <BoxGroup boxes={contextBoxes(state.context.entries, lang, highlight.has('context'))} />

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.layersTitle, lang)}</div>
        {state.layers.length === 0 ? (
          <div style={mutedStyle}>{tl(LABELS.noLayers, lang)}</div>
        ) : (
          <div style={stackStyle}>
            {state.layers.map((layer, i) => (
              <div key={layer.id}>
                {multiStage && layer.stage !== state.layers[i - 1]?.stage && (
                  <div style={stageStyle}>
                    {tl(LABELS.stage, lang)} <b>{layer.stage}</b>
                    {layer.inFinalImage
                      ? ` — ${tl(LABELS.finalStage, lang)}`
                      : ` — ${tl(LABELS.discarded, lang)}`}
                  </div>
                )}
                <LayerRow layer={layer} lang={lang} highlighted={highlight.has(`layer:${layer.id}`)} />
              </div>
            ))}
          </div>
        )}
      </div>

      <div style={rowStyle}>
        {state.image && (
          <div style={{ ...panelStyle, ...(highlight.has('image') ? panelHighlightStyle : {}) }}>
            <div style={panelLabelStyle}>{tl(LABELS.imageTitle, lang)}</div>
            <div style={panelValueStyle}>{state.image.tag}</div>
            <div style={panelDetailStyle}>{mb(state.image.sizeMb, lang)}</div>
            <div style={{ ...panelDetailStyle, color: state.image.user === 'root' ? 'var(--bad)' : 'var(--good)' }}>
              {tl(LABELS.runsAs, lang)} {state.image.user}
            </div>
          </div>
        )}

        {state.push.pushed && (
          <div style={{ ...panelStyle, ...(highlight.has('registry') ? panelHighlightStyle : {}) }}>
            <div style={panelLabelStyle}>{tl(LABELS.pushTitle, lang)}</div>
            <div style={{ ...panelValueStyle, color: 'var(--accent)' }}>
              {mb(state.push.uploadedMb, lang)}
            </div>
            <div style={panelDetailStyle}>{tl(LABELS.uploaded, lang)}</div>
            <div style={panelDetailStyle}>
              {mb(state.push.skippedMb, lang)} — {tl(LABELS.skipped, lang)}
            </div>
          </div>
        )}

        {state.container && (
          <div style={{ ...panelStyle, ...(highlight.has('container') ? panelHighlightStyle : {}) }}>
            <div style={panelLabelStyle}>{tl(LABELS.containerTitle, lang)}</div>
            <div style={panelValueStyle}>{state.container.tag}</div>
            <div
              style={{
                ...panelDetailStyle,
                color: state.container.user === 'root' ? 'var(--bad)' : 'var(--good)',
              }}
            >
              {tl(LABELS.runsAs, lang)} {state.container.user}
            </div>
            <div style={panelDetailStyle}>
              {tl(LABELS.memoryLimit, lang)} {mb(state.container.memoryLimitMb, lang)} ·{' '}
              {tl(LABELS.heap, lang)} {mb(state.container.heapMb, lang)} (
              {state.container.heapPercent}%)
            </div>
          </div>
        )}
      </div>

      <div style={statsStyle}>
        <Stat label={tl(LABELS.statBuilds, lang)} value={String(state.stats.builds)} />
        <Stat label={tl(LABELS.statBuilt, lang)} value={String(state.stats.layersBuilt)} />
        <Stat
          label={tl(LABELS.statCached, lang)}
          value={String(state.stats.layersCached)}
          color={state.stats.layersCached > 0 ? 'var(--good)' : undefined}
        />
        <Stat label={tl(LABELS.statContext, lang)} value={mb(state.stats.contextSentMb, lang)} />
        <Stat
          label={tl(LABELS.statUploaded, lang)}
          value={mb(state.stats.uploadedMb, lang)}
          color={state.stats.uploadedMb > 0 ? 'var(--accent)' : undefined}
        />
      </div>
    </div>
  );
}

function LayerRow({
  layer,
  lang,
  highlighted,
}: {
  layer: LayerView;
  lang: Lang;
  highlighted: boolean;
}) {
  const cached = layer.status === 'cached';
  return (
    <div
      style={{
        ...layerStyle,
        ...(highlighted ? layerHighlightStyle : {}),
        ...(layer.inFinalImage ? {} : layerDimStyle),
        borderLeftColor: cached ? 'var(--good)' : 'var(--accent)',
      }}
    >
      <span style={layerIdStyle}>{layer.id}</span>
      <span style={instructionStyle}>{layer.instruction}</span>
      <span style={layerSizeStyle}>
        {layer.metadata ? tl(LABELS.metadata, lang) : mb(layer.sizeMb, lang)}
      </span>
      <span style={{ ...layerStatusStyle, color: cached ? 'var(--good)' : 'var(--accent)' }}>
        {tl(cached ? LABELS.cached : LABELS.built, lang)}
      </span>
    </div>
  );
}

function contextBoxes(entries: ContextEntry[], lang: Lang, highlighted: boolean): Box[] {
  return entries.map((entry) => ({
    id: `ctx-${entry.path}`,
    title: entry.path,
    subtitle: entry.ignored ? tl(LABELS.ignoredTag, lang) : mb(entry.sizeMb, lang),
    dim: entry.ignored,
    highlighted: highlighted && !entry.ignored,
  }));
}

function Stat({ label, value, color }: { label: string; value: string; color?: string }) {
  return (
    <div style={statStyle}>
      <div style={statLabelStyle}>{label}</div>
      <div style={{ ...statValueStyle, color: color ?? 'inherit' }}>{value}</div>
    </div>
  );
}

/** Sizes are whole MB, so anything below one is shown as "<1" rather than "0". */
function mb(sizeMb: number, lang: Lang): string {
  const amount = sizeMb === 0 ? tl(LABELS.under1, lang) : String(sizeMb);
  return `${amount} ${tl(LABELS.mb, lang)}`;
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 14 };
const rowStyle: CSSProperties = { display: 'flex', gap: 10, flexWrap: 'wrap' };
const stackStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 4 };
const layerStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'baseline',
  gap: 10,
  padding: '4px 10px',
  borderRadius: 6,
  border: '1px solid var(--border)',
  borderLeft: '3px solid var(--border)',
  background: 'var(--viz-box)',
};
const layerHighlightStyle: CSSProperties = {
  background: 'var(--viz-highlight)',
  boxShadow: '0 0 0 2px rgba(255,204,102,0.35)',
};
const layerDimStyle: CSSProperties = { opacity: 0.45, borderStyle: 'dashed' };
const layerIdStyle: CSSProperties = { fontSize: 10, opacity: 0.5, fontFamily: 'monospace', width: 22 };
const instructionStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 12,
  flex: 1,
  overflowWrap: 'anywhere',
};
const layerSizeStyle: CSSProperties = { fontSize: 11, opacity: 0.7, fontFamily: 'monospace' };
const layerStatusStyle: CSSProperties = { fontSize: 11, fontWeight: 600, minWidth: 58, textAlign: 'right' };
const stageStyle: CSSProperties = { fontSize: 11, opacity: 0.6, margin: '8px 0 4px' };
const panelStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 8,
  padding: '6px 12px',
  background: 'var(--viz-box)',
  minWidth: 150,
};
const panelHighlightStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  background: 'var(--viz-highlight)',
};
const panelLabelStyle: CSSProperties = { fontSize: 10, opacity: 0.6 };
const panelValueStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 13, fontWeight: 600 };
const panelDetailStyle: CSSProperties = { fontSize: 11, opacity: 0.8 };
const statsStyle: CSSProperties = { display: 'flex', gap: 18, flexWrap: 'wrap' };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 16, fontWeight: 700, fontFamily: 'monospace' };
const sectionLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6, marginBottom: 6 };
const mutedStyle: CSSProperties = { fontSize: 12, opacity: 0.5, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
