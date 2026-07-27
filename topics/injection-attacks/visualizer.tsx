import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to see where an untrusted value lands, what the parser makes of it, and which defence keeps it a value.',
    ru: 'Запустите код, чтобы увидеть, куда попадает недоверенное значение, чем его считает парсер и какая защита оставляет его значением.',
  },
  config: { en: 'application settings', ru: 'настройки приложения' },
  escaping: { en: 'quotes are doubled before concatenation', ru: 'кавычки удваиваются перед конкатенацией' },
  doctypeAllowed: { en: 'XML parser: DOCTYPE allowed', ru: 'XML-парсер: DOCTYPE разрешён' },
  doctypeBlocked: { en: 'XML parser: DOCTYPE disallowed', ru: 'XML-парсер: DOCTYPE запрещён' },
  noStatement: { en: 'nothing has been parsed yet', ru: 'пока ничего не разобрано' },
  source: { en: 'as written in the application', ru: 'как написано в приложении' },
  parsed: { en: 'what the parser received', ru: 'что получил парсер' },
  untrusted: { en: 'untrusted value', ru: 'недоверенное значение' },
  tokens: { en: 'what the parser made of it', ru: 'чем это счёл парсер' },
  result: { en: 'result', ru: 'результат' },
  noRows: { en: 'no rows', ru: 'строк нет' },
  statStatements: { en: 'statements', ru: 'запросов' },
  statInjections: { en: 'injections', ru: 'инъекций' },
  statLeaked: { en: 'leaked', ru: 'утечек' },
  statSafe: { en: 'stayed data', ru: 'осталось данными' },
  pending: { en: 'parsing…', ru: 'разбор…' },
  injectedVerdict: { en: 'the value became grammar', ru: 'значение стало грамматикой' },
  safeVerdict: { en: 'the value stayed a value', ru: 'значение осталось значением' },
  byBinding: { en: 'it never entered the statement text', ru: 'оно не попало в текст запроса' },
  byEscaping: { en: 'the doubled quotes held it inside one literal', ru: 'удвоенные кавычки удержали его в одном литерале' },
  byAllowlist: { en: 'the SQL was built from a name we wrote', ru: 'SQL собран из имени, написанного нами' },
  byParserConfig: { en: 'the parser refused to act on it', ru: 'парсер отказался по нему действовать' },
  undefended: { en: 'nothing defended it — this input just had no metacharacters', ru: 'ничто его не защищало — просто в этом вводе не было спецсимволов' },
  channels: {
    'in-band': { en: 'inside the statement', ru: 'внутри запроса' },
    'out-of-band': { en: 'beside the statement', ru: 'рядом с запросом' },
    rejected: { en: 'never reached the statement', ru: 'до запроса не дошло' },
  },
  bindings: {
    concatenation: { en: 'string concatenation', ru: 'конкатенация строк' },
    escaping: { en: 'concatenation + escaping', ru: 'конкатенация + экранирование' },
    'bind-parameter': { en: 'bind parameter', ru: 'привязка параметра' },
    'prepared-but-concatenated': { en: 'prepareStatement over concatenation', ru: 'prepareStatement поверх конкатенации' },
    allowlist: { en: 'allowlisted identifier', ru: 'идентификатор по белому списку' },
    'dynamic-sql': { en: 'dynamic SQL inside the database', ru: 'динамический SQL внутри базы' },
    'external-entities': { en: 'external entities enabled', ru: 'внешние сущности включены' },
    'secure-parser': { en: 'DTD support switched off', ru: 'поддержка DTD выключена' },
  },
  kinds: {
    keyword: { en: 'keyword', ru: 'ключевое слово' },
    identifier: { en: 'identifier', ru: 'идентификатор' },
    literal: { en: 'literal', ru: 'литерал' },
    operator: { en: 'operator', ru: 'оператор' },
    comment: { en: 'comment', ru: 'комментарий' },
    parameter: { en: 'placeholder', ru: 'плейсхолдер' },
    markup: { en: 'markup', ru: 'разметка' },
    dtd: { en: 'DTD', ru: 'DTD' },
    entity: { en: 'entity declaration', ru: 'объявление сущности' },
    reference: { en: 'entity reference', ru: 'ссылка на сущность' },
  },
  impacts: {
    none: { en: 'nothing extra returned', ru: 'ничего лишнего не вернулось' },
    'extra-rows': { en: 'every row returned', ru: 'вернулись все строки' },
    'auth-bypass': { en: 'a locked account got through', ru: 'заблокированный аккаунт прошёл' },
    'data-theft': { en: 'another table returned', ru: 'вернулась другая таблица' },
    'schema-change': { en: 'a second command ran', ru: 'выполнилась вторая команда' },
    'file-read': { en: 'a local file returned', ru: 'вернулся локальный файл' },
    ssrf: { en: 'an internal address was called', ru: 'обращение на внутренний адрес' },
    dos: { en: 'the service ran out of memory', ru: 'у сервиса кончилась память' },
  },
};

