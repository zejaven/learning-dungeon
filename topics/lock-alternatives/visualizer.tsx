import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { LinkedNodes, type LinkedNode } from '@app/primitives/LinkedNodes';
import { tl, useLang, type Localized } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to compare lock alternatives.',
    ru: 'Запустите код, чтобы сравнить альтернативы synchronized.',
  },
  lock: { en: 'lock', ru: 'lock' },
  policy: { en: 'policy', ru: 'правило' },
  owner: { en: 'exclusive owner', ru: 'эксклюзивный владелец' },
  holdCount: { en: 'hold count', ru: 'hold count' },
  holds: { en: 'holds', ru: 'удержаний' },
  readers: { en: 'readers', ru: 'readers' },
  writer: { en: 'writer', ru: 'writer' },
  waitingQueue: { en: 'waiting queue', ru: 'очередь ожидания' },
  optimistic: { en: 'optimistic stamps', ru: 'optimistic stamps' },
  stamp: { en: 'stamp', ru: 'stamp' },
  version: { en: 'version', ru: 'version' },
  recentActions: { en: 'recent actions', ru: 'последние действия' },
  free: { en: 'free', ru: 'свободен' },
  valid: { en: 'valid', ru: 'валиден' },
  invalid: { en: 'invalid', ru: 'невалиден' },
};

const KIND_LABELS: Record<string, Localized> = {
  REENTRANT_LOCK: { en: 'ReentrantLock', ru: 'ReentrantLock' },
  READ_WRITE_LOCK: { en: 'ReentrantReadWriteLock', ru: 'ReentrantReadWriteLock' },
  STAMPED_LOCK: { en: 'StampedLock', ru: 'StampedLock' },
};

const POLICY_LABELS: Record<string, Localized> = {
  REENTRANT_LOCK: {
    en: 'one explicit owner, optional queue control',
    ru: 'один явный владелец, дополнительный контроль очереди',
  },
  READ_WRITE_LOCK: {
    en: 'many readers or one writer',
    ru: 'много readers или один writer',
  },
  STAMPED_LOCK: {
    en: 'optimistic read stamps plus read/write modes',
    ru: 'optimistic read stamps плюс режимы read/write',
  },
};

const MODE_LABELS: Record<string, Localized> = {
  READ: { en: 'read', ru: 'read' },
  WRITE: { en: 'write', ru: 'write' },
};

const ACTION_LABELS: Record<string, Localized> = {
  CREATE: { en: 'created', ru: 'создан' },
  REENTRANT_ACQUIRE: { en: 'acquired', ru: 'получил' },
  REENTRANT_REENTER: { en: 'reentered', ru: 'вошёл повторно' },
  TRY_LOCK_FAIL: { en: 'tryLock failed', ru: 'tryLock отказал' },
  REENTRANT_WAIT: { en: 'waits', ru: 'ждёт' },
  REENTRANT_RELEASE: { en: 'released', ru: 'освободил' },
  REENTRANT_GRANT: { en: 'granted', ru: 'передан' },
  READ_ACQUIRE: { en: 'read acquired', ru: 'read получен' },
  READ_WAIT: { en: 'read waits', ru: 'read ждёт' },
  WRITE_ACQUIRE: { en: 'write acquired', ru: 'write получен' },
  WRITE_WAIT: { en: 'write waits', ru: 'write ждёт' },
  UPGRADE_RISK: { en: 'upgrade risk', ru: 'риск upgrade' },
  READ_RELEASE: { en: 'read released', ru: 'read освобождён' },
  WRITE_RELEASE: { en: 'write released', ru: 'write освобождён' },
  WRITE_GRANT: { en: 'write granted', ru: 'write передан' },
  READ_GRANT: { en: 'read granted', ru: 'read передан' },
  OPTIMISTIC_READ: { en: 'optimistic read', ru: 'optimistic read' },
  VALIDATE_OK: { en: 'validate ok', ru: 'validate успешен' },
  VALIDATE_FAIL: { en: 'validate failed', ru: 'validate неуспешен' },
  STAMPED_READ: { en: 'read stamp', ru: 'read stamp' },
  STAMPED_READ_WAIT: { en: 'read waits', ru: 'read ждёт' },
  STAMPED_WRITE: { en: 'write stamp', ru: 'write stamp' },
  STAMPED_WRITE_WAIT: { en: 'write waits', ru: 'write ждёт' },
  STAMPED_READ_RELEASE: { en: 'read released', ru: 'read освобождён' },
  STAMPED_WRITE_RELEASE: { en: 'write released', ru: 'write освобождён' },
  STAMPED_WRITE_GRANT: { en: 'write granted', ru: 'write передан' },
  STAMPED_READ_GRANT: { en: 'read granted', ru: 'read передан' },
  CONVERT_TO_WRITE: { en: 'converted to write', ru: 'convert в write выполнен' },
  CONVERT_FAIL: { en: 'convert failed', ru: 'convert неуспешен' },
};

interface Reader {
  thread: string;
  holds: number;
}

interface QueueItem {
  thread: string;
  mode: string;
}

interface OptimisticRead {
  thread: string;
  stamp: number;
  valid: boolean;
}

interface HistoryItem {
  actor: string;
  action: string;
  detail: string;
}

interface LockState {
  name: string;
  kind: string;
  policy: string;
  owner: string | null;
  holdCount: number;
  readers: Reader[];
  writer: string | null;
  waitingQueue: QueueItem[];
  version: number;
  optimisticReads: OptimisticRead[];
  history: HistoryItem[];
}

