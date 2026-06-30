import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { LinkedNodes, type LinkedNode } from '@app/primitives/LinkedNodes';
import { tl, useLang, type Localized } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to visualize happens-before edges.',
    ru: 'Запустите код, чтобы увидеть happens-before связи.',
  },
  sharedVariables: { en: 'shared variables', ru: 'shared variables' },
  threadViews: { en: 'thread local views', ru: 'локальные виды threads' },
  monitors: { en: 'monitors', ru: 'monitors' },
  timeline: { en: 'actions by thread', ru: 'действия по threads' },
  edges: { en: 'happens-before edges', ru: 'happens-before связи' },
  kind: { en: 'kind', ru: 'тип' },
  value: { en: 'value', ru: 'значение' },
  lastWriter: { en: 'last writer', ru: 'последний writer' },
  owner: { en: 'owner', ru: 'owner' },
  free: { en: 'free', ru: 'свободен' },
  lastRelease: { en: 'last release', ru: 'последний release' },
  status: { en: 'status', ru: 'статус' },
  refreshedBy: { en: 'refreshed by', ru: 'обновлён через' },
  none: { en: 'none', ru: 'нет' },
  unset: { en: 'unset', ru: 'не задано' },
};

const KIND_LABELS: Record<string, Localized> = {
  PLAIN: { en: 'plain field', ru: 'обычное поле' },
  VOLATILE: { en: 'volatile field', ru: 'volatile field' },
};

const STATUS_LABELS: Record<string, Localized> = {
  READY: { en: 'ready', ru: 'готов' },
  WROTE_PLAIN: { en: 'wrote plain', ru: 'записал plain' },
  READ_STALE: { en: 'read stale', ru: 'прочитал устаревшее' },
  READ_VISIBLE: { en: 'read visible', ru: 'прочитал видимое' },
  VOLATILE_WRITE: { en: 'volatile write', ru: 'volatile-запись' },
  VOLATILE_READ: { en: 'volatile read', ru: 'volatile-чтение' },
  LOCKED: { en: 'locked', ru: 'в lock' },
  UNLOCKED: { en: 'unlocked', ru: 'выполнил unlock' },
  STARTED_CHILD: { en: 'called start()', ru: 'вызвал start()' },
  STARTED: { en: 'started', ru: 'запущен' },
  TERMINATED: { en: 'terminated', ru: 'завершён' },
  JOINED: { en: 'joined', ru: 'join выполнен' },
};

const ACTION_LABELS: Record<string, Localized> = {
  PLAIN_WRITE: { en: 'plain write', ru: 'обычная запись' },
  PLAIN_READ: { en: 'plain read', ru: 'обычное чтение' },
  VOLATILE_WRITE: { en: 'volatile write', ru: 'volatile-запись' },
  VOLATILE_READ: { en: 'volatile read', ru: 'volatile-чтение' },
  MONITOR_ACQUIRE: { en: 'lock', ru: 'lock' },
  MONITOR_RELEASE: { en: 'unlock', ru: 'unlock' },
  THREAD_START: { en: 'start()', ru: 'start()' },
  THREAD_STARTED: { en: 'begins', ru: 'начинается' },
  THREAD_TERMINATED: { en: 'terminates', ru: 'завершается' },
  THREAD_JOIN: { en: 'join()', ru: 'join()' },
};

const EDGE_LABELS: Record<string, Localized> = {
  PROGRAM_ORDER: { en: 'program order', ru: 'program order' },
  VOLATILE: { en: 'volatile write -> read', ru: 'volatile запись -> чтение' },
  MONITOR: { en: 'unlock -> lock', ru: 'unlock -> lock' },
  THREAD_START: { en: 'start() edge', ru: 'start() связь' },
  THREAD_JOIN: { en: 'join() edge', ru: 'join() связь' },
};

interface VariableSnapshot {
  name: string;
  kind: string;
  value: string | number | boolean;
  lastWriter: string;
}