type TokenKind = keyof typeof LABELS.kinds;
type Binding = keyof typeof LABELS.bindings;
type Channel = keyof typeof LABELS.channels;
type Impact = keyof typeof LABELS.impacts;

interface Config {
  escapeQuotes: boolean;
  xmlDoctype: 'allowed' | 'disallowed';
}
interface Statement {
  language: 'SQL' | 'XML';
  binding: Binding;
  template: string;
  text: string | null;
  input: string;
  channel: Channel;
}
interface Token {
  text: string;
  kind: TokenKind;
  fromInput: boolean;
  danger: boolean;
}
interface Result {
  kind: 'rows' | 'text';
  columns: string[];
  rows: string[][];
  text: string | null;
}
interface Outcome {
  stage: 'idle' | 'built' | 'parsed' | 'settled';
  injected: boolean;
  impact: Impact;
  blockedBy: 'binding' | 'escaping' | 'allowlist' | 'parser-config' | null;
}
interface Stats {
  statements: number;
  injections: number;
  leaked: number;
  stayedData: number;
}
interface InjectionState {
  config: Config;
  statement?: Statement | null;
  tokens: Token[];
  result?: Result | null;
  outcome?: Outcome | null;
  stats: Stats;
}

export default function InjectionVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as InjectionState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const { statement, outcome, result } = state;

  return (
    <div style={wrapStyle}>
      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.config, lang)}</div>
        <BoxGroup boxes={configBoxes(state.config, lang, highlight.has('config'))} />
      </div>

      {statement && outcome ? (
        <>
          <div style={bannerStyle}>
            <span style={chipStyle}>{statement.language}</span>
            <span style={chipStyle}>{tl(LABELS.bindings[statement.binding], lang)}</span>
            <span style={chipStyle}>{tl(LABELS.channels[statement.channel], lang)}</span>
            <span style={{ ...verdictStyle, color: verdictOf(outcome).color }}>
              {tl(verdictOf(outcome).label, lang)}
            </span>
            <span style={reasonStyle}>{tl(reasonOf(outcome), lang)}</span>
          </div>

          <div>
            <div style={sectionLabelStyle}>{tl(LABELS.source, lang)}</div>
            <div style={codeStyle}>{statement.template}</div>
          </div>

          <div>
            <div style={sectionLabelStyle}>{tl(LABELS.untrusted, lang)}</div>
            <div style={{ ...codeStyle, ...(highlight.has('statement') ? codeActiveStyle : {}) }}>
              {statement.input || ' '}
            </div>
          </div>

          {statement.language === 'SQL' && statement.text && (
            <div>
              <div style={sectionLabelStyle}>{tl(LABELS.parsed, lang)}</div>
              <div style={{ ...codeStyle, ...(highlight.has('statement') ? codeActiveStyle : {}) }}>
                {statement.text}
              </div>
            </div>
          )}
        </>
      ) : (
        <div style={{ ...bannerStyle, opacity: 0.5 }}>{tl(LABELS.noStatement, lang)}</div>
      )}

      {state.tokens.length > 0 && (
        <div>
          <div style={sectionLabelStyle}>{tl(LABELS.tokens, lang)}</div>
          <BoxGroup boxes={tokenBoxes(state.tokens, lang)} />
        </div>
      )}

      {result && (
        <div>
          <div style={sectionLabelStyle}>{tl(LABELS.result, lang)}</div>
          {result.kind === 'rows' ? (
            <ResultTable result={result} highlighted={highlight.has('result')} lang={lang} />
          ) : (
            <div style={{ ...codeStyle, ...(highlight.has('result') ? codeActiveStyle : {}) }}>
              {result.text}
            </div>
          )}
        </div>
      )}

      <div style={statsStyle}>
        <Stat label={tl(LABELS.statStatements, lang)} value={state.stats.statements} />
        <Stat
          label={tl(LABELS.statInjections, lang)}
          value={state.stats.injections}
          color={state.stats.injections > 0 ? 'var(--accent)' : undefined}
        />
        <Stat
          label={tl(LABELS.statLeaked, lang)}
          value={state.stats.leaked}
          color={state.stats.leaked > 0 ? 'var(--bad)' : undefined}
        />
        <Stat
          label={tl(LABELS.statSafe, lang)}
          value={state.stats.stayedData}
          color={state.stats.stayedData > 0 ? 'var(--good)' : undefined}
        />
      </div>
    </div>
  );
}

