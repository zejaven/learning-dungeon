import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to see where an untrusted value lands, what the browser makes of it, and which defence stops it.',
    ru: 'Запустите код, чтобы увидеть, куда попадает недоверенное значение, чем его считает браузер и какая защита его останавливает.',
  },
  defences: { en: 'defences', ru: 'защита' },
  noDefences: { en: 'nothing is encoded, sanitized or restricted', ru: 'ничего не экранируется, не санитайзится и не ограничивается' },
  encodingNone: { en: 'no output encoding', ru: 'без экранирования вывода' },
  encodingNaive: { en: 'escaper: & < > "', ru: 'экранирование: & < > "' },
  encodingContext: { en: 'context-aware encoding', ru: 'контекстное экранирование' },
  sanitizer: { en: 'allowlist sanitizer', ru: 'санитайзер по белому списку' },
  httpOnly: { en: 'HttpOnly session cookie', ru: 'кука сессии HttpOnly' },
  noRender: { en: 'nothing rendered yet', ru: 'пока ничего не отрисовано' },
  sourceReflected: { en: 'reflected', ru: 'отражённый' },
  sourceStored: { en: 'stored', ru: 'хранимый' },
  sourceDom: { en: 'DOM-based', ru: 'DOM-based' },
  untrusted: { en: 'untrusted value', ru: 'недоверенное значение' },
  asRendered: { en: 'as it goes into the page', ru: 'в том виде, в каком попадает в страницу' },
  unchanged: { en: 'unchanged', ru: 'без изменений' },
  encoded: { en: 'encoded', ru: 'экранировано' },
  sanitized: { en: 'sanitized', ru: 'очищено санитайзером' },
  parsed: { en: 'what the browser made of it', ru: 'чем это сочёл браузер' },
  kindMarkup: { en: 'template markup', ru: 'разметка шаблона' },
  kindText: { en: 'text node', ru: 'текстовый узел' },
  kindScript: { en: 'executable', ru: 'исполняемое' },
  storedTitle: { en: 'saved on the server', ru: 'сохранено на сервере' },
  cookie: { en: 'session cookie', ru: 'кука сессии' },
  cookieReadable: { en: 'readable by any script on the page', ru: 'доступна любому скрипту на странице' },
  cookieHidden: { en: 'hidden from document.cookie', ru: 'скрыта от document.cookie' },
  cookieGone: { en: 'sent to the attacker', ru: 'отправлена злоумышленнику' },
  pending: { en: 'rendering…', ru: 'отрисовка…' },
  safe: { en: 'stayed data', ru: 'осталось данными' },
  injectedBlocked: { en: 'injected, not executed', ru: 'внедрено, но не выполнено' },
  executedVerdict: { en: 'script executed', ru: 'скрипт выполнился' },
  byEncoding: { en: 'stopped by output encoding', ru: 'остановлено экранированием вывода' },
  bySanitizer: { en: 'stopped by the sanitizer', ru: 'остановлено санитайзером' },
  byCsp: { en: 'stopped by the Content-Security-Policy', ru: 'остановлено Content-Security-Policy' },
  harmless: { en: 'this value was never dangerous', ru: 'это значение и не было опасным' },
  statRenders: { en: 'renders', ru: 'отрисовок' },
  statInjections: { en: 'injections', ru: 'внедрений' },
  statExecutions: { en: 'executions', ru: 'выполнений' },
  statStolen: { en: 'cookies stolen', ru: 'украдено кук' },
  statCsp: { en: 'blocked by CSP', ru: 'заблокировано CSP' },
  statSafe: { en: 'kept as data', ru: 'осталось данными' },
  sinks: {
    HTML_TEXT: { en: 'between tags', ru: 'между тегами' },
    ATTRIBUTE: { en: 'in an attribute', ru: 'в атрибуте' },
    SCRIPT: { en: 'in a <script> block', ru: 'в блоке <script>' },
    URL: { en: 'in a link target', ru: 'в адресе ссылки' },
    INNER_HTML: { en: 'in innerHTML', ru: 'в innerHTML' },
  },
};

type SinkId = keyof typeof LABELS.sinks;

