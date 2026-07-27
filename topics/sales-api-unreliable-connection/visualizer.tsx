import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  online: { en: 'online', ru: 'онлайн' },
  offline: { en: 'offline', ru: 'офлайн' },
  localQueue: { en: 'local queue (client)', ru: 'локальная очередь (клиент)' },
  store: { en: 'idempotency store (server)', ru: 'хранилище идемпотентности (сервер)' },
  attempt: { en: 'attempt', ru: 'попытка' },
  noAttempt: { en: 'no request in flight', ru: 'нет запроса в полёте' },
  pending: { en: 'pending', ru: 'в очереди' },
  registered: { en: 'registered', ru: 'зарегистрировано' },
  deduped: { en: 'deduplicated', ru: 'дедуплицировано' },
  doubleCharged: { en: 'double-charged', ru: 'двойных списаний' },
  serverTotal: { en: 'server total', ru: 'итог на сервере' },
  retryIn: { en: 'retry in', ru: 'повтор через' },
  confirmed: { en: 'confirmed', ru: 'подтверждено' },
  outcomeRequestLost: {
    en: 'request lost — server untouched',
    ru: 'запрос потерян — сервер не тронут',
  },
  outcomeResponseLost: {
    en: 'response lost — server already applied it',
    ru: 'ответ потерян — сервер уже применил',
  },
  outcomeDelivered: { en: 'delivered ✓', ru: 'доставлено ✓' },
  runHint: {
    en: 'Run the code to visualize the sale registration.',
    ru: 'Запустите код, чтобы визуализировать регистрацию продаж.',
  },
};

interface QueuedSale {
  id: string;
  item: string;
  amount: number;
  status: 'pending' | 'confirmed';
  attempts: number;
  nextRetryMs: number;
}
interface StoredRecord {
  key: string;
  item: string;
  amount: number;
  response: string;
}
interface CurrentAttempt {
  id: string;
  number: number;
  outcome: 'request_lost' | 'response_lost' | 'delivered';
}
interface SaleState {
  device: string;
  online: boolean;
  sales: QueuedSale[];
  pendingCount: number;
  server: StoredRecord[];
  serverTotal: number;
  appliedCount: number;
  duplicateCount: number;
  doubleChargeCount: number;
  attempt?: CurrentAttempt;
}

export default function SaleRegistrationVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as SaleState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);

  const queueBoxes: Box[] = state.sales.map((sale) => ({
    id: sale.id,
    title: sale.id,
    subtitle:
      sale.status === 'confirmed'
        ? `${sale.item} · ${sale.amount} · ${tl(LABELS.confirmed, lang)} ✓`
        : `${sale.item} · ${sale.amount} · ×${sale.attempts} · ` +
          `${tl(LABELS.retryIn, lang)} ${sale.nextRetryMs}ms`,
    highlighted: highlight.has(`sale:${sale.id}`),
    dim: sale.status === 'confirmed',
  }));

  const storeBoxes: Box[] = state.server.map((rec) => ({
    id: rec.key,
    title: rec.key,
    subtitle: `${rec.item} · ${rec.amount} · ${rec.response}`,
    highlighted: highlight.has(`record:${rec.key}`),
  }));

  return (
    <div style={wrapStyle}>
      <div style={headerStyle}>
        <span style={deviceStyle}>{state.device}</span>
        <span style={state.online ? onlinePillStyle : offlinePillStyle}>
          {tl(state.online ? LABELS.online : LABELS.offline, lang)}
        </span>
      </div>

      <AttemptBanner attempt={state.attempt} lang={lang} />

      <div style={statsStyle}>
        <Stat label={tl(LABELS.pending, lang)} value={state.pendingCount} color="var(--accent)" />
        <Stat label={tl(LABELS.registered, lang)} value={state.appliedCount} color="var(--good)" />
        <Stat
          label={tl(LABELS.deduped, lang)}
          value={state.duplicateCount}
          color="var(--accent-2)"
        />
        <Stat
          label={tl(LABELS.doubleCharged, lang)}
          value={state.doubleChargeCount}
          color="var(--bad)"
        />
        <Stat
          label={tl(LABELS.serverTotal, lang)}
          value={state.serverTotal}
          color={state.doubleChargeCount > 0 ? 'var(--bad)' : 'var(--good)'}
        />
      </div>

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.localQueue, lang)}</div>
        <BoxGroup boxes={queueBoxes} />
      </div>

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.store, lang)}</div>
        <BoxGroup boxes={storeBoxes} />
      </div>
    </div>
  );
}

function AttemptBanner({ attempt, lang }: { attempt: CurrentAttempt | undefined; lang: Lang }) {
  if (!attempt) {
    return <div style={{ ...bannerStyle, opacity: 0.5 }}>{tl(LABELS.noAttempt, lang)}</div>;
  }
  let outcome = tl(LABELS.outcomeDelivered, lang);
  let color = 'var(--good)';
  if (attempt.outcome === 'request_lost') {
    outcome = tl(LABELS.outcomeRequestLost, lang);
    color = 'var(--bad)';
  } else if (attempt.outcome === 'response_lost') {
    outcome = tl(LABELS.outcomeResponseLost, lang);
    color = 'var(--accent-2)';
  }
  return (
    <div style={bannerStyle}>
      <span style={tagStyle}>
        {tl(LABELS.attempt, lang)} #{attempt.number}
      </span>
      <span style={idStyle}>{attempt.id}</span>
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
const headerStyle: CSSProperties = { display: 'flex', alignItems: 'center', gap: 10 };
const deviceStyle: CSSProperties = { fontFamily: 'monospace', fontWeight: 700, fontSize: 14 };
const pillBase: CSSProperties = {
  fontSize: 11,
  padding: '2px 8px',
  borderRadius: 999,
  border: '1px solid var(--border)',
};
const onlinePillStyle: CSSProperties = { ...pillBase, color: 'var(--good)' };
const offlinePillStyle: CSSProperties = { ...pillBase, color: 'var(--bad)' };
const bannerStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'baseline',
  gap: 10,
  padding: '8px 12px',
  borderRadius: 8,
  background: 'var(--viz-box)',
  border: '1px solid var(--viz-active)',
};
const tagStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 11,
  padding: '1px 6px',
  borderRadius: 4,
  background: 'var(--viz-badge)',
};
const idStyle: CSSProperties = { fontWeight: 700, fontSize: 15, fontFamily: 'monospace' };
const outcomeStyle: CSSProperties = { marginLeft: 'auto', fontSize: 13, fontWeight: 600 };
const statsStyle: CSSProperties = { display: 'flex', gap: 18, flexWrap: 'wrap' };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 18, fontWeight: 700, fontFamily: 'monospace' };
const sectionLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6, marginBottom: 6 };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