interface LocalValue {
  name: string;
  value: string | number | boolean;
}

interface ThreadSnapshot {
  name: string;
  status: string;
  lastRefreshKind: string;
  lastRefreshSourceThread: string;
  localValues: LocalValue[];
}

interface MonitorSnapshot {
  name: string;
  owner: string;
  lastReleaseThread: string;
}

interface ActionSnapshot {
  id: string;
  thread: string;
  type: string;
  target: string;
  value: string | number | boolean;
}

interface EdgeSnapshot {
  from: string;
  to: string;
  kind: string;
  detail: string;
}

interface HappensBeforeState {
  name: string;
  variables: VariableSnapshot[];
  threads: ThreadSnapshot[];
  monitors: MonitorSnapshot[];
  actions: ActionSnapshot[];
  edges: EdgeSnapshot[];
}

export default function HappensBeforeVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as HappensBeforeState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const actionById = new Map(state.actions.map((action) => [action.id, action]));

  const variableCells: ArrayCell[] = state.variables.map((variable) => {
    const kind = KIND_LABELS[variable.kind] ?? { en: variable.kind, ru: variable.kind };
    return {
      key: variable.name,
      label: variable.name,
      highlighted: highlight.has(`variable:${variable.name}`),
      content: (
        <div style={rowContentStyle}>
          <strong style={valueStyle}>{formatValue(variable.value, lang)}</strong>
          <span style={metaStyle}>{tl(LABELS.kind, lang)}: {tl(kind, lang)}</span>
          <span style={metaStyle}>
            {tl(LABELS.lastWriter, lang)}: {variable.lastWriter || tl(LABELS.none, lang)}
          </span>
        </div>
      ),
    };
  });

  const threadCells: ArrayCell[] = state.threads.map((thread) => {
    const status = STATUS_LABELS[thread.status] ?? { en: thread.status, ru: thread.status };
    const refresh = thread.lastRefreshKind
      ? `${thread.lastRefreshKind}${thread.lastRefreshSourceThread ? ` / ${thread.lastRefreshSourceThread}` : ''}`
      : tl(LABELS.none, lang);
    return {
      key: thread.name,
      label: thread.name,
      highlighted: highlight.has(`thread:${thread.name}`),
      content: (
        <div style={threadContentStyle}>
          <span>
            {tl(LABELS.status, lang)}: <strong>{tl(status, lang)}</strong>
          </span>
          <span>{tl(LABELS.refreshedBy, lang)}: {refresh}</span>
          <span style={monoStyle}>
            {thread.localValues.map((item) => `${item.name}=${formatValue(item.value, lang)}`).join(', ')}
          </span>
        </div>
      ),
    };
  });

  const monitorCells: ArrayCell[] = state.monitors.map((monitor) => ({
    key: monitor.name,
    label: monitor.name,
    highlighted: highlight.has(`monitor:${monitor.name}`),
    content: (
      <div style={rowContentStyle}>
        <span>
          {tl(LABELS.owner, lang)}:{' '}
          <strong>{monitor.owner || tl(LABELS.free, lang)}</strong>
        </span>
        <span style={metaStyle}>
          {tl(LABELS.lastRelease, lang)}: {monitor.lastReleaseThread || tl(LABELS.none, lang)}
        </span>
      </div>
    ),
  }));

  return (
    <div style={wrapStyle}>
      <section style={sectionStyle}>
        <div style={titleStyle}>{tl(LABELS.sharedVariables, lang)}</div>
        <ArrayGrid cells={variableCells} />
      </section>

      <section style={sectionStyle}>
        <div style={titleStyle}>{tl(LABELS.threadViews, lang)}</div>
        <ArrayGrid cells={threadCells} />
      </section>

      {monitorCells.length > 0 && (
        <section style={sectionStyle}>
          <div style={titleStyle}>{tl(LABELS.monitors, lang)}</div>
          <ArrayGrid cells={monitorCells} />
        </section>
      )}

      <section style={sectionStyle}>
        <div style={titleStyle}>{tl(LABELS.timeline, lang)}</div>
        <div style={timelineStyle}>
          {state.threads.map((thread) => {
            const nodes: LinkedNode[] = state.actions
              .filter((action) => action.thread === thread.name)
              .map((action) => ({
                id: action.id,
                title: `${action.id} ${tl(actionLabel(action.type), lang)}`,
                subtitle: actionSubtitle(action, lang),
                highlighted: highlight.has(`action:${action.id}`),
              }));
            return (
              <div key={thread.name} style={timelineRowStyle}>
                <span style={threadNameStyle}>{thread.name}</span>
                <LinkedNodes nodes={nodes} />
              </div>
            );
          })}
        </div>
      </section>

      <section style={sectionStyle}>
        <div style={titleStyle}>{tl(LABELS.edges, lang)}</div>
        <div style={edgeListStyle}>
          {state.edges.map((edge, index) => {
            const edgeLabel = EDGE_LABELS[edge.kind] ?? { en: edge.kind, ru: edge.kind };
            return (
              <div key={`${edge.from}-${edge.to}-${index}`} style={edgeItemStyle}>
                <span style={edgeKindStyle}>{tl(edgeLabel, lang)}</span>
                <span style={monoStyle}>{actionText(actionById.get(edge.from), lang)}</span>
                <span style={arrowStyle}>-&gt;</span>
                <span style={monoStyle}>{actionText(actionById.get(edge.to), lang)}</span>
              </div>
            );
          })}
          {state.edges.length === 0 && <div style={emptyStyle}>{tl(LABELS.none, lang)}</div>}
        </div>
      </section>
    </div>
  );
}

