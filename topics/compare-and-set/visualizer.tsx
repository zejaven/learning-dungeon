import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { LinkedNodes, type LinkedNode } from '@app/primitives/LinkedNodes';
import { tl, useLang, type Localized } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to visualize compare-and-set.',
    ru: 'Запустите код, чтобы визуализировать compare-and-set.',
  },
  slot: { en: 'atomic slot', ru: 'atomic slot' },
  revision: { en: 'revision', ru: 'revision' },
  lastResult: { en: 'last result', ru: 'последний результат' },
  none: { en: 'none', ru: 'нет' },
  threads: { en: 'threads', ru: 'threads' },
  history: { en: 'recent CAS history', ru: 'последняя история CAS' },
  read: { en: 'read', ru: 'прочитано' },
  rev: { en: 'rev', ru: 'rev' },
  expected: { en: 'expected', ru: 'expected' },
  update: { en: 'update', ru: 'update' },
  attempts: { en: 'attempts', ru: 'попытки' },
  before: { en: 'before', ru: 'до' },
  after: { en: 'after', ru: 'после' },
};

const STATUS_LABELS: Record<string, Localized> = {
  READY: { en: 'ready', ru: 'готов' },
  OBSERVED: { en: 'observed', ru: 'прочитал' },
  ATTEMPT: { en: 'attempting CAS', ru: 'пытается CAS' },
  SUCCESS: { en: 'CAS succeeded', ru: 'CAS успешен' },
  FAILED: { en: 'CAS failed', ru: 'CAS неуспешен' },
  RETRYING: { en: 'retrying', ru: 'повторяет' },
  ABA_RISK: { en: 'ABA risk', ru: 'риск ABA' },
};

const ACTION_LABELS: Record<string, Localized> = {
  CREATE: { en: 'created', ru: 'создано' },
  READ: { en: 'read', ru: 'чтение' },
  RETRY_READ: { en: 'retry read', ru: 'повторное чтение' },
  CAS_SUCCESS: { en: 'CAS success', ru: 'CAS успешен' },
  CAS_FAILURE: { en: 'CAS failure', ru: 'CAS неуспешен' },
  ABA_RISK: { en: 'ABA risk', ru: 'риск ABA' },
};

const RESULT_LABELS: Record<string, Localized> = {
  SUCCESS: { en: 'success', ru: 'успех' },
  FAILURE: { en: 'failure', ru: 'неудача' },
};

interface ThreadSnapshot {
  name: string;
  status: string;
  attempts: number;
  lastRead?: number;
  lastReadRevision?: number;
  lastExpected?: number;
  lastUpdate?: number;
  lastResult?: string;
}

interface HistoryItem {
  thread: string;
  action: string;
  before: number;
  expected?: number;
  update?: number;
  after: number;
  result?: string;
  revision: number;
}

interface CompareAndSetState {
  name: string;
  value: number;
  revision: number;
  threads: ThreadSnapshot[];
  history: HistoryItem[];
}

export default function CompareAndSetVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as CompareAndSetState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const lastHistory = state.history[state.history.length - 1];
  const lastResult = lastHistory?.result
    ? tl(RESULT_LABELS[lastHistory.result] ?? lastHistory.result, lang)
    : tl(LABELS.none, lang);

  const cells: ArrayCell[] = [
    {
      key: 'slot',
      label: tl(LABELS.slot, lang),
      highlighted: highlight.has('slot'),
      content: <strong style={valueStyle}>{state.name} = {state.value}</strong>,
    },
    {
      key: 'revision',
      label: tl(LABELS.revision, lang),
      highlighted: highlight.has('aba'),
      content: <span style={monoStyle}>{state.revision}</span>,
    },
    {
      key: 'result',
      label: tl(LABELS.lastResult, lang),
      highlighted: highlight.has('success') || highlight.has('failure'),
      content: <span style={monoStyle}>{lastResult}</span>,
    },
  ];

  const threadNodes: LinkedNode[] = state.threads.map((thread) => ({
    id: thread.name,
    title: thread.name,
    subtitle: threadSubtitle(thread, lang),
    highlighted: highlight.has(`thread:${thread.name}`),
  }));

  return (
    <div style={wrapStyle}>
      <ArrayGrid cells={cells} />
      <section style={sectionStyle}>
        <div style={titleStyle}>{tl(LABELS.threads, lang)}</div>
        <LinkedNodes nodes={threadNodes} />
      </section>
      <section style={sectionStyle}>
        <div style={titleStyle}>{tl(LABELS.history, lang)}</div>
        <div style={historyStyle}>
          {state.history.map((item, index) => {
            const action = ACTION_LABELS[item.action] ?? { en: item.action, ru: item.action };
            const expected = item.expected === undefined
              ? ''
              : `, ${tl(LABELS.expected, lang)} ${item.expected}`;
            const update = item.update === undefined
              ? ''
              : `, ${tl(LABELS.update, lang)} ${item.update}`;
            return (
              <div key={`${item.thread}-${item.action}-${index}`} style={historyItemStyle}>
                <span style={threadStyle}>{item.thread}</span>
                <span>
                  {tl(action, lang)}:
                  {' '}{tl(LABELS.before, lang)} {item.before}
                  {expected}
                  {update}
                  {', '}{tl(LABELS.after, lang)} {item.after}
                  {', '}{tl(LABELS.rev, lang)} {item.revision}
                </span>
              </div>
            );
          })}
        </div>
      </section>
    </div>
  );
}

function threadSubtitle(thread: ThreadSnapshot, lang: 'en' | 'ru') {
  const parts: string[] = [];
  const status = STATUS_LABELS[thread.status] ?? { en: thread.status, ru: thread.status };
  parts.push(tl(status, lang));
  parts.push(`${tl(LABELS.attempts, lang)} ${thread.attempts}`);
  if (thread.lastRead !== undefined) {
    parts.push(`${tl(LABELS.read, lang)} ${thread.lastRead}@${tl(LABELS.rev, lang)} ${thread.lastReadRevision}`);
  }
  if (thread.lastExpected !== undefined) {
    parts.push(`${tl(LABELS.expected, lang)} ${thread.lastExpected}`);
  }
  if (thread.lastUpdate !== undefined) {
    parts.push(`${tl(LABELS.update, lang)} ${thread.lastUpdate}`);
  }
  return parts.join(' | ');
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 12 };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
const sectionStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 6 };
const titleStyle: CSSProperties = { fontSize: 12, fontWeight: 700, opacity: 0.7 };
const valueStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 18 };
const monoStyle: CSSProperties = { fontFamily: 'monospace', fontWeight: 700 };
const historyStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 4 };
const historyItemStyle: CSSProperties = {
  display: 'flex',
  gap: 8,
  alignItems: 'center',
  padding: '4px 8px',
  borderRadius: 6,
  background: 'var(--viz-box)',
  fontSize: 13,
};
const threadStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontWeight: 700,
  minWidth: 48,
};
