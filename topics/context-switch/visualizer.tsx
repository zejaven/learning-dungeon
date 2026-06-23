import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { LinkedNodes, type LinkedNode } from '@app/primitives/LinkedNodes';
import { tl, useLang, type Localized } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to visualize context switching.',
    ru: 'Запустите код, чтобы визуализировать context switching.',
  },
  cpu: { en: 'CPU core', ru: 'CPU core' },
  readyQueue: { en: 'ready queue', ru: 'ready queue' },
  waiting: { en: 'waiting', ru: 'ожидание' },
  terminated: { en: 'terminated', ru: 'завершенные' },
  savedContexts: { en: 'saved contexts', ru: 'сохраненные contexts' },
  metrics: { en: 'metrics', ru: 'метрики' },
  switches: { en: 'context switches', ru: 'context switches' },
  overhead: { en: 'overhead ticks', ru: 'накладные ticks' },
  useful: { en: 'useful instructions', ru: 'полезные instructions' },
  recentActions: { en: 'recent scheduler actions', ru: 'последние действия scheduler' },
  saved: { en: 'saved', ru: 'сохранен' },
  live: { en: 'loaded on CPU', ru: 'загружен в CPU' },
  idle: { en: 'idle', ru: 'простаивает' },
  reason: { en: 'reason', ru: 'причина' },
  runs: { en: 'runs', ru: 'запусков' },
  slice: { en: 'slice', ru: 'квант' },
};

const STATE_LABELS: Record<string, Localized> = {
  READY: { en: 'READY', ru: 'READY' },
  RUNNING: { en: 'RUNNING', ru: 'RUNNING' },
  WAITING: { en: 'WAITING', ru: 'WAITING' },
  TERMINATED: { en: 'TERMINATED', ru: 'TERMINATED' },
};

const ACTION_LABELS: Record<string, Localized> = {
  CREATE_MODEL: { en: 'created scene', ru: 'создал сцену' },
  THREAD_READY: { en: 'queued thread', ru: 'поставил thread в queue' },
  RESTORE_CONTEXT: { en: 'restored context', ru: 'восстановил context' },
  DISPATCH_THREAD: { en: 'dispatched thread', ru: 'запустил thread' },
  RUN_INSTRUCTIONS: { en: 'ran instructions', ru: 'выполнил instructions' },
  SAVE_CONTEXT: { en: 'saved context', ru: 'сохранил context' },
  CONTEXT_SWITCH: { en: 'switched context', ru: 'переключил context' },
  THREAD_BLOCKED: { en: 'blocked thread', ru: 'заблокировал thread' },
  THREAD_WOKE: { en: 'woke thread', ru: 'разбудил thread' },
  THREAD_FINISHED: { en: 'finished thread', ru: 'завершил thread' },
  CPU_IDLE: { en: 'CPU became idle', ru: 'CPU простаивает' },
};

interface CpuSnapshot {
  core: string;
  mode: string;
  runningThread?: string;
  pc?: number;
  registerA?: number;
  timeSliceUsed?: number;
}

interface ThreadSnapshot {
  name: string;
  state: string;
  pc: number;
  sp: number;
  registerA: number;
  saved: boolean;
  runs: number;
  timeSliceUsed: number;
  waitReason?: string;
  lastSaveReason?: string;
}

interface MetricsSnapshot {
  contextSwitches: number;
  overheadTicks: number;
  usefulInstructions: number;
}

interface HistoryItem {
  actor: string;
  action: string;
  detail: string;
}

interface ContextSwitchState {
  name: string;
  cpu: CpuSnapshot;
  readyQueue: ThreadSnapshot[];
  waiting: ThreadSnapshot[];
  terminated: ThreadSnapshot[];
  contexts: ThreadSnapshot[];
  metrics: MetricsSnapshot;
  history: HistoryItem[];
}

