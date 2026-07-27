import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  namespace: { en: 'namespace', ru: 'пространство имён' },
  codes: { en: 'codes', ru: 'кодов' },
  findings: { en: 'contract findings', ru: 'замечаний по контракту' },
  unmapped: { en: 'unmapped', ru: 'без отображения' },
  catalog: { en: 'error catalog', ru: 'каталог ошибок' },
  empty: { en: 'no error code is declared yet', ru: 'ни один код ошибки ещё не объявлен' },
  noRequest: { en: 'no request sent yet', ru: 'запросов ещё не было' },
  pending: { en: 'in the API…', ru: 'внутри API…' },
  body: { en: 'response body', ru: 'тело ответа' },
  log: { en: 'log line', ru: 'строка лога' },
  leakedFields: { en: 'internals in the body', ru: 'внутренности в теле' },
  retryable: { en: 'retryable', ru: 'повторяемый' },
  notRetryable: { en: 'not retryable', ru: 'неповторяемый' },
  call: { en: 'call', ru: 'вызов' },
  throw: { en: 'throw', ru: 'исключение' },
  map: { en: 'map', ru: 'отображение' },
  respond: { en: 'respond', ru: 'ответ' },
  logStage: { en: 'log', ru: 'лог' },
  proseCode: { en: 'code is a sentence', ru: 'код — это фраза' },
  unnamespacedCode: { en: 'no namespace', ru: 'нет пространства имён' },
  successStatus: { en: 'error with a 2xx', ru: 'ошибка с 2xx' },
  retryMismatch: { en: 'retry advice does not fit', ru: 'совет о повторе не подходит' },
  runHint: {
    en: "Run the code to see one API's error contract being designed, then used.",
    ru: 'Запустите код, чтобы увидеть, как проектируется, а затем работает контракт ошибок одного API.',
  },
};

type Issue = 'prose-code' | 'unnamespaced-code' | 'success-status' | 'retry-mismatch';
type StageName = 'call' | 'throw' | 'map' | 'respond' | 'log';
type StageState = 'pending' | 'active' | 'done' | 'failed' | 'skipped';

interface ErrorCode {
  code: string;
  exception: string;
  status: number;
  statusLine: string;
  retryable: boolean;
  issues: Issue[];
  current: boolean;
}
interface Stage {
  name: StageName;
  state: StageState;
  detail: string;
}
interface BodyField {
  name: string;
  value: string;
  leaked: boolean;
}
interface LogLine {
  level: 'ERROR' | 'DEBUG';
  line: string;
}
interface Request {
  method: string;
  path: string;
  status: string;
  code: string | null;
  traceId: string | null;
  stages: Stage[];
  body: BodyField[];
  log?: LogLine;
}
interface ContractState {
  api: string;
  namespace: string;
  codes: ErrorCode[];
  request?: Request;
  codeCount: number;
  issues: number;
  unmapped: string[];
}

