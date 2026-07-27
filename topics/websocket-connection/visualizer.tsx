import type { CSSProperties, ReactNode } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid } from '@app/primitives/ArrayGrid';
import { BoxGroup } from '@app/primitives/BoxGroup';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to see the handshake, the frames it turns into, and which object serves each connection.',
    ru: 'Запустите код, чтобы увидеть рукопожатие, фреймы, в которые оно превращается, и объект, обслуживающий каждое соединение.',
  },
  models: {
    PER_CONNECTION: { en: 'one endpoint object per connection', ru: 'объект эндпоинта на соединение' },
    SHARED: { en: 'one shared handler object', ru: 'один общий объект-обработчик' },
  },
  openSockets: { en: 'open sockets', ru: 'открытых сокетов' },
  originCheck: { en: 'Origin allowed', ru: 'разрешённый Origin' },
  originAny: { en: 'no Origin check', ru: 'Origin не проверяется' },
  handshake: { en: 'handshake', ru: 'рукопожатие' },
  noHandshake: { en: 'nothing has connected yet', ru: 'пока никто не подключился' },
  request: { en: 'browser sends', ru: 'браузер отправляет' },
  response: { en: 'server answers', ru: 'сервер отвечает' },
  upgraded: { en: 'upgraded — this is a socket now', ru: 'апгрейд выполнен — теперь это сокет' },
  refused: { en: 'refused — it stayed an HTTP exchange', ru: 'отказано — обмен остался HTTP' },
  pending: { en: 'in flight', ru: 'в полёте' },
  connections: { en: 'connections', ru: 'соединения' },
  instances: { en: 'endpoint objects', ru: 'объекты эндпоинта' },
  noInstances: { en: 'no endpoint object exists yet', ru: 'объекта эндпоинта ещё нет' },
  serves: { en: 'serves', ru: 'обслуживает' },
  servesNobody: { en: 'serves nobody', ru: 'никого не обслуживает' },
  discarded: { en: 'garbage', ru: 'мусор' },
  fields: { en: 'instance fields', ru: 'поля экземпляра' },
  noFields: { en: 'no fields written', ru: 'полей не записано' },
  writtenBy: { en: 'written by', ru: 'записал' },
  session: { en: 'session', ru: 'сессия' },
  phases: {
    HTTP: { en: 'still HTTP', ru: 'ещё HTTP' },
    OPEN: { en: 'open', ru: 'открыт' },
    CLOSED: { en: 'closed', ru: 'закрыт' },
  },
  sentShort: { en: 'up', ru: 'вверх' },
  receivedShort: { en: 'down', ru: 'вниз' },
  frames: { en: 'frames on the wire', ru: 'фреймы на проводе' },
  noFrames: { en: 'no frame has been written yet', ru: 'ни одного фрейма ещё не записано' },
  masked: { en: 'masked', ru: 'маска' },
  bytesShort: { en: 'B', ru: 'Б' },
  legendIn: { en: 'client to server', ru: 'от клиента к серверу' },
  legendOut: { en: 'server to client', ru: 'от сервера к клиенту' },
  statHandshakes: { en: 'handshakes', ru: 'рукопожатий' },
  statRejected: { en: 'rejected', ru: 'отклонено' },
  statInstances: { en: 'endpoint objects', ru: 'объектов эндпоинта' },
  statFramesIn: { en: 'frames in', ru: 'фреймов внутрь' },
  statFramesOut: { en: 'frames out', ru: 'фреймов наружу' },
  statBytes: { en: 'bytes framed', ru: 'байт в кадрах' },
  statClashes: { en: 'state collisions', ru: 'коллизий состояния' },
  statLeaks: { en: 'wrong-client reads', ru: 'чтений чужого' },
  statLost: { en: 'frames lost', ru: 'фреймов потеряно' },
  statDropped: { en: 'abnormal drops', ru: 'аварийных обрывов' },
  comparison: { en: 'three connections, three ways of being served', ru: 'три соединения, три способа обслуживания' },
  modelNames: {
    JSR356_ENDPOINT: { en: '@ServerEndpoint POJO', ru: 'POJO с @ServerEndpoint' },
    JSR356_SINGLETON: { en: '@ServerEndpoint + Configurator', ru: '@ServerEndpoint + Configurator' },
    SPRING_HANDLER: { en: 'Spring WebSocketHandler', ru: 'Spring WebSocketHandler' },
  },
  colModel: { en: 'how it is created', ru: 'как создаётся' },
  colInstances: { en: 'objects for 3 connections', ru: 'объектов на 3 соединения' },
  colField: { en: 'a field is per connection', ru: 'поле принадлежит соединению' },
  colSync: { en: 'needs thread safety', ru: 'нужна потокобезопасность' },
  colInjection: { en: 'injection works', ru: 'внедрение работает' },
  colState: { en: 'keep connection state in', ru: 'состояние соединения хранить в' },
  stateLives: {
    FIELD: { en: 'a field', ru: 'поле' },
    SESSION: { en: 'the Session', ru: 'Session' },
  },
  yes: { en: 'yes', ru: 'да' },
  no: { en: 'no', ru: 'нет' },
};

