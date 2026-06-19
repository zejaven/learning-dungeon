import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  message: { en: 'message', ru: 'сообщение' },
  amount: { en: 'amount', ru: 'сумма' },
  inboxTable: { en: 'inbox table (processed ids)', ru: 'таблица inbox (обработанные id)' },
  processed: { en: 'processed', ru: 'обработано' },
  skipped: { en: 'skipped (dupes)', ru: 'пропущено (дубли)' },
  effect: { en: 'effect total', ru: 'итог эффекта' },
  outcomeReceived: { en: 'received…', ru: 'получено…' },
  outcomeProcessed: { en: 'processed ✓', ru: 'обработано ✓' },
  outcomeDuplicate: { en: 'duplicate — skipped ✗', ru: 'дубликат — пропущено ✗' },
  noMessage: { en: 'no message in flight', ru: 'нет сообщения в полёте' },
  runHint: {
    en: 'Run the code to visualize the inbox.',
    ru: 'Запустите код, чтобы визуализировать inbox.',
  },
};

interface InboxEntry {
  id: string;
  payload: string;
  amount: number;
}
interface CurrentMessage {
  id: string;
  payload: string;
  amount: number;
  outcome: 'received' | 'processed' | 'duplicate';
}
interface InboxState {
  name: string;
  inbox: InboxEntry[];
  processedCount: number;
  skippedCount: number;
  effect: number;
  message?: CurrentMessage;
}

export default function InboxVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as InboxState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);

  const boxes: Box[] = state.inbox.map((e) => ({
    id: e.id,
    title: e.id,
    subtitle: `${e.payload} · ${e.amount}`,
    highlighted: highlight.has(`inbox:${e.id}`),
  }));

  return (
    <div style={wrapStyle}>
      <MessageBanner
        msg={state.message}
        highlighted={highlight.has('message')}
        lang={lang}
      />

      <div style={statsStyle}>
        <Stat label={tl(LABELS.processed, lang)} value={state.processedCount} color="var(--good)" />
        <Stat label={tl(LABELS.skipped, lang)} value={state.skippedCount} color="var(--bad)" />
        <Stat label={tl(LABELS.effect, lang)} value={state.effect} color="var(--accent-2)" />
      </div>

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.inboxTable, lang)}</div>
        <BoxGroup boxes={boxes} />
      </div>
    </div>
  );
}

function MessageBanner({
  msg,
  highlighted,
  lang,
}: {
  msg: CurrentMessage | undefined;
  highlighted: boolean;
  lang: Lang;
}) {
  if (!msg) {
    return <div style={{ ...bannerStyle, opacity: 0.5 }}>{tl(LABELS.noMessage, lang)}</div>;
  }
  let outcome = tl(LABELS.outcomeReceived, lang);
  let color = 'var(--accent)';
  if (msg.outcome === 'processed') {
    outcome = tl(LABELS.outcomeProcessed, lang);
    color = 'var(--good)';
  } else if (msg.outcome === 'duplicate') {
    outcome = tl(LABELS.outcomeDuplicate, lang);
    color = 'var(--bad)';
  }
  return (
    <div style={{ ...bannerStyle, ...(highlighted ? bannerHighlightStyle : {}) }}>
      <span style={tagStyle}>{tl(LABELS.message, lang)}</span>
      <span style={idStyle}>{msg.id}</span>
      <span style={payloadStyle}>{msg.payload}</span>
      <span style={payloadStyle}>
        {tl(LABELS.amount, lang)} {msg.amount}
      </span>
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
  background: 'var(--viz-box)',
  border: '1px solid var(--viz-active)',
};
const bannerHighlightStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  boxShadow: '0 0 0 2px rgba(255,204,102,0.30)',
};
const tagStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 11,
  padding: '1px 6px',
  borderRadius: 4,
  background: 'var(--viz-badge)',
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