function configBoxes(config: Config, lang: Lang, highlighted: boolean): Box[] {
  const boxes: Box[] = [
    {
      id: 'doctype',
      title: tl(config.xmlDoctype === 'allowed' ? LABELS.doctypeAllowed : LABELS.doctypeBlocked, lang),
      highlighted,
      dim: config.xmlDoctype === 'disallowed',
    },
  ];
  if (config.escapeQuotes) {
    boxes.unshift({ id: 'escaping', title: tl(LABELS.escaping, lang), highlighted });
  }
  return boxes;
}

function tokenBoxes(tokens: Token[], lang: Lang): Box[] {
  return tokens.map((token, index) => ({
    id: `token-${index}`,
    title: token.text || ' ',
    subtitle: tl(LABELS.kinds[token.kind], lang),
    highlighted: token.danger,
    dim: !token.fromInput,
  }));
}

function ResultTable({
  result,
  highlighted,
  lang,
}: {
  result: Result;
  highlighted: boolean;
  lang: Lang;
}) {
  if (result.rows.length === 0) {
    return <div style={mutedStyle}>{tl(LABELS.noRows, lang)}</div>;
  }
  return (
    <table style={{ ...tableStyle, ...(highlighted ? codeActiveStyle : {}) }}>
      <thead>
        <tr>
          {result.columns.map((column) => (
            <th key={column} style={thStyle}>
              {column}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {result.rows.map((row, index) => (
          <tr key={`row-${index}`}>
            {row.map((cell, cellIndex) => (
              <td key={`cell-${cellIndex}`} style={tdStyle}>
                {cell}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function verdictOf(outcome: Outcome) {
  if (outcome.stage !== 'settled') {
    return { label: LABELS.pending, color: 'var(--accent)' };
  }
  if (outcome.injected) {
    return { label: LABELS.injectedVerdict, color: 'var(--bad)' };
  }
  return { label: LABELS.safeVerdict, color: 'var(--good)' };
}

function reasonOf(outcome: Outcome) {
  if (outcome.stage !== 'settled') return LABELS.pending;
  if (outcome.injected) return LABELS.impacts[outcome.impact];
  if (outcome.blockedBy === 'binding') return LABELS.byBinding;
  if (outcome.blockedBy === 'escaping') return LABELS.byEscaping;
  if (outcome.blockedBy === 'allowlist') return LABELS.byAllowlist;
  if (outcome.blockedBy === 'parser-config') return LABELS.byParserConfig;
  return LABELS.undefended;
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
  whiteSpace: 'pre-wrap',
  wordBreak: 'break-word',
};
const codeActiveStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  background: 'var(--viz-highlight)',
};
const tableStyle: CSSProperties = {
  borderCollapse: 'collapse',
  fontFamily: 'monospace',
  fontSize: 12,
  border: '1px solid var(--border)',
  borderRadius: 6,
};
const thStyle: CSSProperties = {
  textAlign: 'left',
  padding: '4px 10px',
  borderBottom: '1px solid var(--border)',
  opacity: 0.7,
  fontWeight: 600,
};
const tdStyle: CSSProperties = { padding: '3px 10px' };
const statsStyle: CSSProperties = { display: 'flex', gap: 18, flexWrap: 'wrap' };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 18, fontWeight: 700, fontFamily: 'monospace' };
const sectionLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6, marginBottom: 6 };
const mutedStyle: CSSProperties = { fontSize: 12, opacity: 0.5, fontStyle: 'italic' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
