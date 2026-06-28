import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to visualize resource cleanup.',
    ru: 'Запустите код, чтобы визуализировать очистку ресурсов.',
  },
  resources: { en: 'resources', ru: 'ресурсы' },
  exceptions: { en: 'exceptions', ru: 'исключения' },
  primary: { en: 'primary', ru: 'основное' },
  caught: { en: 'caught', ru: 'поймано' },
  suppressed: { en: 'suppressed', ru: 'suppressed' },
  none: { en: 'none', ru: 'нет' },
  closeOrder: { en: 'close order', ru: 'порядок close' },
  opened: { en: 'opened', ru: 'открыт' },
  closesWithError: { en: 'close throws', ru: 'close выбросит' },
  yes: { en: 'yes', ru: 'да' },
  no: { en: 'no', ru: 'нет' },
  statusOpen: { en: 'open', ru: 'открыт' },
  statusClosed: { en: 'closed', ru: 'закрыт' },
  statusCloseFailed: { en: 'close failed', ru: 'close упал' },
};

interface ResourceItem {
  name: string;
  status: string;
  openOrder: number;
  closeOrder: number | null;
  closeFails: boolean;
}

interface ResourceTraceState {
  phase: string;
  primaryException: string | null;
  caughtException: string | null;
  suppressedExceptions: string[];
  closeSequence: string[];
  resources: ResourceItem[];
}

export default function ResourceExceptionVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as ResourceTraceState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const cells: ArrayCell[] = state.resources.map((resource) => ({
    key: resource.name,
    label: `#${resource.openOrder}`,
    highlighted: highlight.has(`resource:${resource.name}`),
    content: <ResourceRow resource={resource} lang={lang} />,
  }));

  return (
    <div style={wrapStyle}>
      <section style={sectionStyle}>
        <div style={sectionTitleStyle}>{tl(LABELS.resources, lang)}</div>
        <ArrayGrid cells={cells} />
      </section>

      <section style={sectionStyle}>
        <div style={sectionTitleStyle}>{tl(LABELS.exceptions, lang)}</div>
        <ExceptionLine
          label={tl(LABELS.primary, lang)}
          value={state.primaryException}
          emptyValue={tl(LABELS.none, lang)}
          highlighted={highlight.has('exception:primary')}
        />
        <ExceptionLine
          label={tl(LABELS.caught, lang)}
          value={state.caughtException}
          emptyValue={tl(LABELS.none, lang)}
          highlighted={highlight.has('exception:caught')}
        />
        <ExceptionLine
          label={tl(LABELS.suppressed, lang)}
          value={
            state.suppressedExceptions.length === 0
              ? null
              : state.suppressedExceptions.join(', ')
          }
          emptyValue={tl(LABELS.none, lang)}
          highlighted={highlight.has('exception:suppressed')}
        />
      </section>

      <section style={sectionStyle}>
        <div style={sectionTitleStyle}>{tl(LABELS.closeOrder, lang)}</div>
        <div style={sequenceStyle}>
          {state.closeSequence.length === 0 ? tl(LABELS.none, lang) : state.closeSequence.join(' -> ')}
        </div>
      </section>
    </div>
  );
}

function ResourceRow({ resource, lang }: { resource: ResourceItem; lang: Lang }) {
  return (
    <div style={resourceRowStyle}>
      <div>
        <div style={resourceNameStyle}>{resource.name}</div>
        <div style={resourceMetaStyle}>
          {tl(LABELS.opened, lang)} #{resource.openOrder}
          {resource.closeOrder ? ` · close #${resource.closeOrder}` : ''}
        </div>
      </div>
      <div style={badgesStyle}>
        <span style={statusBadgeStyle}>{statusText(resource.status, lang)}</span>
        {resource.closeFails && (
          <span style={dangerBadgeStyle}>
            {tl(LABELS.closesWithError, lang)}: {tl(LABELS.yes, lang)}
          </span>
        )}
      </div>
    </div>
  );
}

function ExceptionLine({
  label,
  value,
  emptyValue,
  highlighted,
}: {
  label: string;
  value: string | null;
  emptyValue: string;
  highlighted: boolean;
}) {
  return (
    <div style={{ ...exceptionLineStyle, ...(highlighted ? exceptionHighlightStyle : {}) }}>
      <span style={exceptionLabelStyle}>{label}</span>
      <span style={exceptionValueStyle}>{value ?? emptyValue}</span>
    </div>
  );
}

function statusText(status: string, lang: Lang) {
  if (status === 'closed') return tl(LABELS.statusClosed, lang);
  if (status === 'close failed') return tl(LABELS.statusCloseFailed, lang);
  return tl(LABELS.statusOpen, lang);
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 12 };
const sectionStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 6 };
const sectionTitleStyle: CSSProperties = {
  fontSize: 11,
  textTransform: 'uppercase',
  opacity: 0.65,
  letterSpacing: 0,
};
const resourceRowStyle: CSSProperties = {
  width: '100%',
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'center',
  gap: 8,
};
const resourceNameStyle: CSSProperties = { fontFamily: 'monospace', fontWeight: 700 };
const resourceMetaStyle: CSSProperties = { fontSize: 12, opacity: 0.65 };
const badgesStyle: CSSProperties = { display: 'flex', gap: 6, flexWrap: 'wrap', justifyContent: 'flex-end' };
const statusBadgeStyle: CSSProperties = {
  fontSize: 12,
  padding: '2px 6px',
  borderRadius: 4,
  background: 'var(--viz-badge)',
};
const dangerBadgeStyle: CSSProperties = {
  fontSize: 12,
  padding: '2px 6px',
  borderRadius: 4,
  color: 'var(--accent)',
  background: 'rgba(255,204,102,0.10)',
};
const exceptionLineStyle: CSSProperties = {
  display: 'flex',
  gap: 8,
  alignItems: 'baseline',
  padding: '5px 8px',
  borderRadius: 6,
  background: 'var(--viz-box)',
};
const exceptionHighlightStyle: CSSProperties = { boxShadow: 'inset 2px 0 0 var(--accent)' };
const exceptionLabelStyle: CSSProperties = { width: 86, flexShrink: 0, opacity: 0.65, fontSize: 12 };
const exceptionValueStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 12, overflowWrap: 'anywhere' };
const sequenceStyle: CSSProperties = {
  padding: '6px 8px',
  borderRadius: 6,
  background: 'var(--viz-box)',
  fontFamily: 'monospace',
  fontSize: 12,
};
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