type ModelCode = keyof typeof LABELS.models;
type PhaseCode = keyof typeof LABELS.phases;
type ComparisonModel = keyof typeof LABELS.modelNames;
type StateHome = keyof typeof LABELS.stateLives;

interface Transport {
  protocol: string;
  port: number;
  tls: boolean;
  sockets: number;
}
interface Handshake {
  client: string | null;
  request: string[];
  response: string[];
  accepted: boolean | null;
}
interface SessionAttr {
  key: string;
  value: string;
}
interface Connection {
  id: string;
  phase: PhaseCode;
  instance: string | null;
  sent: number;
  received: number;
  closeCode: number | null;
  session: SessionAttr[];
}
interface InstanceField {
  name: string;
  value: string;
  owner: string;
}
interface EndpointInstance {
  name: string;
  alive: boolean;
  serves: string[];
  fields: InstanceField[];
}
interface WireFrame {
  seq: number;
  dir: 'IN' | 'OUT';
  opcode: string;
  client: string;
  payload: string;
  masked: boolean;
  bytes: number;
}
interface Stats {
  handshakes: number;
  rejected: number;
  open: number;
  instances: number;
  framesIn: number;
  framesOut: number;
  bytes: number;
  clashes: number;
  leaks: number;
  lost: number;
  closed: number;
  dropped: number;
}
interface ComparisonRow {
  model: ComparisonModel;
  instances: number;
  fieldPerConnection: boolean;
  needsSync: boolean;
  injection: boolean;
  stateLives: StateHome;
}
interface WebSocketState {
  url: string;
  model: ModelCode | null;
  endpointClass: string;
  allowedOrigin: string | null;
  transport: Transport;
  handshake: Handshake;
  connections: Connection[];
  instances: EndpointInstance[];
  frames: WireFrame[];
  stats: Stats;
  comparison?: ComparisonRow[];
}

