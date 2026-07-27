import type { CSSProperties, ReactNode } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid } from '@app/primitives/ArrayGrid';
import { BoxGroup } from '@app/primitives/BoxGroup';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to follow one page load down the layers: URL, DNS, TCP, TLS, HTTP, and everything the document still needs.',
    ru: 'Запустите код, чтобы пройти одну загрузку страницы по слоям: URL, DNS, TCP, TLS, HTTP — и всё, что документу ещё нужно.',
  },
  typed: { en: 'typed', ru: 'набрано' },
  visit: { en: 'visit', ru: 'визит' },
  hstsOn: { en: 'HSTS: https only', ru: 'HSTS: только https' },
  pipeline: { en: 'the layers, in order', ru: 'слои по порядку' },
  stageNames: {
    URL: { en: 'URL', ru: 'URL' },
    DNS: { en: 'DNS', ru: 'DNS' },
    TCP: { en: 'TCP', ru: 'TCP' },
    TLS: { en: 'TLS', ru: 'TLS' },
    HTTP: { en: 'HTTP', ru: 'HTTP' },
    CONTENT: { en: 'content', ru: 'содержимое' },
    RENDER: { en: 'render', ru: 'отрисовка' },
  },
  statuses: {
    PENDING: { en: 'waiting', ru: 'ждёт' },
    DONE: { en: 'done', ru: 'выполнено' },
    CACHED: { en: 'from cache — free', ru: 'из кэша — бесплатно' },
    REUSED: { en: 'reused — free', ru: 'переиспользовано — бесплатно' },
    SKIPPED: { en: 'skipped', ru: 'пропущено' },
  },
  dns: { en: 'name to address', ru: 'от имени к адресу' },
  noDns: { en: 'nothing has been looked up yet', ru: 'пока ничего не разрешалось' },
  dnsFrom: {
    BROWSER_CACHE: { en: 'browser cache', ru: 'кэш браузера' },
    OS_CACHE: { en: 'OS resolver', ru: 'системный резолвер' },
    HOSTS_FILE: { en: 'hosts file', ru: 'файл hosts' },
    RESOLVER: { en: 'recursive resolver', ru: 'рекурсивный резолвер' },
    ROOT: { en: 'root server', ru: 'корневой сервер' },
    TLD: { en: 'TLD server', ru: 'сервер зоны' },
    AUTHORITATIVE: { en: 'authoritative server', ru: 'авторитативный сервер' },
  },
  dnsResults: {
    MISS: { en: 'miss', ru: 'промах' },
    HIT: { en: 'hit', ru: 'попадание' },
    REFERRAL: { en: 'referral', ru: 'отсылка' },
    ANSWER: { en: 'answer', ru: 'ответ' },
  },
  connection: { en: 'connection', ru: 'соединение' },
  connectionStates: {
    NONE: { en: 'nothing is open', ru: 'ничего не открыто' },
    ESTABLISHED: { en: 'open, in the clear', ru: 'открыто, без шифрования' },
    SECURE: { en: 'open and encrypted', ru: 'открыто и зашифровано' },
    CLOSED: { en: 'closed', ru: 'закрыто' },
  },
  onConnection: { en: 'requests on it', ru: 'запросов по нему' },
  wire: { en: 'on the wire', ru: 'на проводе' },
  noWire: { en: 'nothing has been sent yet', ru: 'пока ничего не отправлено' },
  page: { en: 'what the page is made of', ru: 'из чего состоит страница' },
  noResources: { en: 'no resource has arrived yet', ru: 'ни один ресурс ещё не пришёл' },
  pending: { en: 'discovered, not fetched', ru: 'найдено, но не загружено' },
  sources: {
    NETWORK: { en: 'network', ru: 'сеть' },
    DISK_CACHE: { en: 'cache', ru: 'кэш' },
  },
  kinds: {
    DOCUMENT: { en: 'document', ru: 'документ' },
    STYLESHEET: { en: 'stylesheet', ru: 'таблица стилей' },
    SCRIPT: { en: 'script', ru: 'скрипт' },
    IMAGE: { en: 'image', ru: 'изображение' },
    OTHER: { en: 'other', ru: 'прочее' },
  },
  milestones: { en: 'milestones', ru: 'вехи' },
  milestoneNames: {
    TTFB: { en: 'first byte', ru: 'первый байт' },
    DOM_INTERACTIVE: { en: 'DOM built', ru: 'DOM построен' },
    FIRST_PAINT: { en: 'first paint', ru: 'первая отрисовка' },
  },
  statMs: { en: 'ms since Enter', ru: 'мс после Enter' },
  statRoundTrips: { en: 'round trips', ru: 'round trip' },
  statDns: { en: 'DNS queries', ru: 'DNS-запросов' },
  statConnections: { en: 'connections opened', ru: 'соединений открыто' },
  statRequests: { en: 'HTTP requests', ru: 'HTTP-запросов' },
  statCache: { en: 'from cache', ru: 'из кэша' },
  statRedirects: { en: 'redirects', ru: 'редиректов' },
  statBytes: { en: 'bytes', ru: 'байт' },
  comparison: { en: 'the same document, five starting conditions', ru: 'один документ, пять стартовых условий' },
  scenarios: {
    COLD_TLS12: { en: 'cold, TLS 1.2', ru: 'холодная, TLS 1.2' },
    COLD_TLS13: { en: 'cold, TLS 1.3', ru: 'холодная, TLS 1.3' },
    WARM_DNS: { en: 'DNS cached', ru: 'DNS в кэше' },
    REUSED_CONNECTION: { en: 'connection open', ru: 'соединение открыто' },
    FROM_CACHE: { en: 'page in cache', ru: 'страница в кэше' },
  },
  colScenario: { en: 'starting condition', ru: 'стартовое условие' },
  colTotal: { en: 'round trips', ru: 'round trip' },
  colMs: { en: 'to first byte', ru: 'до первого байта' },
};

