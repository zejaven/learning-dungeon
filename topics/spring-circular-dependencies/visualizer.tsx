import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { LinkedNodes, type LinkedNode } from '@app/primitives/LinkedNodes';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to visualize Spring bean creation.',
    ru: 'Запустите код, чтобы увидеть создание Spring beans.',
  },
  phase: { en: 'phase', ru: 'phase' },
  stack: { en: 'creation stack', ru: 'creation stack' },
  noStack: { en: 'stack is empty', ru: 'stack пуст' },
  noDeps: { en: 'no outgoing dependencies', ru: 'нет исходящих dependencies' },
  statusDefined: { en: 'defined', ru: 'defined' },
  statusCreating: { en: 'creating', ru: 'creating' },
  statusReady: { en: 'ready', ru: 'ready' },
  statusFailed: { en: 'failed', ru: 'failed' },
  kindConstructor: { en: 'constructor', ru: 'constructor' },
  kindLazy: { en: '@Lazy', ru: '@Lazy' },
  kindProvider: { en: 'ObjectProvider', ru: 'ObjectProvider' },
  depPending: { en: 'pending', ru: 'pending' },
  depResolving: { en: 'resolving', ru: 'resolving' },
  depResolved: { en: 'resolved', ru: 'resolved' },
  depDeferred: { en: 'deferred', ru: 'deferred' },
  depFailed: { en: 'failed', ru: 'failed' },
};

type BeanStatus = 'defined' | 'creating' | 'ready' | 'failed';
type DependencyKind = 'constructor' | 'lazy' | 'provider';
type DependencyStatus = 'pending' | 'resolving' | 'resolved' | 'deferred' | 'failed';

interface BeanState {
  name: string;
  status: BeanStatus;
}

interface DependencyState {
  from: string;
  to: string;
  kind: DependencyKind;
  status: DependencyStatus;
}

interface SpringBeanState {
  name: string;
  phase: 'defined' | 'refreshing' | 'ready' | 'failed';
  beans: BeanState[];
  dependencies: DependencyState[];
  stack: string[];
}

export default function SpringCircularDependenciesVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as SpringBeanState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const byName = new Map(state.beans.map((bean) => [bean.name, bean]));
  const stackNodes: LinkedNode[] = state.stack.map((name) => {
    const bean = byName.get(name);
    return {
      id: name,
      title: name,
      subtitle: bean ? statusText(bean.status, lang) : undefined,
      highlighted: highlight.has(`bean:${name}`) || highlight.has(`stack:${name}`),
    };
  });

  const cells: ArrayCell[] = state.beans.map((bean) => {
    const deps = state.dependencies.filter((dependency) => dependency.from === bean.name);
    return {
      key: bean.name,
      label: bean.name,
      highlighted: highlight.has(`bean:${bean.name}`) || bean.status === 'creating',
      content: (
        <div style={rowContentStyle}>
          <StatusPill status={bean.status} lang={lang} />
          <div style={depsStyle}>
            {deps.length === 0 ? (
              <span style={emptyStyle}>{tl(LABELS.noDeps, lang)}</span>
            ) : (
              deps.map((dependency) => (
                <DependencyChip
                  key={`${dependency.from}->${dependency.to}-${dependency.kind}`}
                  dependency={dependency}
                  highlighted={highlight.has(`edge:${dependency.from}->${dependency.to}`)}
                  lang={lang}
                />
              ))
            )}
          </div>
        </div>
      ),
    };
  });

  return (
    <div style={wrapStyle}>
      <div style={topBarStyle}>
        <span style={labelStyle}>{tl(LABELS.phase, lang)}</span>
        <span style={{ ...phaseStyle, ...phaseColor(state.phase) }}>{state.phase}</span>
      </div>
      <div style={sectionStyle}>
        <div style={sectionTitleStyle}>{tl(LABELS.stack, lang)}</div>
        {stackNodes.length === 0 ? (
          <div style={emptyStackStyle}>{tl(LABELS.noStack, lang)}</div>
        ) : (
          <LinkedNodes nodes={stackNodes} />
        )}
      </div>
      <ArrayGrid cells={cells} />
    </div>
  );
}