export default function WebSocketVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as WebSocketState | undefined;
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

      <Pane title={tl(LABELS.handshake, lang)} highlighted={highlight.has('handshake')}>
        <HandshakeLines handshake={state.handshake} lang={lang} />
      </Pane>

      <div style={sidesStyle}>
        <Pane title={tl(LABELS.connections, lang)} highlighted={highlight.has('connections')}>
          {state.connections.length === 0 ? (
            <div style={mutedStyle}>{tl(LABELS.noHandshake, lang)}</div>
          ) : (
            <BoxGroup boxes={state.connections.map((conn) => connectionBox(conn, lang))} />
          )}
        </Pane>

        <Pane
          title={tl(LABELS.instances, lang)}
          highlighted={highlight.has('instances') || highlight.has('clash')}
        >
          {state.instances.length === 0 ? (
            <div style={mutedStyle}>{tl(LABELS.noInstances, lang)}</div>
          ) : (
            <div style={instancesStyle}>
              {state.instances.map((instance) => (
                <InstanceCard key={instance.name} instance={instance} lang={lang} />
              ))}
            </div>
          )}
        </Pane>
      </div>

      <Pane
        title={tl(LABELS.frames, lang)}
        highlighted={highlight.has('frames') || highlight.has('transport')}
      >
        {state.frames.length === 0 ? (
          <div style={mutedStyle}>{tl(LABELS.noFrames, lang)}</div>
        ) : (
          <>
            <ArrayGrid
              cells={state.frames.map((frame) => ({
                key: frame.seq,
                label: `${frame.dir === 'IN' ? '↑' : '↓'} ${frame.opcode}`,
                highlighted: frame.seq === state.frames.length,
                content: (
                  <span style={frameStyle}>
                    <span style={frameClientStyle}>{frame.client}</span>
                    {frame.payload && <span>{frame.payload}</span>}
                    <span style={frameMetaStyle}>
                      {`${frame.bytes} ${tl(LABELS.bytesShort, lang)}`}
                      {frame.masked ? ` · ${tl(LABELS.masked, lang)}` : ''}
                    </span>
                  </span>
                ),
              }))}
            />
            <div style={legendStyle}>
              <span>↑ {tl(LABELS.legendIn, lang)}</span>
              <span>↓ {tl(LABELS.legendOut, lang)}</span>
            </div>
          </>
        )}
      </Pane>

      <div style={statsStyle}>
        <Stat label={tl(LABELS.statHandshakes, lang)} value={state.stats.handshakes} />
        <Stat
          label={tl(LABELS.statRejected, lang)}
          value={state.stats.rejected}
          color={state.stats.rejected > 0 ? 'var(--accent)' : undefined}
        />
        <Stat label={tl(LABELS.statInstances, lang)} value={state.stats.instances} />
        <Stat label={tl(LABELS.statFramesIn, lang)} value={state.stats.framesIn} />
        <Stat label={tl(LABELS.statFramesOut, lang)} value={state.stats.framesOut} />
        <Stat label={tl(LABELS.statBytes, lang)} value={state.stats.bytes} />
        <Stat
          label={tl(LABELS.statClashes, lang)}
          value={state.stats.clashes}
          color={state.stats.clashes > 0 ? 'var(--bad)' : undefined}
        />
        <Stat
          label={tl(LABELS.statLeaks, lang)}
          value={state.stats.leaks}
          color={state.stats.leaks > 0 ? 'var(--bad)' : undefined}
        />
        <Stat
          label={tl(LABELS.statLost, lang)}
          value={state.stats.lost}
          color={state.stats.lost > 0 ? 'var(--bad)' : undefined}
        />
        <Stat
          label={tl(LABELS.statDropped, lang)}
          value={state.stats.dropped}
          color={state.stats.dropped > 0 ? 'var(--accent)' : undefined}
        />
      </div>
    </div>
  );
}

function connectionBox(conn: Connection, lang: Lang) {
  const parts = [tl(LABELS.phases[conn.phase], lang)];
  if (conn.closeCode !== null) {
    parts.push(String(conn.closeCode));
  }
  parts.push(`↑${conn.sent} ↓${conn.received}`);
  if (conn.instance) {
    parts.push(conn.instance);
  }
  const session = conn.session.map((attr) => `${attr.key}=${attr.value}`).join(', ');
  if (session) {
    parts.push(`${tl(LABELS.session, lang)}: ${session}`);
  }
  return {
    id: conn.id,
    title: conn.id,
    subtitle: parts.join(' · '),
    highlighted: conn.phase === 'OPEN',
    dim: conn.phase === 'CLOSED',
  };
}

