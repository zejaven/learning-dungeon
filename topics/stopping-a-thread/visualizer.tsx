import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { LinkedNodes, type LinkedNode } from '@app/primitives/LinkedNodes';
import { tl, useLang, type Localized } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to visualize cooperative thread stopping.',
    ru: 'Запустите код, чтобы визуализировать кооперативную остановку thread.',
  },
  controller: { en: 'owner thread', ru: 'thread-владелец' },
  workers: { en: 'workers', ru: 'workers' },
  signals: { en: 'cancellation signals', ru: 'сигналы отмены' },
  recentActions: { en: 'recent actions', ru: 'последние действия' },
  workUnits: { en: 'work units', ru: 'единицы работы' },
  joined: { en: 'joined', ru: 'join выполнен' },
  notJoined: { en: 'not joined', ru: 'join не выполнен' },
  waitingFor: { en: 'waiting in', ru: 'ожидает в' },
  observed: { en: 'observed', ru: 'заметил' },
  unsafeAttempts: { en: 'unsafe stop attempts', ru: 'попытки unsafe stop' },
  on: { en: 'on', ru: 'вкл' },
  off: { en: 'off', ru: 'выкл' },
  stopFlag: { en: 'stop flag', ru: 'флаг остановки' },
  interruptSignal: { en: 'interrupt sent', ru: 'interrupt отправлен' },
  interruptStatus: { en: 'interrupt status', ru: 'interrupt status' },
  interruptedException: { en: 'InterruptedException', ru: 'InterruptedException' },
  restored: { en: 'restored status', ru: 'status восстановлен' },
};

const STATE_LABELS: Record<string, Localized> = {
  NEW: { en: 'NEW', ru: 'NEW' },
  RUNNING: { en: 'RUNNING', ru: 'RUNNING' },
  STOP_REQUESTED: { en: 'STOP_REQUESTED', ru: 'STOP_REQUESTED' },
  WAITING: { en: 'WAITING', ru: 'WAITING' },
  STOPPING: { en: 'STOPPING', ru: 'STOPPING' },
  TERMINATED: { en: 'TERMINATED', ru: 'TERMINATED' },
};

const ACTION_LABELS: Record<string, Localized> = {
  CREATE_MODEL: { en: 'created scene', ru: 'создал сцену' },
  CREATE_WORKER: { en: 'created worker', ru: 'создал worker' },
  START_WORKER: { en: 'called start()', ru: 'вызвал start()' },
  DO_WORK: { en: 'did work', ru: 'выполнил работу' },
  REQUEST_STOP: { en: 'requested stop', ru: 'запросил остановку' },
  OBSERVE_STOP: { en: 'checked stop flag', ru: 'проверил флаг остановки' },
  BLOCK: { en: 'blocked', ru: 'заблокировался' },
  INTERRUPT: { en: 'called interrupt()', ru: 'вызвал interrupt()' },
  OBSERVE_INTERRUPT_STATUS: { en: 'checked interrupt status', ru: 'проверил interrupt status' },
  HANDLE_INTERRUPTED_EXCEPTION: { en: 'handled InterruptedException', ru: 'обработал InterruptedException' },
  RESTORE_INTERRUPT_STATUS: { en: 'restored interrupt status', ru: 'восстановил interrupt status' },
  REJECT_THREAD_STOP: { en: 'rejected Thread.stop()', ru: 'отклонил Thread.stop()' },
  EXIT: { en: 'returned from run()', ru: 'вернулся из run()' },
  JOIN: { en: 'completed join()', ru: 'завершил join()' },
};

interface WorkerSnapshot {
  name: string;
  state: string;
  started: boolean;
  workUnits: number;
  joined: boolean;
  unsafeStopAttempts: number;
  waitReason?: string;
  lastObservation?: string;
}

interface SignalSnapshot {
  worker: string;
  stopRequested: boolean;
  interruptSignalSent: boolean;
  interruptStatus: boolean;
  interruptedExceptionPending: boolean;
  restoredInterrupt: boolean;
}

interface HistoryItem {
  actor: string;
  action: string;
  target: string;
}

interface ThreadStopState {
  name: string;
  controllerThread: string;
  workers: WorkerSnapshot[];
  signals: SignalSnapshot[];
  history: HistoryItem[];
}

export default function StoppingAThreadVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as ThreadStopState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const cells: ArrayCell[] = [
    {
      key: 'controller',
      label: tl(LABELS.controller, lang),
      content: <strong style={monoStyle}>{state.controllerThread}</strong>,
    },
    {
      key: 'workers',
      label: tl(LABELS.workers, lang),
      content: <LinkedNodes nodes={workerNodes(state.workers, highlight, lang)} />,
    },
    {
      key: 'signals',
      label: tl(LABELS.signals, lang),
      content: <LinkedNodes nodes={signalNodes(state.signals, highlight, lang)} />,
    },
  ];

  return (
    <div style={wrapStyle}>
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
                <span style={monoStyle}>{item.target}</span>
              </div>
            );
          })}
        </div>
      </section>
    </div>
  );
}

function workerNodes(workers: WorkerSnapshot[], highlight: Set<string>, lang: 'en' | 'ru'): LinkedNode[] {
  return workers.map((worker) => {
    const state = STATE_LABELS[worker.state] ?? { en: worker.state, ru: worker.state };
    const joined = worker.joined ? tl(LABELS.joined, lang) : tl(LABELS.notJoined, lang);
    const wait = worker.waitReason ? ` - ${tl(LABELS.waitingFor, lang)} ${worker.waitReason}` : '';
    const observed = worker.lastObservation ? ` - ${tl(LABELS.observed, lang)} ${worker.lastObservation}` : '';
    const unsafe = worker.unsafeStopAttempts > 0
      ? ` - ${tl(LABELS.unsafeAttempts, lang)} ${worker.unsafeStopAttempts}`
      : '';
    return {
      id: worker.name,
      title: worker.name,
      subtitle: `${tl(state, lang)} - ${tl(LABELS.workUnits, lang)} ${worker.workUnits} - ${joined}${wait}${observed}${unsafe}`,
      highlighted: highlight.has(`worker:${worker.name}`) || highlight.has(`join:${worker.name}`),
    };
  });
}

function signalNodes(signals: SignalSnapshot[], highlight: Set<string>, lang: 'en' | 'ru'): LinkedNode[] {
  return signals.flatMap((signal) => [
    signalNode(signal.worker, 'stop', LABELS.stopFlag, signal.stopRequested, highlight, lang),
    signalNode(signal.worker, 'interrupt', LABELS.interruptSignal, signal.interruptSignalSent, highlight, lang),
    signalNode(signal.worker, 'status', LABELS.interruptStatus, signal.interruptStatus, highlight, lang),
    signalNode(signal.worker, 'exception', LABELS.interruptedException, signal.interruptedExceptionPending, highlight, lang),
    signalNode(signal.worker, 'restored', LABELS.restored, signal.restoredInterrupt, highlight, lang),
  ]);
}

function signalNode(
  worker: string,
  key: string,
  label: Localized,
  active: boolean,
  highlight: Set<string>,
  lang: 'en' | 'ru',
): LinkedNode {
  return {
    id: `${worker}-${key}`,
    title: tl(label, lang),
    subtitle: `${worker} - ${active ? tl(LABELS.on, lang) : tl(LABELS.off, lang)}`,
    highlighted: highlight.has(`signal:${worker}:${key}`),
  };
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 12 };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
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
  minWidth: 72,
};
const monoStyle: CSSProperties = { fontFamily: 'monospace', fontWeight: 700 };