function StatusPill({ status, lang }: { status: BeanStatus; lang: Lang }) {
  return (
    <span style={{ ...pillStyle, ...statusColor(status) }}>
      {statusText(status, lang)}
    </span>
  );
}

function DependencyChip({
  dependency,
  highlighted,
  lang,
}: {
  dependency: DependencyState;
  highlighted: boolean;
  lang: Lang;
}) {
  return (
    <span style={{ ...depStyle, ...(highlighted ? depHighlightStyle : {}) }}>
      <strong>{kindText(dependency.kind, lang)}</strong>
      <span>{dependency.to}</span>
      <em>{dependencyStatusText(dependency.status, lang)}</em>
    </span>
  );
}

function statusText(status: BeanStatus, lang: Lang): string {
  if (status === 'creating') return tl(LABELS.statusCreating, lang);
  if (status === 'ready') return tl(LABELS.statusReady, lang);
  if (status === 'failed') return tl(LABELS.statusFailed, lang);
  return tl(LABELS.statusDefined, lang);
}

function kindText(kind: DependencyKind, lang: Lang): string {
  if (kind === 'lazy') return tl(LABELS.kindLazy, lang);
  if (kind === 'provider') return tl(LABELS.kindProvider, lang);
  return tl(LABELS.kindConstructor, lang);
}

function dependencyStatusText(status: DependencyStatus, lang: Lang): string {
  if (status === 'resolving') return tl(LABELS.depResolving, lang);
  if (status === 'resolved') return tl(LABELS.depResolved, lang);
  if (status === 'deferred') return tl(LABELS.depDeferred, lang);
  if (status === 'failed') return tl(LABELS.depFailed, lang);
  return tl(LABELS.depPending, lang);
}

function statusColor(status: BeanStatus): CSSProperties {
  if (status === 'creating') return { color: 'var(--accent)', borderColor: 'var(--accent)' };
  if (status === 'ready') return { color: 'var(--good)', borderColor: 'var(--good)' };
  if (status === 'failed') return { color: 'var(--bad)', borderColor: 'var(--bad)' };
  return {};
}

function phaseColor(phase: SpringBeanState['phase']): CSSProperties {
  if (phase === 'ready') return { color: 'var(--good)' };
  if (phase === 'failed') return { color: 'var(--bad)' };
  if (phase === 'refreshing') return { color: 'var(--accent)' };
  return {};
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 14 };
const topBarStyle: CSSProperties = { display: 'flex', alignItems: 'baseline', gap: 8 };
const labelStyle: CSSProperties = { fontSize: 12, opacity: 0.65 };
const phaseStyle: CSSProperties = { fontFamily: 'monospace', fontWeight: 700 };
const sectionStyle: CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: 6,
  padding: 8,
  border: '1px solid var(--border)',
  borderRadius: 8,
  background: 'var(--viz-box)',
};
const sectionTitleStyle: CSSProperties = { fontSize: 12, opacity: 0.7, fontWeight: 700 };
const rowContentStyle: CSSProperties = { display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' };
const depsStyle: CSSProperties = { display: 'flex', gap: 6, flexWrap: 'wrap' };
const pillStyle: CSSProperties = {
  minWidth: 70,
  textAlign: 'center',
  fontFamily: 'monospace',
  fontSize: 12,
  border: '1px solid var(--border)',
  borderRadius: 999,
  padding: '2px 8px',
};
const depStyle: CSSProperties = {
  display: 'inline-flex',
  alignItems: 'center',
  gap: 6,
  border: '1px solid var(--border)',
  borderRadius: 999,
  padding: '3px 8px',
  background: 'var(--viz-box)',
  fontSize: 12,
};
const depHighlightStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  boxShadow: '0 0 0 2px rgba(255,204,102,0.25)',
};
const emptyStyle: CSSProperties = { fontSize: 12, opacity: 0.45, fontStyle: 'italic' };
const emptyStackStyle: CSSProperties = { ...emptyStyle, padding: '4px 0' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
