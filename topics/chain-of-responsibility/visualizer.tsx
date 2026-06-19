import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { LinkedNodes, type LinkedNode } from '@app/primitives/LinkedNodes';
import { tl, useLang } from '@app/i18n';

const LABELS = {
  request: { en: 'request', ru: 'запрос' },
  amount: { en: 'amount', ru: 'сумма' },
  approves: { en: 'approves ≤', ru: 'одобряет ≤' },
  outcomePending: { en: 'walking the chain…', ru: 'идёт по цепочке…' },
  outcomeHandled: { en: 'handled ✓', ru: 'обработан ✓' },
  outcomeUnhandled: { en: 'unhandled ✗', ru: 'не обработан ✗' },
  noRequest: { en: 'no request in flight', ru: 'нет запроса в полёте' },
  statusInspecting: { en: 'inspecting', ru: 'проверяет' },
  statusPassed: { en: 'passed on', ru: 'передал дальше' },
  statusHandled: { en: 'handled it', ru: 'обработал' },
  runHint: {
    en: 'Run the code to visualize the chain.',
    ru: 'Запустите код, чтобы визуализировать цепочку.',
  },
};

type HandlerStatus = 'idle' | 'inspecting' | 'passed' | 'handled';

interface Handler {
  id: string;
  name: string;
  limit: number;
  status: HandlerStatus;
}
interface RequestState {
  label: string;
  amount: number;
  position: number;
  outcome: 'pending' | 'handled' | 'unhandled';
}
interface ChainState {
  name: string;
  handlers: Handler[];
  request?: RequestState;
}

export default function ChainOfResponsibilityVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as ChainState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const req = state.request;

  const statusLabel = (s: HandlerStatus): string | undefined => {
    if (s === 'inspecting') return tl(LABELS.statusInspecting, lang);
    if (s === 'passed') return tl(LABELS.statusPassed, lang);
    if (s === 'handled') return tl(LABELS.statusHandled, lang);
    return undefined;
  };

  const nodes: LinkedNode[] = state.handlers.map((h) => {
    const status = statusLabel(h.status);
    const subtitle = `${tl(LABELS.approves, lang)} ${h.limit}${status ? ` · ${status}` : ''}`;
    return {
      id: h.id,
      title: h.name,
      subtitle,
      highlighted: highlight.has(`handler:${h.id}`) || h.status === 'inspecting',
    };
  });

  return (
    <div style={wrapStyle}>
      <RequestBanner req={req} highlighted={highlight.has('request')} lang={lang} />
      <LinkedNodes nodes={nodes} />
    </div>
  );
}

function RequestBanner({
  req,
  highlighted,
  lang,
}: {
  req: RequestState | undefined;
  highlighted: boolean;
  lang: 'en' | 'ru';
}) {
  if (!req) {
    return <div style={{ ...bannerStyle, opacity: 0.5 }}>{tl(LABELS.noRequest, lang)}</div>;
  }
  let outcome = tl(LABELS.outcomePending, lang);
  let color = 'var(--accent)';
  if (req.outcome === 'handled') {
    outcome = tl(LABELS.outcomeHandled, lang);
    color = 'var(--good)';
  } else if (req.outcome === 'unhandled') {
    outcome = tl(LABELS.outcomeUnhandled, lang);
    color = 'var(--bad)';
  }
  return (
    <div style={{ ...bannerStyle, ...(highlighted ? bannerHighlightStyle : {}) }}>
      <span style={tagStyle}>{tl(LABELS.request, lang)}</span>
      <span style={labelStyle}>{req.label}</span>
      <span style={amountStyle}>
        {tl(LABELS.amount, lang)} {req.amount}
      </span>
      <span style={{ ...outcomeStyle, color }}>{outcome}</span>
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
const labelStyle: CSSProperties = { fontWeight: 700, fontSize: 15 };
const amountStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 13, opacity: 0.8 };
const outcomeStyle: CSSProperties = { marginLeft: 'auto', fontSize: 13, fontWeight: 600 };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