type StageName = keyof typeof LABELS.stageNames;
type StageStatus = keyof typeof LABELS.statuses;
type DnsFrom = keyof typeof LABELS.dnsFrom;
type DnsResult = keyof typeof LABELS.dnsResults;
type ConnState = keyof typeof LABELS.connectionStates;
type Source = keyof typeof LABELS.sources;
type Kind = keyof typeof LABELS.kinds;
type MilestoneName = keyof typeof LABELS.milestoneNames;
type Scenario = keyof typeof LABELS.scenarios;

interface Url {
  scheme: string;
  host: string;
  port: number;
  path: string;
  hsts: boolean;
}
interface Stage {
  name: StageName;
  status: StageStatus;
  ms: number;
  detail: string;
}
interface DnsStep {
  from: DnsFrom;
  question: string;
  result: DnsResult;
  detail: string;
  ms: number;
}
interface Dns {
  ip: string | null;
  ttl: number | null;
  cached: boolean;
  steps: DnsStep[];
}
interface Connection {
  state: ConnState;
  peer: string | null;
  tls: string | null;
  alpn: string | null;
  requests: number;
}
interface WireMessage {
  seq: number;
  dir: 'OUT' | 'IN';
  layer: 'TCP' | 'TLS' | 'HTTP';
  label: string;
  detail: string;
  bytes: number;
}
interface Resource {
  name: string;
  kind: Kind;
  source: Source;
  ms: number;
  bytes: number;
}
interface Milestone {
  name: MilestoneName;
  ms: number;
}
interface Page {
  resources: Resource[];
  pending: string[];
  milestones: Milestone[];
}
interface Stats {
  ms: number;
  roundTrips: number;
  dnsQueries: number;
  connections: number;
  requests: number;
  cacheHits: number;
  redirects: number;
  bytes: number;
}
interface ComparisonRow {
  scenario: Scenario;
  dnsRtt: number;
  tcpRtt: number;
  tlsRtt: number;
  httpRtt: number;
  totalRtt: number;
  ms: number;
}
interface PageLoadState {
  typed: string;
  visit: number;
  url: Url;
  stages: Stage[];
  dns: Dns;
  connection: Connection;
  wire: WireMessage[];
  page: Page;
  stats: Stats;
  comparison?: ComparisonRow[];
}

const STAGE_TOKEN: Record<StageName, string> = {
  URL: 'url',
  DNS: 'dns',
  TCP: 'tcp',
  TLS: 'tls',
  HTTP: 'http',
  CONTENT: 'content',
  RENDER: 'render',
};