export default function ContextSwitchVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as ContextSwitchState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const cells: ArrayCell[] = [
    {
      key: 'cpu',
      label: tl(LABELS.cpu, lang),
      highlighted: highlight.has('cpu:cpu-0'),
      content: <LinkedNodes nodes={cpuNodes(state.cpu, highlight, lang)} />,
    },
    {
      key: 'ready',
      label: tl(LABELS.readyQueue, lang),
      highlighted: highlight.has('queue:ready'),
      content: <LinkedNodes nodes={threadNodes(state.readyQueue, highlight, lang)} />,
    },
    {
      key: 'waiting',
      label: tl(LABELS.waiting, lang),
      content: <LinkedNodes nodes={threadNodes(state.waiting, highlight, lang)} />,
    },
    {
      key: 'terminated',
      label: tl(LABELS.terminated, lang),
      content: <LinkedNodes nodes={threadNodes(state.terminated, highlight, lang)} />,
    },
    {
      key: 'contexts',
      label: tl(LABELS.savedContexts, lang),
      content: <LinkedNodes nodes={contextNodes(state.contexts, highlight, lang)} />,
    },
  ];

  return (
    <div style={wrapStyle}>
      <div style={statsStyle}>
        <Stat label={tl(LABELS.switches, lang)} value={state.metrics.contextSwitches} />
        <Stat label={tl(LABELS.overhead, lang)} value={state.metrics.overheadTicks} highlight={event?.event === 'CONTEXT_SWITCHED'} />
        <Stat label={tl(LABELS.useful, lang)} value={state.metrics.usefulInstructions} />
      </div>

      <ArrayGrid cells={cells} />

      <section style={sectionStyle}>
        <div style={titleStyle}>{tl(LABELS.recentActions, lang)}</div>
        <div style={historyStyle}>
          {state.history.map((item, index) => {
            const action = ACTION_LABELS[item.action] ?? { en: item.action, ru: item.action };
            return (
              <div key={`${item.actor}-${item.action}-${index}`} style={historyItemStyle}>
                <span style={actorStyle}>{item.actor}</span>
                <span>{tl(action, lang)}</span>
                <span style={monoStyle}>{item.detail}</span>
              </div>
            );
          })}
        </div>
      </section>
    </div>
  );
}

function cpuNodes(cpu: CpuSnapshot, highlight: Set<string>, lang: 'en' | 'ru'): LinkedNode[] {
  if (!cpu.runningThread) {
    return [
      {
        id: cpu.core,
        title: cpu.core,
        subtitle: `${cpu.mode} - ${tl(LABELS.idle, lang)}`,
        highlighted: highlight.has(`cpu:${cpu.core}`),
      },
    ];
  }

  return [
    {
      id: cpu.runningThread,
      title: `${cpu.core}: ${cpu.runningThread}`,
      subtitle: `${cpu.mode} - PC ${cpu.pc} - R1 ${cpu.registerA} - ${tl(LABELS.slice, lang)} ${cpu.timeSliceUsed}`,
      highlighted: highlight.has(`cpu:${cpu.core}`) || highlight.has(`thread:${cpu.runningThread}`),
    },
  ];
}

function threadNodes(threads: ThreadSnapshot[], highlight: Set<string>, lang: 'en' | 'ru'): LinkedNode[] {
  return threads.map((thread) => ({
    id: thread.name,
    title: thread.name,
    subtitle: threadSubtitle(thread, lang),
    highlighted: highlight.has(`thread:${thread.name}`),
  }));
}

function contextNodes(threads: ThreadSnapshot[], highlight: Set<string>, lang: 'en' | 'ru'): LinkedNode[] {
  return threads.map((thread) => {
    const saved = thread.saved ? tl(LABELS.saved, lang) : tl(LABELS.live, lang);
    return {
      id: `context-${thread.name}`,
      title: thread.name,
      subtitle: `${saved} - PC ${thread.pc} - SP ${thread.sp} - R1 ${thread.registerA}`,
      highlighted: highlight.has(`context:${thread.name}`) || highlight.has(`thread:${thread.name}`),
    };
  });
}

function threadSubtitle(thread: ThreadSnapshot, lang: 'en' | 'ru') {
  const state = STATE_LABELS[thread.state] ?? { en: thread.state, ru: thread.state };
  const reason = thread.waitReason ? ` - ${tl(LABELS.reason, lang)} ${thread.waitReason}` : '';
  return `${tl(state, lang)} - PC ${thread.pc} - ${tl(LABELS.runs, lang)} ${thread.runs}${reason}`;
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
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
const statsStyle: CSSProperties = { display: 'flex', flexWrap: 'wrap', gap: 16 };
const statStyle: CSSProperties = { textAlign: 'center', minWidth: 118 };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.65 };
const statValueStyle: CSSProperties = { fontSize: 18, fontWeight: 700, fontFamily: 'monospace' };
const sectionStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 6 };
const titleStyle: CSSProperties = { fontSize: 12, fontWeight: 700, opacity: 0.7 };
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
const actorStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontWeight: 700,
  minWidth: 78,
};
const monoStyle: CSSProperties = { fontFamily: 'monospace', fontWeight: 700 };
