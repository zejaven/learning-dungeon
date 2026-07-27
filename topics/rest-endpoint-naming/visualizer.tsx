import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  surface: { en: 'routing table', ru: 'таблица маршрутов' },
  endpoints: { en: 'endpoints', ru: 'эндпоинтов' },
  paths: { en: 'URLs', ru: 'URL' },
  findings: { en: 'naming findings', ru: 'замечаний по именованию' },
  query: { en: 'query string', ru: 'строка запроса' },
  noRequest: { en: 'no request sent yet', ru: 'запросов ещё не было' },
  empty: { en: 'nothing is exposed yet', ru: 'ничего ещё не выставлено' },
  matched: { en: 'matched', ru: 'совпал с' },
  pending: { en: 'routing…', ru: 'маршрутизация…' },
  kindCollection: { en: 'collection', ru: 'коллекция' },
  kindItem: { en: 'one item', ru: 'один элемент' },
  kindNested: { en: 'part of an item', ru: 'часть элемента' },
  verbInPath: { en: 'verb in the path', ru: 'глагол в пути' },
  notPlural: { en: 'singular collection', ru: 'коллекция в единственном числе' },
  upperCase: { en: 'mixed case', ru: 'смешанный регистр' },
  trailingSlash: { en: 'trailing slash', ru: 'слэш в конце' },
  fileExtension: { en: 'file extension', ru: 'расширение файла' },
  filterInPath: { en: 'filter as a segment', ru: 'фильтр как сегмент' },
  deepNesting: { en: 'nested too deep', ru: 'слишком глубокая вложенность' },
  runHint: {
    en: 'Run the code to see the URLs an API exposes and the methods each one answers.',
    ru: 'Запустите код, чтобы увидеть URL, которые выставляет API, и методы, на которые отвечает каждый.',
  },
};

type Kind = 'collection' | 'item' | 'nested';
type Smell =
  | 'verb-in-path'
  | 'not-plural'
  | 'upper-case'
  | 'trailing-slash'
  | 'file-extension'
  | 'filter-in-path'
  | 'deep-nesting';

interface Method {
  name: string;
  current: boolean;
}
interface Route {
  path: string;
  kind: Kind;
  methods: Method[];
  smells: Smell[];
}
interface Request {
  method: string;
  path: string;
  query: { name: string; value: string }[];
  status: string;
  matchedPath: string | null;
}
interface SurfaceState {
  service: string;
  routes: Route[];
  request?: Request;
  endpoints: number;
  paths: number;
  smells: number;
}