export default function PageLoadVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as PageLoadState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const comparison = state.comparison ?? [];
  if (comparison.length > 0) {
    return <ComparisonTable rows={comparison} lang={lang} />;
  }

  return (
    <div style={wrapStyle}>
      <Header state={state} lang={lang} />

      <Pane title={tl(LABELS.pipeline, lang)} highlighted={false}>
        <BoxGroup
          boxes={state.stages.map((stage) => ({
            id: stage.name,
            title: `${tl(LABELS.stageNames[stage.name], lang)} · ${stage.ms} ms`,
            subtitle: stage.detail || tl(LABELS.statuses[stage.status], lang),
            highlighted: highlight.has(STAGE_TOKEN[stage.name]),
            dim: stage.status === 'PENDING' && !stage.detail,
          }))}
        />
      </Pane>

      <div style={sidesStyle}>
        <Pane title={tl(LABELS.dns, lang)} highlighted={highlight.has('dns')}>
          {state.dns.steps.length === 0 ? (
            <div style={mutedStyle}>{tl(LABELS.noDns, lang)}</div>
          ) : (
            <ArrayGrid
              cells={state.dns.steps.map((step, index) => ({
                key: `${step.from}-${index}`,
                label: tl(LABELS.dnsResults[step.result], lang),
                highlighted: index === state.dns.steps.length - 1,
                content: (
                  <span style={rowStyle}>
                    <span style={strongStyle}>{tl(LABELS.dnsFrom[step.from], lang)}</span>
                    <span style={monoStyle}>{step.question}</span>
                    <span style={metaStyle}>{`${step.detail} · ${step.ms} ms`}</span>
                  </span>
                ),
              }))}
            />
          )}
        </Pane>

        <Pane
          title={tl(LABELS.page, lang)}
          highlighted={highlight.has('content') || highlight.has('render')}
        >
          {state.page.resources.length === 0 ? (
            <div style={mutedStyle}>{tl(LABELS.noResources, lang)}</div>
          ) : (
            <div style={listStyle}>
              {state.page.resources.map((res) => (
                <div key={res.name} style={rowStyle}>
                  <span style={monoStyle}>{res.name}</span>
                  <span style={metaStyle}>{tl(LABELS.kinds[res.kind], lang)}</span>
                  <span
                    style={{
                      ...metaStyle,
                      color: res.source === 'DISK_CACHE' ? 'var(--good)' : undefined,
                    }}
                  >
                    {`${tl(LABELS.sources[res.source], lang)} · ${res.ms} ms`}
                  </span>
                </div>
              ))}
            </div>
          )}
          {state.page.pending.length > 0 && (
            <div style={metaStyle}>
              {`${tl(LABELS.pending, lang)}: ${state.page.pending.join(', ')}`}
            </div>
          )}
          {state.page.milestones.length > 0 && (
            <div style={legendStyle}>
              {state.page.milestones.map((mark) => (
                <span key={mark.name}>
                  {`${tl(LABELS.milestoneNames[mark.name], lang)} ${mark.ms} ms`}
                </span>
              ))}
            </div>
          )}
        </Pane>
      </div>

      <Pane
        title={tl(LABELS.wire, lang)}
        highlighted={highlight.has('wire') || highlight.has('tcp') || highlight.has('tls')}
      >
        {state.wire.length === 0 ? (
          <div style={mutedStyle}>{tl(LABELS.noWire, lang)}</div>
        ) : (
          <ArrayGrid
            cells={state.wire.map((message) => ({
              key: message.seq,
              label: `${message.dir === 'OUT' ? '↑' : '↓'} ${message.layer}`,
              highlighted: message.seq === state.wire.length,
              content: (
                <span style={rowStyle}>
                  <span style={strongStyle}>{message.label}</span>
                  <span style={metaStyle}>{message.detail}</span>
                  <span style={metaStyle}>{`${message.bytes} B`}</span>
                </span>
              ),
            }))}
          />
        )}
      </Pane>

      <div style={statsStyle}>
        <Stat label={tl(LABELS.statMs, lang)} value={state.stats.ms} />
        <Stat
          label={tl(LABELS.statRoundTrips, lang)}
          value={state.stats.roundTrips}
          color="var(--accent)"
        />
        <Stat label={tl(LABELS.statDns, lang)} value={state.stats.dnsQueries} />
        <Stat label={tl(LABELS.statConnections, lang)} value={state.stats.connections} />
        <Stat label={tl(LABELS.statRequests, lang)} value={state.stats.requests} />
        <Stat
          label={tl(LABELS.statCache, lang)}
          value={state.stats.cacheHits}
          color={state.stats.cacheHits > 0 ? 'var(--good)' : undefined}
        />
        <Stat
          label={tl(LABELS.statRedirects, lang)}
          value={state.stats.redirects}
          color={state.stats.redirects > 0 ? 'var(--bad)' : undefined}
        />
        <Stat label={tl(LABELS.statBytes, lang)} value={state.stats.bytes} />
      </div>
    </div>
  );
}

function Header({ state, lang }: { state: PageLoadState; lang: Lang }) {
  const { url, connection } = state;
  return (
    <div style={headerStyle}>
      <span style={badgeStyle}>
        {`${url.scheme}://${url.host}:${url.port}${url.path}`}
      </span>
      {state.typed && (
        <span style={pillStyle}>{`${tl(LABELS.typed, lang)}: ${state.typed}`}</span>
      )}
      <span style={pillStyle}>{`${tl(LABELS.visit, lang)} ${state.visit}`}</span>
      {url.hsts && <span style={{ ...pillStyle, color: 'var(--good)' }}>{tl(LABELS.hstsOn, lang)}</span>}
      <span
        style={{
          ...pillStyle,
          color: connection.state === 'SECURE' ? 'var(--good)' : undefined,
        }}
      >
        {`${tl(LABELS.connection, lang)}: ${tl(LABELS.connectionStates[connection.state], lang)}`}
        {connection.peer ? ` · ${connection.peer}` : ''}
        {connection.tls ? ` · ${connection.tls}` : ''}
        {connection.alpn && connection.state !== 'NONE' ? ` · ${connection.alpn}` : ''}
      </span>
      {connection.requests > 0 && (
        <span style={pillStyle}>
          {`${tl(LABELS.onConnection, lang)}: ${connection.requests}`}
        </span>
      )}
    </div>
  );
}