export default function LockAlternativesVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as LockState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const kindLabel = KIND_LABELS[state.kind] ?? { en: state.kind, ru: state.kind };
  const policyLabel = POLICY_LABELS[state.kind] ?? { en: state.policy, ru: state.policy };

  const cells: ArrayCell[] = [
    {
      key: 'lock',
      label: tl(LABELS.lock, lang),
      content: (
        <div style={lineStyle}>
          <strong style={monoStyle}>{state.name}</strong>
          <span>{tl(kindLabel, lang)}</span>
        </div>
      ),
    },
    {
      key: 'policy',
      label: tl(LABELS.policy, lang),
      content: <span>{tl(policyLabel, lang)}</span>,
    },
    {
      key: 'owner',
      label: tl(LABELS.owner, lang),
      highlighted: Boolean(state.owner && highlight.has(`owner:${state.owner}`)),
      content: <Owner state={state} highlight={highlight} />,
    },
    {
      key: 'readers',
      label: tl(LABELS.readers, lang),
      content: <BoxGroup boxes={readerBoxes(state.readers, highlight, lang)} />,
    },
    {
      key: 'writer',
      label: tl(LABELS.writer, lang),
      highlighted: Boolean(state.writer && highlight.has(`writer:${state.writer}`)),
      content: <BoxGroup boxes={writerBoxes(state.writer, highlight, lang)} />,
    },
    {
      key: 'waiting',
      label: tl(LABELS.waitingQueue, lang),
      content: <LinkedNodes nodes={queueNodes(state.waitingQueue, highlight, lang)} />,
    },
  ];

  if (state.kind === 'STAMPED_LOCK') {
    cells.push(
      {
        key: 'version',
        label: tl(LABELS.version, lang),
        highlighted: highlight.has('version'),
        content: <strong style={countStyle}>{state.version}</strong>,
      },
      {
        key: 'optimistic',
        label: tl(LABELS.optimistic, lang),
        content: <BoxGroup boxes={optimisticBoxes(state.optimisticReads, highlight, lang)} />,
      },
    );
  }

  return (
    <div style={wrapStyle}>
      <ArrayGrid cells={cells} />
      <section style={sectionStyle}>
        <div style={titleStyle}>{tl(LABELS.recentActions, lang)}</div>
        <div style={historyStyle}>
          {state.history.map((item, index) => {
            const label = ACTION_LABELS[item.action] ?? { en: item.action, ru: item.action };
            return (
              <div key={`${item.actor}-${item.action}-${index}`} style={historyItemStyle}>
                <span style={actorStyle}>{item.actor}</span>
                <span>{tl(label, lang)}</span>
              </div>
            );
          })}
        </div>
      </section>
    </div>
  );
}

function Owner({ state, highlight }: { state: LockState; highlight: Set<string> }) {
  const lang = useLang((s) => s.lang);
  if (!state.owner) {
    return <span style={mutedStyle}>{tl(LABELS.free, lang)}</span>;
  }
  return (
    <div style={lineStyle}>
      <BoxGroup
        boxes={[
          {
            id: state.owner,
            title: state.owner,
            subtitle: `${tl(LABELS.holdCount, lang)} ${state.holdCount}`,
            highlighted: highlight.has(`owner:${state.owner}`),
          },
        ]}
      />
      {highlight.has('holdCount') && <strong style={countStyle}>{state.holdCount}</strong>}
    </div>
  );
}

function readerBoxes(readers: Reader[], highlight: Set<string>, lang: 'en' | 'ru'): Box[] {
  return readers.map((reader) => ({
    id: reader.thread,
    title: reader.thread,
    subtitle: `${tl(LABELS.holds, lang)} ${reader.holds}`,
    highlighted: highlight.has(`reader:${reader.thread}`),
  }));
}

function writerBoxes(writer: string | null, highlight: Set<string>, lang: 'en' | 'ru'): Box[] {
  if (!writer) {
    return [];
  }
  return [
    {
      id: writer,
      title: writer,
      subtitle: tl(LABELS.writer, lang),
      highlighted: highlight.has(`writer:${writer}`),
    },
  ];
}

function queueNodes(queue: QueueItem[], highlight: Set<string>, lang: 'en' | 'ru'): LinkedNode[] {
  return queue.map((item) => {
    const mode = MODE_LABELS[item.mode] ?? { en: item.mode, ru: item.mode };
    return {
      id: `${item.thread}-${item.mode}`,
      title: item.thread,
      subtitle: tl(mode, lang),
      highlighted: highlight.has(`queue:${item.thread}`),
    };
  });
}

function optimisticBoxes(
  reads: OptimisticRead[],
  highlight: Set<string>,
  lang: 'en' | 'ru',
): Box[] {
  return reads.map((read) => ({
    id: `${read.thread}-${read.stamp}`,
    title: read.thread,
    subtitle: `${tl(LABELS.stamp, lang)} ${read.stamp} - ${tl(read.valid ? LABELS.valid : LABELS.invalid, lang)}`,
    highlighted: highlight.has(`optimistic:${read.thread}`),
    dim: !read.valid,
  }));
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 12 };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
const lineStyle: CSSProperties = { display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' };
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
  minWidth: 96,
};
const monoStyle: CSSProperties = { fontFamily: 'monospace', fontWeight: 700 };
const mutedStyle: CSSProperties = { opacity: 0.55, fontSize: 13 };
const countStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 18, color: 'var(--accent)' };