function InstanceCard({ instance, lang }: { instance: EndpointInstance; lang: Lang }) {
  const shared = instance.serves.length > 1;
  return (
    <div
      style={{
        ...instanceStyle,
        ...(shared ? instanceSharedStyle : {}),
        ...(instance.alive ? {} : instanceDeadStyle),
      }}
    >
      <div style={instanceNameStyle}>{instance.name}</div>
      <div style={instanceServesStyle}>
        {instance.serves.length === 0
          ? `${tl(LABELS.servesNobody, lang)}${instance.alive ? '' : ` · ${tl(LABELS.discarded, lang)}`}`
          : `${tl(LABELS.serves, lang)}: ${instance.serves.join(', ')}`}
      </div>
      {instance.fields.length === 0 ? (
        <div style={mutedStyle}>{tl(LABELS.noFields, lang)}</div>
      ) : (
        <div style={fieldsStyle}>
          {instance.fields.map((field) => (
            <div key={field.name} style={fieldRowStyle}>
              <span style={fieldNameStyle}>{`this.${field.name}`}</span>
              <span style={fieldValueStyle}>{`= "${field.value}"`}</span>
              <span style={{ ...fieldOwnerStyle, color: shared ? 'var(--bad)' : undefined }}>
                {`${tl(LABELS.writtenBy, lang)} ${field.owner}`}
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function HandshakeLines({ handshake, lang }: { handshake: Handshake; lang: Lang }) {
  if (handshake.request.length === 0) {
    return <div style={mutedStyle}>{tl(LABELS.noHandshake, lang)}</div>;
  }
  const verdict =
    handshake.accepted === null
      ? { text: tl(LABELS.pending, lang), color: 'var(--accent)' }
      : handshake.accepted
        ? { text: tl(LABELS.upgraded, lang), color: 'var(--good)' }
        : { text: tl(LABELS.refused, lang), color: 'var(--bad)' };
  return (
    <div>
      <div style={sectionLabelStyle}>
        {`${tl(LABELS.request, lang)}${handshake.client ? ` · ${handshake.client}` : ''}`}
      </div>
      <pre style={wireStyle}>{handshake.request.join('\n')}</pre>
      {handshake.response.length > 0 && (
        <>
          <div style={sectionLabelStyle}>{tl(LABELS.response, lang)}</div>
          <pre style={{ ...wireStyle, color: verdict.color }}>{handshake.response.join('\n')}</pre>
        </>
      )}
      <div style={{ ...verdictStyle, color: verdict.color }}>{verdict.text}</div>
    </div>
  );
}

function Header({ state, lang }: { state: WebSocketState; lang: Lang }) {
  return (
    <div style={headerStyle}>
      <span style={badgeStyle}>{state.url}</span>
      {state.model && <span style={headNameStyle}>{tl(LABELS.models[state.model], lang)}</span>}
      <span style={pillStyle}>
        {`${state.transport.protocol} :${state.transport.port}${state.transport.tls ? ' + TLS' : ''}`}
      </span>
      <span style={pillStyle}>
        {`${tl(LABELS.openSockets, lang)}: ${state.transport.sockets}`}
      </span>
      <span
        style={{ ...pillStyle, color: state.allowedOrigin ? 'var(--good)' : 'var(--bad)' }}
      >
        {state.allowedOrigin
          ? `${tl(LABELS.originCheck, lang)}: ${state.allowedOrigin}`
          : tl(LABELS.originAny, lang)}
      </span>
    </div>
  );
}

function ComparisonTable({ rows, lang }: { rows: ComparisonRow[]; lang: Lang }) {
  const yesNo = (value: boolean) => tl(value ? LABELS.yes : LABELS.no, lang);
  return (
    <div style={wrapStyle}>
      <div style={sectionLabelStyle}>{tl(LABELS.comparison, lang)}</div>
      <div style={tableStyle}>
        <div style={tableHeadStyle}>
          <span style={cellTextStyle}>{tl(LABELS.colModel, lang)}</span>
          <span style={cellTextStyle}>{tl(LABELS.colInstances, lang)}</span>
          <span style={cellTextStyle}>{tl(LABELS.colField, lang)}</span>
          <span style={cellTextStyle}>{tl(LABELS.colSync, lang)}</span>
          <span style={cellTextStyle}>{tl(LABELS.colInjection, lang)}</span>
          <span style={cellTextStyle}>{tl(LABELS.colState, lang)}</span>
        </div>
        {rows.map((row) => (
          <div key={row.model} style={tableRowStyle}>
            <span style={{ ...cellTextStyle, fontWeight: 700 }}>
              {tl(LABELS.modelNames[row.model], lang)}
            </span>
            <span style={cellTextStyle}>{row.instances}</span>
            <span
              style={{
                ...cellTextStyle,
                color: row.fieldPerConnection ? 'var(--good)' : 'var(--bad)',
              }}
            >
              {yesNo(row.fieldPerConnection)}
            </span>
            <span style={{ ...cellTextStyle, color: row.needsSync ? 'var(--accent)' : undefined }}>
              {yesNo(row.needsSync)}
            </span>
            <span style={cellTextStyle}>{yesNo(row.injection)}</span>
            <span style={{ ...cellTextStyle, fontWeight: 700 }}>
              {tl(LABELS.stateLives[row.stateLives], lang)}
            </span>
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
const headNameStyle: CSSProperties = { fontSize: 13, fontWeight: 600 };
const pillStyle: CSSProperties = {
  fontSize: 11,
  opacity: 0.75,
  border: '1px solid var(--border)',
  borderRadius: 10,
  padding: '1px 7px',
};
const sidesStyle: CSSProperties = { display: 'flex', gap: 10, alignItems: 'stretch', flexWrap: 'wrap' };
const paneStyle: CSSProperties = {
  flex: '1 1 260px',
  minWidth: 240,
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
const wireStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 11,
  lineHeight: '15px',
  margin: '2px 0 6px',
  whiteSpace: 'pre-wrap',
  overflowWrap: 'anywhere',
};
const verdictStyle: CSSProperties = { fontSize: 11, fontWeight: 700 };
const instancesStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 6 };
const instanceStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 6,
  padding: '5px 8px',
  background: 'var(--viz-box)',
};
const instanceSharedStyle: CSSProperties = { borderColor: 'var(--bad)' };
const instanceDeadStyle: CSSProperties = { opacity: 0.4, borderStyle: 'dashed' };
const instanceNameStyle: CSSProperties = { fontSize: 12, fontWeight: 700, fontFamily: 'monospace' };
const instanceServesStyle: CSSProperties = { fontSize: 11, opacity: 0.7, marginBottom: 2 };
const fieldsStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 2 };
const fieldRowStyle: CSSProperties = { display: 'flex', gap: 6, flexWrap: 'wrap', alignItems: 'baseline' };
const fieldNameStyle: CSSProperties = { fontSize: 11, fontFamily: 'monospace', opacity: 0.8 };
const fieldValueStyle: CSSProperties = { fontSize: 11, fontFamily: 'monospace', fontWeight: 700 };
const fieldOwnerStyle: CSSProperties = { fontSize: 10, opacity: 0.7 };
const frameStyle: CSSProperties = { display: 'flex', gap: 8, alignItems: 'baseline', flexWrap: 'wrap' };
const frameClientStyle: CSSProperties = { fontSize: 11, opacity: 0.6, fontFamily: 'monospace' };
const frameMetaStyle: CSSProperties = { fontSize: 10, opacity: 0.55, fontFamily: 'monospace' };
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
  gridTemplateColumns: '1.7fr repeat(5, 1fr)',
  fontSize: 10,
  opacity: 0.6,
};
const tableRowStyle: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: '1.7fr repeat(5, 1fr)',
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
