import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  handlers: { en: 'handlers', ru: 'обработчиков' },
  findings: { en: 'design findings', ru: 'замечаний по дизайну' },
  entity: { en: 'entity', ru: 'сущность' },
  table: { en: 'handler methods', ru: 'методы-обработчики' },
  query: { en: 'query', ru: 'параметры запроса' },
  empty: { en: 'nothing is declared yet', ru: 'ничего ещё не объявлено' },
  noRequest: { en: 'no request sent yet', ru: 'запросов ещё не было' },
  pending: { en: 'in the controller…', ru: 'внутри контроллера…' },
  validated: { en: 'validated', ru: 'проверяется' },
  logic: { en: 'logic here', ru: 'логика здесь' },
  bind: { en: 'bind', ru: 'привязка' },
  validate: { en: 'validate', ru: 'валидация' },
  service: { en: 'delegate', ru: 'делегирование' },
  respond: { en: 'respond', ru: 'ответ' },
  statusMismatch: { en: 'status does not fit', ru: 'статус не подходит' },
  bodyMismatch: { en: 'body does not fit', ru: 'тело не подходит' },
  entityExposed: { en: 'entity on the wire', ru: 'сущность наружу' },
  unbounded: { en: 'unbounded collection', ru: 'коллекция без границы' },
  logicInController: { en: 'logic in the controller', ru: 'логика в контроллере' },
  runHint: {
    en: 'Run the code to see one controller being designed handler by handler.',
    ru: 'Запустите код, чтобы увидеть, как контроллер проектируется обработчик за обработчиком.',
  },
};

type Issue =
  | 'status-mismatch'
  | 'body-mismatch'
  | 'entity-exposed'
  | 'unbounded-collection'
  | 'logic-in-controller';
type StageName = 'bind' | 'validate' | 'service' | 'respond';
type StageState = 'pending' | 'active' | 'done' | 'failed' | 'skipped';

interface Handler {
  key: string;
  method: string;
  path: string;
  in: string;
  out: string;
  status: number;
  statusLine: string;
  signature: string;
  query: string[];
  validated: boolean;
  logic: string | null;
  issues: Issue[];
  current: boolean;
}
interface Stage {
  name: StageName;
  state: StageState;
  detail: string;
}
interface Request {
  method: string;
  path: string;
  handler: string | null;
  status: string;
  stages: Stage[];
}
interface ControllerState {
  controller: string;
  basePath: string;
  entity: string;
  service: string;
  handlers: Handler[];
  request?: Request;
  handlerCount: number;
  issues: number;
}

export default function RestControllerVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as ControllerState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);

  return (
    <div style={wrapStyle}>
      <div style={headerStyle}>
        <span style={pillStyle}>{state.controller}</span>
        <span style={pillStyle}>{state.basePath}</span>
        <span style={pillStyle}>
          {tl(LABELS.entity, lang)}: {state.entity}
        </span>
        <span style={pillStyle}>
          {state.handlerCount} {tl(LABELS.handlers, lang)}
        </span>
        <span style={{ ...pillStyle, color: state.issues > 0 ? 'var(--bad)' : undefined }}>
          {state.issues} {tl(LABELS.findings, lang)}
        </span>
      </div>

      <RequestPanel request={state.request} highlight={highlight} lang={lang} />

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.table, lang)}</div>
        {state.handlers.length === 0 ? (
          <div style={absentStyle}>{tl(LABELS.empty, lang)}</div>
        ) : (
          <div style={tableStyle}>
            {state.handlers.map((handler) => (
              <HandlerRow
                key={handler.key}
                handler={handler}
                active={highlight.has(`handler:${handler.key}`)}
                lang={lang}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function HandlerRow({ handler, active, lang }: { handler: Handler; active: boolean; lang: Lang }) {
  const flagged = handler.issues.length > 0;
  const queryBoxes: Box[] = handler.query.map((param) => ({
    id: `${handler.key}#${param}`,
    title: param,
  }));
  return (
    <div
      style={{
        ...rowStyle,
        ...(active ? rowActiveStyle : {}),
        ...(flagged ? rowFlaggedStyle : {}),
      }}
    >
      <div style={topLineStyle}>
        <span style={methodStyle}>{handler.method}</span>
        <span style={pathStyle}>{handler.path}</span>
        <span style={typesStyle}>
          {handler.in} → {handler.out}
        </span>
        <span style={{ ...statusStyle, color: statusColor(handler.statusLine) }}>
          {handler.statusLine}
        </span>
      </div>
      <div style={signatureStyle}>{handler.signature}</div>
      <div style={tagRowStyle}>
        {handler.validated && <span style={okTagStyle}>@Valid · {tl(LABELS.validated, lang)}</span>}
        {handler.logic && (
          <span style={badTagStyle}>
            {tl(LABELS.logic, lang)}: {handler.logic}
          </span>
        )}
        {handler.issues.map((issue) => (
          <span key={issue} style={badTagStyle}>
            {tl(issueLabel(issue), lang)}
          </span>
        ))}
      </div>
      {queryBoxes.length > 0 && (
        <div style={queryRowStyle}>
          <span style={queryLabelStyle}>{tl(LABELS.query, lang)}</span>
          <BoxGroup boxes={queryBoxes} />
        </div>
      )}
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
  return (
    <div style={requestWrapStyle}>
      <div style={bannerStyle}>
        <span style={bannerMethodStyle}>{request.method}</span>
        <span style={bannerPathStyle}>{request.path}</span>
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
            <div style={stageNameStyle}>{tl(LABELS[stage.name], lang)}</div>
            {stage.detail && <div style={stageDetailStyle}>{stage.detail}</div>}
          </div>
        ))}
      </div>
    </div>
  );
}

function issueLabel(issue: Issue) {
  switch (issue) {
    case 'status-mismatch':
      return LABELS.statusMismatch;
    case 'body-mismatch':
      return LABELS.bodyMismatch;
    case 'entity-exposed':
      return LABELS.entityExposed;
    case 'unbounded-collection':
      return LABELS.unbounded;
    default:
      return LABELS.logicInController;
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
const requestWrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 8 };
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
const bannerStatusStyle: CSSProperties = { marginLeft: 'auto', fontSize: 13, fontWeight: 600 };
const stageRowStyle: CSSProperties = { display: 'flex', gap: 6, flexWrap: 'wrap' };
const stageStyle: CSSProperties = {
  flex: '1 1 140px',
  minWidth: 120,
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
const methodStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 13, fontWeight: 700, minWidth: 58 };
const pathStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 13, fontWeight: 600 };
const typesStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 11, opacity: 0.7 };
const statusStyle: CSSProperties = { marginLeft: 'auto', fontSize: 12, fontWeight: 600 };
const signatureStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 11, opacity: 0.85 };
const tagRowStyle: CSSProperties = { display: 'flex', gap: 6, flexWrap: 'wrap' };
const okTagStyle: CSSProperties = {
  fontSize: 11,
  padding: '1px 6px',
  borderRadius: 4,
  border: '1px solid var(--good)',
  color: 'var(--good)',
};
const badTagStyle: CSSProperties = {
  fontSize: 11,
  padding: '1px 6px',
  borderRadius: 4,
  border: '1px solid var(--bad)',
  color: 'var(--bad)',
};
const queryRowStyle: CSSProperties = { display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' };
const queryLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const sectionLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6, marginBottom: 6 };
const absentStyle: CSSProperties = { fontSize: 12, opacity: 0.4, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
