import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { LinkedNodes, type LinkedNode } from '@app/primitives/LinkedNodes';
import { tl, useLang, type Localized } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to visualize semaphore permits.',
    ru: 'Запустите код, чтобы визуализировать permits семафора.',
  },
  semaphore: { en: 'semaphore', ru: 'semaphore' },
  fair: { en: 'fair', ru: 'fair' },
  yes: { en: 'yes', ru: 'да' },
  no: { en: 'no', ru: 'нет' },
  available: { en: 'available', ru: 'доступно' },
  initial: { en: 'initial', ru: 'начально' },
  holders: { en: 'holders', ru: 'holders' },
  waitingQueue: { en: 'waiting queue', ru: 'очередь ожидания' },
  trackedPermits: { en: 'tracked permits', ru: 'отслеживаемые permits' },
  permits: { en: 'permits', ru: 'permits' },
  permit: { en: 'permit', ru: 'permit' },
  recentActions: { en: 'recent actions', ru: 'последние действия' },
  overRelease: { en: 'over-release', ru: 'лишний release' },
};

const ACTION_LABELS: Record<string, Localized> = {
  CREATE: { en: 'created', ru: 'создан' },
  ACQUIRE: { en: 'acquired', ru: 'получил' },
  WAIT: { en: 'waits for', ru: 'ждёт' },
  TRY_OK: { en: 'tryAcquire ok', ru: 'tryAcquire успешно' },
  TRY_FAIL: { en: 'tryAcquire failed', ru: 'tryAcquire неудачно' },
  RELEASE: { en: 'released', ru: 'освободил' },
  NO_OWNER_RELEASE: { en: 'released without ownership', ru: 'release без ownership' },
  GRANT: { en: 'granted', ru: 'передан' },
  OVER_RELEASE: { en: 'over-released', ru: 'лишний release' },
};

interface PermitHolder {
  thread: string;
  permits: number;
}

interface HistoryItem {
  actor: string;
  action: string;
  permits: number;
}

interface SemaphoreState {
  name: string;
  initialPermits: number;
  availablePermits: number;
  trackedPermits: number;
  fair: boolean;
  holders: PermitHolder[];
  waitingQueue: PermitHolder[];
  history: HistoryItem[];
}

export default function SemaphoreVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as SemaphoreState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const overRelease = state.trackedPermits > state.initialPermits;
  const cells: ArrayCell[] = [
    {
      key: 'semaphore',
      label: tl(LABELS.semaphore, lang),
      content: (
        <span>
          <strong style={monoStyle}>{state.name}</strong>
          <span style={mutedStyle}>
            {' '}
            - {tl(LABELS.fair, lang)}: {state.fair ? tl(LABELS.yes, lang) : tl(LABELS.no, lang)}
          </span>
        </span>
      ),
    },
    {
      key: 'available',
      label: tl(LABELS.available, lang),
      highlighted: highlight.has('available') || highlight.has('over'),
      content: (
        <div style={lineStyle}>
          <strong style={countStyle}>{state.availablePermits}</strong>
          <span style={mutedStyle}>
            {tl(LABELS.initial, lang)} {state.initialPermits}
          </span>
          <LinkedNodes nodes={permitNodes(state.availablePermits, highlight, lang)} />
        </div>
      ),
    },
    {
      key: 'holders',
      label: tl(LABELS.holders, lang),
      content: <LinkedNodes nodes={holderNodes(state.holders, highlight, lang)} />,
    },
    {
      key: 'waiting',
      label: tl(LABELS.waitingQueue, lang),
      content: <LinkedNodes nodes={waiterNodes(state.waitingQueue, highlight, lang)} />,
    },
    {
      key: 'tracked',
      label: tl(LABELS.trackedPermits, lang),
      highlighted: overRelease || highlight.has('over'),
      content: (
        <strong style={{ ...countStyle, color: overRelease ? 'var(--accent)' : 'var(--text)' }}>
          {state.trackedPermits}
          {overRelease ? ` - ${tl(LABELS.overRelease, lang)}` : ''}
        </strong>
      ),
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
                <span style={monoStyle}>
                  {item.permits} {tl(LABELS.permits, lang)}
                </span>
              </div>
            );
          })}
        </div>
      </section>
    </div>
  );
}

function permitNodes(count: number, highlight: Set<string>, lang: 'en' | 'ru'): LinkedNode[] {
  return Array.from({ length: count }, (_, index) => ({
    id: `permit-${index}`,
    title: `P${index + 1}`,
    subtitle: tl(LABELS.permit, lang),
    highlighted: highlight.has('available'),
  }));
}

function holderNodes(holders: PermitHolder[], highlight: Set<string>, lang: 'en' | 'ru'): LinkedNode[] {
  return holders.map((holder) => ({
    id: holder.thread,
    title: holder.thread,
    subtitle: `${holder.permits} ${tl(LABELS.permits, lang)}`,
    highlighted: highlight.has(`holder:${holder.thread}`),
  }));
}

function waiterNodes(waiters: PermitHolder[], highlight: Set<string>, lang: 'en' | 'ru'): LinkedNode[] {
  return waiters.map((waiter) => ({
    id: waiter.thread,
    title: waiter.thread,
    subtitle: `${waiter.permits} ${tl(LABELS.permits, lang)}`,
    highlighted: highlight.has(`queue:${waiter.thread}`),
  }));
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
  minWidth: 92,
};
const monoStyle: CSSProperties = { fontFamily: 'monospace', fontWeight: 700 };
const mutedStyle: CSSProperties = { opacity: 0.7, fontSize: 12 };
const countStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 18 };
const lineStyle: CSSProperties = {
  display: 'flex',
  flexWrap: 'wrap',
  gap: 8,
  alignItems: 'center',
};