function ComparisonTable({ rows, lang }: { rows: ComparisonRow[]; lang: Lang }) {
  return (
    <div style={wrapStyle}>
      <div style={sectionLabelStyle}>{tl(LABELS.comparison, lang)}</div>
      <div style={tableStyle}>
        <div style={tableHeadStyle}>
          <span style={cellTextStyle}>{tl(LABELS.colScenario, lang)}</span>
          <span style={cellTextStyle}>DNS</span>
          <span style={cellTextStyle}>TCP</span>
          <span style={cellTextStyle}>TLS</span>
          <span style={cellTextStyle}>HTTP</span>
          <span style={cellTextStyle}>{tl(LABELS.colTotal, lang)}</span>
          <span style={cellTextStyle}>{tl(LABELS.colMs, lang)}</span>
        </div>
        {rows.map((row) => (
          <div key={row.scenario} style={tableRowStyle}>
            <span style={{ ...cellTextStyle, fontWeight: 700 }}>
              {tl(LABELS.scenarios[row.scenario], lang)}
            </span>
            <span style={cellTextStyle}>{row.dnsRtt}</span>
            <span style={cellTextStyle}>{row.tcpRtt}</span>
            <span style={cellTextStyle}>{row.tlsRtt}</span>
            <span style={cellTextStyle}>{row.httpRtt}</span>
            <span style={{ ...cellTextStyle, fontWeight: 700, color: 'var(--accent)' }}>
              {row.totalRtt}
            </span>
            <span style={cellTextStyle}>{`${row.ms} ms`}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function Pane({
  title,
  highlighted,
  children,
}: {
  title: string;
  highlighted: boolean;
  children: ReactNode;
}) {
  return (
    <div style={{ ...paneStyle, ...(highlighted ? paneHighlightStyle : {}) }}>
      <div style={sectionLabelStyle}>{title}</div>
      {children}
    </div>
  );
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
const headerStyle: CSSProperties = { display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' };
const badgeStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 12,
  fontWeight: 700,
  padding: '2px 6px',
  borderRadius: 4,
  background: 'var(--viz-badge)',
};
const pillStyle: CSSProperties = {
  fontSize: 11,
  opacity: 0.75,
  border: '1px solid var(--border)',
  borderRadius: 10,
  padding: '1px 7px',
};
const sidesStyle: CSSProperties = { display: 'flex', gap: 10, alignItems: 'stretch', flexWrap: 'wrap' };
const paneStyle: CSSProperties = {
  flex: '1 1 280px',
  minWidth: 250,
  display: 'flex',
  flexDirection: 'column',
  gap: 6,
  border: '1px solid var(--border)',
  borderRadius: 8,
  padding: '8px 10px',
  background: 'var(--viz-box)',
};
const paneHighlightStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  background: 'var(--viz-highlight)',
};
const listStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 3 };
const rowStyle: CSSProperties = { display: 'flex', gap: 8, alignItems: 'baseline', flexWrap: 'wrap' };
const strongStyle: CSSProperties = { fontSize: 12, fontWeight: 700 };
const monoStyle: CSSProperties = { fontSize: 11, fontFamily: 'monospace' };
const metaStyle: CSSProperties = { fontSize: 10, opacity: 0.6, fontFamily: 'monospace' };
const legendStyle: CSSProperties = {
  display: 'flex',
  gap: 12,
  flexWrap: 'wrap',
  fontSize: 10,
  opacity: 0.7,
  marginTop: 4,
};
const tableStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 2 };
const tableHeadStyle: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: '1.8fr repeat(6, 1fr)',
  fontSize: 10,
  opacity: 0.6,
};
const tableRowStyle: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: '1.8fr repeat(6, 1fr)',
  fontSize: 11,
  fontFamily: 'monospace',
  padding: '4px 0',
  borderTop: '1px solid var(--border)',
  alignItems: 'center',
};
const cellTextStyle: CSSProperties = { paddingRight: 6, overflowWrap: 'anywhere' };
const statsStyle: CSSProperties = { display: 'flex', gap: 18, flexWrap: 'wrap' };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 18, fontWeight: 700, fontFamily: 'monospace' };
const sectionLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6, marginBottom: 2 };
const mutedStyle: CSSProperties = { fontSize: 12, opacity: 0.5, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