interface Defences {
  encoding: 'none' | 'naive' | 'context-aware';
  sanitizer: boolean;
  csp: string | null;
  httpOnly: boolean;
}
interface Cookie {
  name: string;
  value: string;
  httpOnly: boolean;
  stolen: boolean;
}
interface Stored {
  field: string;
  value: string;
  dangerous: boolean;
}
interface Render {
  source: 'reflected' | 'stored' | 'dom';
  sink: SinkId;
  input: string;
  output: string;
  encoded: boolean;
  sanitized: boolean;
  html: string | null;
}
interface Outcome {
  stage: 'idle' | 'rendered' | 'parsed' | 'settled';
  injected: boolean;
  executed: boolean;
  blockedBy: 'encoding' | 'sanitizer' | 'csp' | null;
}
interface Node {
  kind: 'markup' | 'text' | 'script';
  text: string;
  danger: boolean;
}
interface Stats {
  renders: number;
  injections: number;
  executions: number;
  cookiesStolen: number;
  blockedByCsp: number;
  neutralized: number;
}
interface XssState {
  defences: Defences;
  cookie: Cookie;
  stored: Stored[];
  render?: Render | null;
  outcome?: Outcome | null;
  nodes: Node[];
  stats: Stats;
}

export default function XssVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as XssState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const { render, outcome } = state;

  return (
    <div style={wrapStyle}>
      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.defences, lang)}</div>
        {defenceBoxes(state.defences, lang).length === 0 ? (
          <div style={{ ...mutedStyle, color: 'var(--bad)' }}>{tl(LABELS.noDefences, lang)}</div>
        ) : (
          <BoxGroup boxes={defenceBoxes(state.defences, lang, highlight.has('defences'))} />
        )}
      </div>

      {render && outcome ? (
        <>
          <div style={bannerStyle}>
            <span style={chipStyle}>{tl(sourceLabel(render.source), lang)}</span>
            <span style={chipStyle}>{tl(LABELS.sinks[render.sink], lang)}</span>
            <span style={{ ...verdictStyle, color: verdictOf(outcome).color }}>
              {tl(verdictOf(outcome).label, lang)}
            </span>
            <span style={reasonStyle}>{tl(reasonOf(outcome), lang)}</span>
          </div>

          <div>
            <div style={sectionLabelStyle}>{tl(LABELS.untrusted, lang)}</div>
            <div style={{ ...codeStyle, ...(highlight.has('input') ? codeActiveStyle : {}) }}>
              {render.input || ' '}
            </div>
          </div>

          <div>
            <div style={sectionLabelStyle}>
              {tl(LABELS.asRendered, lang)} — {tl(transformLabel(render), lang)}
            </div>
            <div style={{ ...codeStyle, ...(highlight.has('html') ? codeActiveStyle : {}) }}>
              {render.html ?? render.output}
            </div>
          </div>
        </>
      ) : (
        <div style={{ ...bannerStyle, opacity: 0.5 }}>{tl(LABELS.noRender, lang)}</div>
      )}

      {state.nodes.length > 0 && (
        <div>
          <div style={sectionLabelStyle}>{tl(LABELS.parsed, lang)}</div>
          <BoxGroup boxes={nodeBoxes(state.nodes, lang)} />
        </div>
      )}

      {state.stored.length > 0 && (
        <div>
          <div style={sectionLabelStyle}>{tl(LABELS.storedTitle, lang)}</div>
          <BoxGroup
            boxes={state.stored.map((row) => ({
              id: `stored-${row.field}`,
              title: `${row.field}: ${row.value}`,
              highlighted: row.dangerous && highlight.has('stored'),
            }))}
          />
        </div>
      )}

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.cookie, lang)}</div>
        <BoxGroup
          boxes={[
            {
              id: 'cookie',
              title: `${state.cookie.name}=${state.cookie.value}`,
              subtitle: tl(cookieLabel(state.cookie), lang),
              highlighted: highlight.has('cookie') && !state.cookie.httpOnly,
              dim: state.cookie.httpOnly,
            },
          ]}
        />
      </div>

      <div style={statsStyle}>
        <Stat label={tl(LABELS.statRenders, lang)} value={state.stats.renders} />
        <Stat
          label={tl(LABELS.statInjections, lang)}
          value={state.stats.injections}
          color={state.stats.injections > 0 ? 'var(--accent)' : undefined}
        />
        <Stat
          label={tl(LABELS.statExecutions, lang)}
          value={state.stats.executions}
          color={state.stats.executions > 0 ? 'var(--bad)' : undefined}
        />
        <Stat
          label={tl(LABELS.statStolen, lang)}
          value={state.stats.cookiesStolen}
          color={state.stats.cookiesStolen > 0 ? 'var(--bad)' : undefined}
        />
        <Stat label={tl(LABELS.statCsp, lang)} value={state.stats.blockedByCsp} />
        <Stat
          label={tl(LABELS.statSafe, lang)}
          value={state.stats.neutralized}
          color={state.stats.neutralized > 0 ? 'var(--good)' : undefined}
        />
      </div>
    </div>
  );
}

