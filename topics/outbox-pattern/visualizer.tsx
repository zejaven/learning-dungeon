import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  action: { en: 'action', ru: 'действие' },
  ordersTable: { en: 'business table (orders)', ru: 'таблица бизнес-данных (заказы)' },
  outboxTable: { en: 'outbox table (staged events)', ru: 'таблица outbox (готовые события)' },
  brokerTable: { en: 'broker (delivered events)', ru: 'брокер (доставленные события)' },
  committed: { en: 'committed', ru: 'зафиксировано' },
  published: { en: 'published', ru: 'опубликовано' },
  redelivered: { en: 'redelivered', ru: 'повторно' },
  lost: { en: 'lost', ru: 'потеряно' },
  pending: { en: 'PENDING', ru: 'PENDING' },
  publishedTag: { en: 'PUBLISHED', ru: 'PUBLISHED' },
  duplicate: { en: 'duplicate', ru: 'дубликат' },
  outcomeCommitted: { en: 'committed ✓', ru: 'зафиксировано ✓' },
  outcomeRolledback: { en: 'rolled back ✗', ru: 'откат ✗' },
  outcomePublished: { en: 'published ✓', ru: 'опубликовано ✓' },
  outcomeRedelivered: { en: 'redelivered (duplicate) ✗', ru: 'повторная доставка (дубликат) ✗' },
  outcomeLost: { en: 'event lost ✗', ru: 'событие потеряно ✗' },
  noAction: { en: 'no action in flight', ru: 'нет действия в полёте' },
  runHint: {
    en: 'Run the code to visualize the outbox.',
    ru: 'Запустите код, чтобы визуализировать outbox.',
  },
};

interface Order {
  id: string;
  amount: number;
}
interface OutboxRow {
  eventId: string;
  type: string;
  payload: string;
  status: 'PENDING' | 'PUBLISHED';
}
interface Delivered {
  eventId: string;
  type: string;
  payload: string;
  duplicate: boolean;
}
interface CurrentAction {
  id: string;
  info: string;
  outcome: 'committed' | 'rolledback' | 'published' | 'redelivered' | 'lost';
}
interface OutboxState {
  name: string;
  orders: Order[];
  outbox: OutboxRow[];
  broker: Delivered[];
  committed: number;
  published: number;
  redelivered: number;
  lost: number;
  action?: CurrentAction;
}

export default function OutboxVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as OutboxState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);

  const orderBoxes: Box[] = state.orders.map((o) => ({
    id: o.id,
    title: o.id,
    subtitle: String(o.amount),
    highlighted: highlight.has(`order:${o.id}`),
  }));

  const outboxBoxes: Box[] = state.outbox.map((r) => ({
    id: r.eventId,
    title: `${r.eventId} · ${r.status === 'PENDING' ? tl(LABELS.pending, lang) : tl(LABELS.publishedTag, lang)}`,
    subtitle: r.payload,
    highlighted: highlight.has(`outbox:${r.eventId}`),
    dim: r.status === 'PUBLISHED',
  }));

  const brokerBoxes: Box[] = state.broker.map((d, i) => ({
    id: `${d.eventId}#${i}`,
    title: d.duplicate ? `${d.eventId} · ${tl(LABELS.duplicate, lang)}` : d.eventId,
    subtitle: d.payload,
    highlighted: highlight.has(`broker:${d.eventId}`),
    dim: d.duplicate,
  }));

  return (
    <div style={wrapStyle}>
      <ActionBanner
        action={state.action}
        highlighted={highlight.has('action')}
        lang={lang}
      />

      <div style={statsStyle}>
        <Stat label={tl(LABELS.committed, lang)} value={state.committed} color="#79c0ff" />
        <Stat label={tl(LABELS.published, lang)} value={state.published} color="#7ee787" />
        <Stat label={tl(LABELS.redelivered, lang)} value={state.redelivered} color="#ffcc66" />
        <Stat label={tl(LABELS.lost, lang)} value={state.lost} color="#ff7b72" />
      </div>

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.ordersTable, lang)}</div>
        <BoxGroup boxes={orderBoxes} />
      </div>
      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.outboxTable, lang)}</div>
        <BoxGroup boxes={outboxBoxes} />
      </div>
      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.brokerTable, lang)}</div>
        <BoxGroup boxes={brokerBoxes} />
      </div>
    </div>
  );
}

function ActionBanner({
  action,
  highlighted,
  lang,
}: {
  action: CurrentAction | undefined;
  highlighted: boolean;
  lang: Lang;
}) {
  if (!action) {
    return <div style={{ ...bannerStyle, opacity: 0.5 }}>{tl(LABELS.noAction, lang)}</div>;
  }
  let outcome = tl(LABELS.outcomeCommitted, lang);
  let color = '#7ee787';
  if (action.outcome === 'rolledback') {
    outcome = tl(LABELS.outcomeRolledback, lang);
    color = '#ff7b72';
  } else if (action.outcome === 'published') {
    outcome = tl(LABELS.outcomePublished, lang);
    color = '#7ee787';
  } else if (action.outcome === 'redelivered') {
    outcome = tl(LABELS.outcomeRedelivered, lang);
    color = '#ffcc66';
  } else if (action.outcome === 'lost') {
    outcome = tl(LABELS.outcomeLost, lang);
    color = '#ff7b72';
  }
  return (
    <div style={{ ...bannerStyle, ...(highlighted ? bannerHighlightStyle : {}) }}>
      <span style={tagStyle}>{tl(LABELS.action, lang)}</span>
      <span style={idStyle}>{action.id}</span>
      <span style={payloadStyle}>{action.info}</span>
      <span style={{ ...outcomeStyle, color }}>{outcome}</span>
    </div>
  );
}

function Stat({ label, value, color }: { label: string; value: number; color: string }) {
  return (
    <div style={statStyle}>
      <div style={statLabelStyle}>{label}</div>
      <div style={{ ...statValueStyle, color }}>{value}</div>
    </div>
  );
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 14 };
const bannerStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'baseline',
  gap: 10,
  padding: '8px 12px',
  borderRadius: 8,
  background: '#16202b',
  border: '1px solid #2b3a4a',
};
const bannerHighlightStyle: CSSProperties = {
  borderColor: '#ffcc66',
  boxShadow: '0 0 0 2px rgba(255,204,102,0.30)',
};
const tagStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 11,
  padding: '1px 6px',
  borderRadius: 4,
  background: '#324456',
};
const idStyle: CSSProperties = { fontWeight: 700, fontSize: 15, fontFamily: 'monospace' };
const payloadStyle: CSSProperties = { fontSize: 13, opacity: 0.8 };
const outcomeStyle: CSSProperties = { marginLeft: 'auto', fontSize: 13, fontWeight: 600 };
const statsStyle: CSSProperties = { display: 'flex', gap: 20 };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 18, fontWeight: 700, fontFamily: 'monospace' };
const sectionLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6, marginBottom: 6 };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