export default function ErrorContractVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as ContractState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);

  return (
    <div style={wrapStyle}>
      <div style={headerStyle}>
        <span style={pillStyle}>{state.api}</span>
        <span style={pillStyle}>
          {tl(LABELS.namespace, lang)}: {state.namespace}
        </span>
        <span style={pillStyle}>
          {state.codeCount} {tl(LABELS.codes, lang)}
        </span>
        <span style={{ ...pillStyle, color: state.issues > 0 ? 'var(--bad)' : undefined }}>
          {state.issues} {tl(LABELS.findings, lang)}
        </span>
        {state.unmapped.length > 0 && (
          <span style={{ ...pillStyle, color: 'var(--bad)' }}>
            {tl(LABELS.unmapped, lang)}: {state.unmapped.join(', ')}
          </span>
        )}
      </div>

      <RequestPanel request={state.request} highlight={highlight} lang={lang} />

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.catalog, lang)}</div>
        {state.codes.length === 0 ? (
          <div style={absentStyle}>{tl(LABELS.empty, lang)}</div>
        ) : (
          <div style={tableStyle}>
            {state.codes.map((entry) => (
              <CodeRow
                key={entry.code}
                entry={entry}
                active={highlight.has(`code:${entry.code}`)}
                lang={lang}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function CodeRow({ entry, active, lang }: { entry: ErrorCode; active: boolean; lang: Lang }) {
  const flagged = entry.issues.length > 0;
  return (
    <div
      style={{
        ...rowStyle,
        ...(active || entry.current ? rowActiveStyle : {}),
        ...(flagged ? rowFlaggedStyle : {}),
      }}
    >
      <div style={topLineStyle}>
        <span style={codeStyle}>{entry.code}</span>
        <span style={exceptionStyle}>← {entry.exception}</span>
        <span style={{ ...statusStyle, color: statusColor(entry.statusLine) }}>{entry.statusLine}</span>
      </div>
      <div style={tagRowStyle}>
        <span style={entry.retryable ? okTagStyle : neutralTagStyle}>
          {tl(entry.retryable ? LABELS.retryable : LABELS.notRetryable, lang)}
        </span>
        {entry.issues.map((issue) => (
          <span key={issue} style={badTagStyle}>
            {tl(issueLabel(issue), lang)}
          </span>
        ))}
      </div>
    </div>
  );
}

function RequestPanel({
  request,
  highlight,
  lang,
}: {
  request: Request | undefined;
  highlight: Set<string>;
  lang: Lang;
}) {
  if (!request) {
    return <div style={{ ...bannerStyle, opacity: 0.5 }}>{tl(LABELS.noRequest, lang)}</div>;
  }

  const fields: Box[] = request.body.map((field) => ({
    id: field.name,
    title: field.name,
    subtitle: field.value,
    highlighted: highlight.has(`field:${field.name}`),
    dim: field.leaked,
  }));
  const leaked = request.body.filter((field) => field.leaked).map((field) => field.name);

  return (
    <div style={requestWrapStyle}>
      <div style={bannerStyle}>
        <span style={bannerMethodStyle}>{request.method}</span>
        <span style={bannerPathStyle}>{request.path}</span>
        {request.code && <span style={bannerCodeStyle}>{request.code}</span>}
        <span style={{ ...bannerStatusStyle, color: statusColor(request.status) }}>
          {request.status === 'pending' ? tl(LABELS.pending, lang) : request.status}
        </span>
      </div>

      <div style={stageRowStyle}>
        {request.stages.map((stage) => (
          <div
            key={stage.name}
            style={{
              ...stageStyle,
              ...stageStateStyle(stage.state),
              ...(highlight.has(`stage:${stage.name}`) ? stageActiveStyle : {}),
            }}
          >
            <div style={stageNameStyle}>{tl(stageLabel(stage.name), lang)}</div>
            {stage.detail && <div style={stageDetailStyle}>{stage.detail}</div>}
          </div>
        ))}
      </div>

      {fields.length > 0 && (
        <div>
          <div style={sectionLabelStyle}>{tl(LABELS.body, lang)}</div>
          <BoxGroup boxes={fields} />
          {leaked.length > 0 && (
            <div style={leakStyle}>
              {tl(LABELS.leakedFields, lang)}: {leaked.join(', ')}
            </div>
          )}
        </div>
      )}

      {request.log && (
        <div>
          <div style={sectionLabelStyle}>{tl(LABELS.log, lang)}</div>
          <div style={logStyle}>
            <span style={{ ...levelStyle, color: request.log.level === 'ERROR' ? 'var(--bad)' : undefined }}>
              {request.log.level}
            </span>
            <span style={logLineStyle}>{request.log.line}</span>
          </div>
        </div>
      )}
    </div>
  );
}

function stageLabel(name: StageName) {
  return name === 'log' ? LABELS.logStage : LABELS[name];
}

function issueLabel(issue: Issue) {
  switch (issue) {
    case 'prose-code':
      return LABELS.proseCode;
    case 'unnamespaced-code':
      return LABELS.unnamespacedCode;
    case 'success-status':
      return LABELS.successStatus;
    default:
      return LABELS.retryMismatch;
  }
}

function statusColor(status: string) {
  if (status.startsWith('2')) return 'var(--good)';
  if (status === 'pending') return 'var(--accent)';
  return 'var(--bad)';
}

function stageStateStyle(state: StageState): CSSProperties {
  switch (state) {
    case 'done':
      return { borderColor: 'var(--good)' };
    case 'active':
      return { borderColor: 'var(--accent)', background: 'var(--viz-highlight)' };
    case 'failed':
      return { borderColor: 'var(--bad)', color: 'var(--bad)' };
    case 'skipped':
      return { opacity: 0.4, borderStyle: 'dashed' };
    default:
      return { opacity: 0.55 };
  }
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 14 };
const headerStyle: CSSProperties = { display: 'flex', gap: 8, flexWrap: 'wrap' };
const pillStyle: CSSProperties = {
  fontSize: 11,
  padding: '2px 8px',
  borderRadius: 999,
  border: '1px solid var(--border)',
  fontFamily: 'monospace',
};
const requestWrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 10 };
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
const bannerMethodStyle: CSSProperties = { fontWeight: 700, fontSize: 15, fontFamily: 'monospace' };
const bannerPathStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 13 };
const bannerCodeStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 12, opacity: 0.75 };
const bannerStatusStyle: CSSProperties = { marginLeft: 'auto', fontSize: 13, fontWeight: 600 };
const stageRowStyle: CSSProperties = { display: 'flex', gap: 6, flexWrap: 'wrap' };
const stageStyle: CSSProperties = {
  flex: '1 1 120px',
  minWidth: 104,
  border: '1px solid var(--border)',
  borderRadius: 6,
  padding: '5px 8px',
  background: 'var(--viz-box)',
};
const stageActiveStyle: CSSProperties = { boxShadow: '0 0 0 2px rgba(255,204,102,0.35)' };
const stageNameStyle: CSSProperties = { fontSize: 11, fontWeight: 600, textTransform: 'uppercase' };
const stageDetailStyle: CSSProperties = { fontSize: 11, opacity: 0.7, fontFamily: 'monospace' };
const tableStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 6 };
const rowStyle: CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: 4,
  padding: '6px 10px',
  borderRadius: 8,
  border: '1px solid var(--border)',
  background: 'var(--viz-box)',
};
const rowActiveStyle: CSSProperties = { borderColor: 'var(--accent)', background: 'var(--viz-highlight)' };
const rowFlaggedStyle: CSSProperties = { borderStyle: 'dashed' };
const topLineStyle: CSSProperties = { display: 'flex', alignItems: 'baseline', gap: 10, flexWrap: 'wrap' };
const codeStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 13, fontWeight: 700 };
const exceptionStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 11, opacity: 0.7 };
const statusStyle: CSSProperties = { marginLeft: 'auto', fontSize: 12, fontWeight: 600 };
const tagRowStyle: CSSProperties = { display: 'flex', gap: 6, flexWrap: 'wrap' };
const okTagStyle: CSSProperties = {
  fontSize: 11,
  padding: '1px 6px',
  borderRadius: 4,
  border: '1px solid var(--good)',
  color: 'var(--good)',
};
const neutralTagStyle: CSSProperties = {
  fontSize: 11,
  padding: '1px 6px',
  borderRadius: 4,
  border: '1px solid var(--border)',
  opacity: 0.7,
};
const badTagStyle: CSSProperties = {
  fontSize: 11,
  padding: '1px 6px',
  borderRadius: 4,
  border: '1px solid var(--bad)',
  color: 'var(--bad)',
};
const leakStyle: CSSProperties = { fontSize: 11, color: 'var(--bad)', marginTop: 6 };
const logStyle: CSSProperties = {
  display: 'flex',
  gap: 8,
  alignItems: 'baseline',
  flexWrap: 'wrap',
  padding: '5px 8px',
  borderRadius: 6,
  border: '1px solid var(--border)',
  background: 'var(--viz-box)',
};
const levelStyle: CSSProperties = { fontSize: 11, fontWeight: 700, fontFamily: 'monospace' };
const logLineStyle: CSSProperties = { fontSize: 11, fontFamily: 'monospace', opacity: 0.8 };
const sectionLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6, marginBottom: 6 };
const absentStyle: CSSProperties = { fontSize: 12, opacity: 0.4, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