function defenceBoxes(defences: Defences, lang: Lang, highlighted = false): Box[] {
  const boxes: Box[] = [];
  if (defences.encoding !== 'none') {
    boxes.push({
      id: 'encoding',
      title: tl(defences.encoding === 'naive' ? LABELS.encodingNaive : LABELS.encodingContext, lang),
      highlighted,
    });
  }
  if (defences.sanitizer) {
    boxes.push({ id: 'sanitizer', title: tl(LABELS.sanitizer, lang), highlighted });
  }
  if (defences.csp) {
    boxes.push({ id: 'csp', title: `Content-Security-Policy: ${defences.csp}`, highlighted });
  }
  if (defences.httpOnly) {
    boxes.push({ id: 'httponly', title: tl(LABELS.httpOnly, lang), highlighted });
  }
  return boxes;
}

function nodeBoxes(nodes: Node[], lang: Lang): Box[] {
  return nodes.map((node, index) => ({
    id: `node-${index}`,
    title: node.text || ' ',
    subtitle: tl(
      node.kind === 'markup' ? LABELS.kindMarkup : node.kind === 'text' ? LABELS.kindText : LABELS.kindScript,
      lang,
    ),
    highlighted: node.danger,
    dim: node.kind === 'markup',
  }));
}

function sourceLabel(source: Render['source']) {
  if (source === 'stored') return LABELS.sourceStored;
  if (source === 'dom') return LABELS.sourceDom;
  return LABELS.sourceReflected;
}

function transformLabel(render: Render) {
  if (render.sanitized) return LABELS.sanitized;
  if (render.encoded) return LABELS.encoded;
  return LABELS.unchanged;
}

function cookieLabel(cookie: Cookie) {
  if (cookie.stolen) return LABELS.cookieGone;
  return cookie.httpOnly ? LABELS.cookieHidden : LABELS.cookieReadable;
}

function verdictOf(outcome: Outcome) {
  if (outcome.stage !== 'settled') {
    return { label: LABELS.pending, color: 'var(--accent)' };
  }
  if (outcome.executed) {
    return { label: LABELS.executedVerdict, color: 'var(--bad)' };
  }
  if (outcome.injected) {
    return { label: LABELS.injectedBlocked, color: 'var(--accent)' };
  }
  return { label: LABELS.safe, color: 'var(--good)' };
}

function reasonOf(outcome: Outcome) {
  if (outcome.blockedBy === 'encoding') return LABELS.byEncoding;
  if (outcome.blockedBy === 'sanitizer') return LABELS.bySanitizer;
  if (outcome.blockedBy === 'csp') return LABELS.byCsp;
  if (outcome.stage === 'settled' && !outcome.injected) return LABELS.harmless;
  return LABELS.pending;
}

function Stat({ label, value, color }: { label: string; value: number; color?: string }) {
  return (
    <div style={statStyle}>
      <div style={statLabelStyle}>{label}</div>
      <div style={{ ...statValueStyle, color: color ?? 'inherit' }}>{value}</div>
    </div>
  );
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 14 };
const bannerStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'baseline',
  gap: 10,
  flexWrap: 'wrap',
  padding: '8px 12px',
  borderRadius: 8,
  background: 'var(--viz-box)',
  border: '1px solid var(--viz-active)',
};
const chipStyle: CSSProperties = {
  fontSize: 11,
  padding: '1px 6px',
  borderRadius: 4,
  background: 'var(--viz-badge)',
};
const verdictStyle: CSSProperties = { marginLeft: 'auto', fontSize: 13, fontWeight: 600 };
const reasonStyle: CSSProperties = { width: '100%', fontSize: 11, opacity: 0.75 };
const codeStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 12,
  padding: '6px 10px',
  borderRadius: 6,
  border: '1px solid var(--border)',
  background: 'var(--viz-box)',
  wordBreak: 'break-all',
};
const codeActiveStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  background: 'var(--viz-highlight)',
};
const statsStyle: CSSProperties = { display: 'flex', gap: 18, flexWrap: 'wrap' };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 18, fontWeight: 700, fontFamily: 'monospace' };
const sectionLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6, marginBottom: 6 };
const mutedStyle: CSSProperties = { fontSize: 12, opacity: 0.5, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