function actionLabel(type: string): Localized {
  return ACTION_LABELS[type] ?? { en: type, ru: type };
}

function actionSubtitle(action: ActionSnapshot, lang: 'en' | 'ru') {
  const value = formatValue(action.value, lang);
  return value ? `${action.target}=${value}` : action.target;
}

function actionText(action: ActionSnapshot | undefined, lang: 'en' | 'ru') {
  if (!action) return '?';
  return `${action.thread}:${action.id} ${tl(actionLabel(action.type), lang)}`;
}

function formatValue(value: string | number | boolean, lang: 'en' | 'ru') {
  if (value === 'unset') return tl(LABELS.unset, lang);
  if (value === '') return '';
  return String(value);
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 12 };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
const sectionStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 6 };
const titleStyle: CSSProperties = { fontSize: 12, fontWeight: 700, opacity: 0.72 };
const rowContentStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'baseline',
  flexWrap: 'wrap',
  gap: '4px 12px',
  fontSize: 13,
};
const threadContentStyle: CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: 3,
  fontSize: 13,
};
const valueStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 16 };
const metaStyle: CSSProperties = { fontSize: 12, opacity: 0.7 };
const monoStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 12 };
const timelineStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 8 };
const timelineRowStyle: CSSProperties = {
  display: 'flex',
  gap: 8,
  alignItems: 'center',
  flexWrap: 'wrap',
};
const threadNameStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontWeight: 700,
  minWidth: 72,
};
const edgeListStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 4 };
const edgeItemStyle: CSSProperties = {
  display: 'flex',
  gap: 8,
  alignItems: 'center',
  flexWrap: 'wrap',
  padding: '4px 8px',
  borderRadius: 6,
  background: 'var(--viz-box)',
  fontSize: 13,
};
const edgeKindStyle: CSSProperties = {
  minWidth: 136,
  fontWeight: 700,
  color: 'var(--accent)',
};
const arrowStyle: CSSProperties = { opacity: 0.65 };
const emptyStyle: CSSProperties = { opacity: 0.5, fontSize: 13, padding: 6 };