export default function EndpointNamingVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as SurfaceState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const queryBoxes: Box[] = (state.request?.query ?? []).map((param) => ({
    id: `query-${param.name}`,
    title: `${param.name}=${param.value}`,
    highlighted: true,
  }));

  return (
    <div style={wrapStyle}>
      <div style={headerStyle}>
        <span style={pillStyle}>{state.service}</span>
        <span style={pillStyle}>
          {state.endpoints} {tl(LABELS.endpoints, lang)}
        </span>
        <span style={pillStyle}>
          {state.paths} {tl(LABELS.paths, lang)}
        </span>
        <span style={{ ...pillStyle, color: state.smells > 0 ? 'var(--bad)' : undefined }}>
          {state.smells} {tl(LABELS.findings, lang)}
        </span>
      </div>

      <RequestBanner request={state.request} lang={lang} />

      {queryBoxes.length > 0 && (
        <div>
          <div style={sectionLabelStyle}>{tl(LABELS.query, lang)}</div>
          <BoxGroup boxes={queryBoxes} />
        </div>
      )}

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.surface, lang)}</div>
        {state.routes.length === 0 ? (
          <div style={absentStyle}>{tl(LABELS.empty, lang)}</div>
        ) : (
          <div style={tableStyle}>
            {state.routes.map((route) => (
              <RouteRow
                key={route.path}
                route={route}
                active={highlight.has(`route:${route.path}`)}
                lang={lang}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function RouteRow({ route, active, lang }: { route: Route; active: boolean; lang: Lang }) {
  const methodBoxes: Box[] = route.methods.map((method) => ({
    id: `${route.path}#${method.name}`,
    title: method.name,
    highlighted: method.current,
  }));
  const flagged = route.smells.length > 0;
  return (
    <div
      style={{
        ...rowStyle,
        ...(active ? rowActiveStyle : {}),
        ...(flagged ? rowFlaggedStyle : {}),
      }}
    >
      <div style={pathCellStyle}>
        <div style={pathStyle}>{route.path}</div>
        <div style={kindStyle}>{tl(kindLabel(route.kind), lang)}</div>
      </div>
      <div style={methodCellStyle}>
        <BoxGroup boxes={methodBoxes} />
      </div>
      {flagged && (
        <div style={smellCellStyle}>
          {route.smells.map((smell) => (
            <span key={smell} style={smellStyle}>
              {tl(smellLabel(smell), lang)}
            </span>
          ))}
        </div>
      )}
    </div>
  );
}

function RequestBanner({ request, lang }: { request: Request | undefined; lang: Lang }) {
  if (!request) {
    return <div style={{ ...bannerStyle, opacity: 0.5 }}>{tl(LABELS.noRequest, lang)}</div>;
  }
  return (
    <div style={bannerStyle}>
      <span style={bannerMethodStyle}>{request.method}</span>
      <span style={bannerPathStyle}>{request.path}</span>
      {request.matchedPath && (
        <span style={bannerMatchStyle}>
          {tl(LABELS.matched, lang)} {request.matchedPath}
        </span>
      )}
      <span style={{ ...bannerStatusStyle, color: statusColor(request.status) }}>
        {request.status === 'pending' ? tl(LABELS.pending, lang) : request.status}
      </span>
    </div>
  );
}

function kindLabel(kind: Kind) {
  if (kind === 'item') return LABELS.kindItem;
  if (kind === 'nested') return LABELS.kindNested;
  return LABELS.kindCollection;
}

function smellLabel(smell: Smell) {
  switch (smell) {
    case 'verb-in-path':
      return LABELS.verbInPath;
    case 'not-plural':
      return LABELS.notPlural;
    case 'upper-case':
      return LABELS.upperCase;
    case 'trailing-slash':
      return LABELS.trailingSlash;
    case 'file-extension':
      return LABELS.fileExtension;
    case 'filter-in-path':
      return LABELS.filterInPath;
    default:
      return LABELS.deepNesting;
  }
}

function statusColor(status: string) {
  if (status.startsWith('2')) return 'var(--good)';
  if (status === 'pending') return 'var(--accent)';
  return 'var(--bad)';
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
const bannerMatchStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 11,
  padding: '1px 6px',
  borderRadius: 4,
  background: 'var(--viz-badge)',
};
const bannerStatusStyle: CSSProperties = { marginLeft: 'auto', fontSize: 13, fontWeight: 600 };
const tableStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 6 };
const rowStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: 12,
  flexWrap: 'wrap',
  padding: '6px 10px',
  borderRadius: 8,
  border: '1px solid var(--border)',
  background: 'var(--viz-box)',
};
const rowActiveStyle: CSSProperties = { borderColor: 'var(--accent)', background: 'var(--viz-highlight)' };
const rowFlaggedStyle: CSSProperties = { borderStyle: 'dashed' };
const pathCellStyle: CSSProperties = { minWidth: 200 };
const pathStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 13, fontWeight: 600 };
const kindStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const methodCellStyle: CSSProperties = { flex: 1 };
const smellCellStyle: CSSProperties = { display: 'flex', gap: 6, flexWrap: 'wrap' };
const smellStyle: CSSProperties = {
  fontSize: 11,
  padding: '1px 6px',
  borderRadius: 4,
  border: '1px solid var(--bad)',
  color: 'var(--bad)',
};
const sectionLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6, marginBottom: 6 };
const absentStyle: CSSProperties = { fontSize: 12, opacity: 0.4, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
