import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to watch the Environment walk its property sources from the top and stop at the first one that answers.',
    ru: 'Запустите код, чтобы увидеть, как Environment идёт по источникам свойств сверху вниз и останавливается на первом, который ответил.',
  },
  ladderTitle: { en: 'property sources, highest priority first', ru: 'источники свойств, сверху — самый приоритетный' },
  lookupTitle: { en: 'lookup', ru: 'поиск' },
  takenFrom: { en: 'taken from rank', ru: 'взято из ранга' },
  notFound: { en: 'not defined by any source', ru: 'не задано ни одним источником' },
  searching: { en: 'walking the ladder…', ru: 'идём по лестнице…' },
  matchedAs: { en: 'matched as', ru: 'совпало как' },
  relaxedTag: { en: 'relaxed binding', ru: 'relaxed binding' },
  shadowedTitle: { en: 'also defined lower down', ru: 'также задано ниже' },
  getenvTitle: { en: 'System.getenv', ru: 'System.getenv' },
  getenvExact: { en: 'exact name only — no relaxed binding', ru: 'только точное имя — без relaxed binding' },
  profilesTitle: { en: 'active profiles', ru: 'активные профили' },
  noProfiles: { en: 'none', ru: 'нет' },
  statLookups: { en: 'lookups', ru: 'поисков' },
  statOverrides: { en: 'overrode a lower value', ru: 'перебили значение ниже' },
  statMissing: { en: 'found nothing', ru: 'ничего не нашли' },
  // per-source hints, keyed by the technical source id
  hintTestProperties: { en: 'inlined in the test class', ru: 'вписаны в тестовый класс' },
  hintCommandLine: { en: 'after the jar name', ru: 'после имени jar' },
  hintApplicationJson: { en: 'one variable holding JSON', ru: 'одна переменная с JSON' },
  hintSystemProperties: { en: 'JVM flags before the jar name', ru: 'флаги JVM перед именем jar' },
  hintSystemEnvironment: { en: 'the OS environment', ru: 'окружение ОС' },
  hintProfileExternal: { en: 'profile file next to the jar', ru: 'файл профиля рядом с jar' },
  hintProfilePackaged: { en: 'profile file inside the jar', ru: 'файл профиля внутри jar' },
  hintExternal: { en: 'file next to the jar', ru: 'файл рядом с jar' },
  hintPackaged: { en: 'file inside the jar', ru: 'файл внутри jar' },
  hintAnnotation: { en: 'extra file from an annotation', ru: 'дополнительный файл из аннотации' },
  hintDefaults: { en: 'registered in code', ru: 'зарегистрированы в коде' },
  // per-source status badges
  statusHit: { en: 'WINS', ru: 'ПОБЕДА' },
  statusMiss: { en: 'no such key', ru: 'нет такого ключа' },
  statusShadowed: { en: 'shadowed', ru: 'затенено' },
  statusNotReached: { en: 'never reached', ru: 'не дошли' },
  statusInactive: { en: 'profile off', ru: 'профиль выключен' },
};

const HINTS: Record<string, { en: string; ru: string }> = {
  'test-properties': LABELS.hintTestProperties,
  'command-line': LABELS.hintCommandLine,
  'application-json': LABELS.hintApplicationJson,
  'system-properties': LABELS.hintSystemProperties,
  'system-environment': LABELS.hintSystemEnvironment,
  'profile-external': LABELS.hintProfileExternal,
  'profile-packaged': LABELS.hintProfilePackaged,
  external: LABELS.hintExternal,
  packaged: LABELS.hintPackaged,
  annotation: LABELS.hintAnnotation,
  defaults: LABELS.hintDefaults,
};

type SourceStatus =
  | 'idle'
  | 'empty'
  | 'pending'
  | 'miss'
  | 'hit'
  | 'shadowed'
  | 'not-reached'
  | 'inactive';

interface PropertyView {
  id: string;
  display: string;
  role: '' | 'winner' | 'shadowed';
}
interface SourceView {
  rank: number;
  id: string;
  name: string;
  profile: string;
  active: boolean;
  status: SourceStatus;
  properties: PropertyView[];
}
interface LookupView {
  key: string;
  done: boolean;
  found: boolean;
  value: string | null;
  winner: string;
  winnerRank: number;
  matchedName: string;
  relaxed: boolean;
  shadowed: string[];
}
interface GetenvView {
  name: string;
  value: string | null;
}
interface Stats {
  lookups: number;
  overrides: number;
  missing: number;
}
interface PrecedenceState {
  activeProfiles: string[];
  sources: SourceView[];
  lookup: LookupView | null;
  getenv: GetenvView | null;
  stats: Stats;
}

export default function PropertyPrecedenceVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as PrecedenceState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);

  return (
    <div style={wrapStyle}>
      <div style={rowStyle}>
        <LookupPanel lookup={state.lookup} lang={lang} highlighted={highlight.has('lookup')} />
        {state.getenv && (
          <div style={{ ...panelStyle, ...(highlight.has('getenv') ? panelHighlightStyle : {}) }}>
            <div style={panelLabelStyle}>{tl(LABELS.getenvTitle, lang)}</div>
            <div style={panelValueStyle}>
              {state.getenv.name} ={' '}
              <span style={{ color: state.getenv.value === null ? 'var(--bad)' : 'var(--good)' }}>
                {state.getenv.value === null ? 'null' : state.getenv.value}
              </span>
            </div>
            <div style={panelDetailStyle}>{tl(LABELS.getenvExact, lang)}</div>
          </div>
        )}
        <div style={{ ...panelStyle, ...(highlight.has('profiles') ? panelHighlightStyle : {}) }}>
          <div style={panelLabelStyle}>{tl(LABELS.profilesTitle, lang)}</div>
          <div style={panelValueStyle}>
            {state.activeProfiles.length === 0
              ? tl(LABELS.noProfiles, lang)
              : state.activeProfiles.join(', ')}
          </div>
        </div>
      </div>

      <div>
        <div style={sectionLabelStyle}>{tl(LABELS.ladderTitle, lang)}</div>
        <div style={stackStyle}>
          {state.sources.map((source) => (
            <SourceRow
              key={source.id}
              source={source}
              lang={lang}
              highlighted={highlight.has(`source:${source.id}`)}
            />
          ))}
        </div>
      </div>

      <div style={statsStyle}>
        <Stat label={tl(LABELS.statLookups, lang)} value={String(state.stats.lookups)} />
        <Stat
          label={tl(LABELS.statOverrides, lang)}
          value={String(state.stats.overrides)}
          color={state.stats.overrides > 0 ? 'var(--accent)' : undefined}
        />
        <Stat
          label={tl(LABELS.statMissing, lang)}
          value={String(state.stats.missing)}
          color={state.stats.missing > 0 ? 'var(--bad)' : undefined}
        />
      </div>
    </div>
  );
}

function LookupPanel({
  lookup,
  lang,
  highlighted,
}: {
  lookup: LookupView | null;
  lang: Lang;
  highlighted: boolean;
}) {
  if (!lookup) {
    return null;
  }
  const resolved = lookup.done && lookup.found;
  const failed = lookup.done && !lookup.found;
  return (
    <div style={{ ...panelStyle, ...(highlighted ? panelHighlightStyle : {}) }}>
      <div style={panelLabelStyle}>{tl(LABELS.lookupTitle, lang)}</div>
      <div style={panelValueStyle}>
        {lookup.key} ={' '}
        <span style={{ color: resolved ? 'var(--good)' : failed ? 'var(--bad)' : 'inherit' }}>
          {resolved ? lookup.value : failed ? 'null' : '…'}
        </span>
      </div>
      <div style={panelDetailStyle}>
        {resolved
          ? `${tl(LABELS.takenFrom, lang)} ${lookup.winnerRank} — ${lookup.winner}`
          : failed
            ? tl(LABELS.notFound, lang)
            : tl(LABELS.searching, lang)}
      </div>
      {resolved && lookup.relaxed && (
        <div style={panelDetailStyle}>
          {tl(LABELS.matchedAs, lang)} <code>{lookup.matchedName}</code>{' '}
          <span style={tagStyle}>{tl(LABELS.relaxedTag, lang)}</span>
        </div>
      )}
      {lookup.shadowed.length > 0 && (
        <div style={panelDetailStyle}>
          {tl(LABELS.shadowedTitle, lang)}: {lookup.shadowed.join(', ')}
        </div>
      )}
    </div>
  );
}

function SourceRow({
  source,
  lang,
  highlighted,
}: {
  source: SourceView;
  lang: Lang;
  highlighted: boolean;
}) {
  const badge = statusBadge(source.status, lang);
  const faded = source.properties.length === 0 || !source.active;
  return (
    <div
      style={{
        ...sourceStyle,
        ...(highlighted ? sourceHighlightStyle : {}),
        ...(faded ? sourceDimStyle : {}),
        borderLeftColor: accentFor(source.status),
      }}
    >
      <span style={rankStyle}>{source.rank}</span>
      <div style={sourceBodyStyle}>
        <div style={sourceNameStyle}>{source.name}</div>
        <div style={sourceHintStyle}>{hintFor(source.id, lang)}</div>
        {source.properties.length > 0 && (
          <div style={propsStyle}>
            <BoxGroup boxes={propertyBoxes(source.properties)} />
          </div>
        )}
      </div>
      {badge && <span style={{ ...badgeStyle, color: accentFor(source.status) }}>{badge}</span>}
    </div>
  );
}

function propertyBoxes(properties: PropertyView[]): Box[] {
  return properties.map((property) => ({
    id: property.id,
    title: property.display,
    highlighted: property.role === 'winner',
    dim: property.role === 'shadowed',
  }));
}

function hintFor(id: string, lang: Lang): string {
  const hint = HINTS[id];
  return hint ? tl(hint, lang) : '';
}

function statusBadge(status: SourceStatus, lang: Lang): string {
  switch (status) {
    case 'hit':
      return tl(LABELS.statusHit, lang);
    case 'miss':
      return tl(LABELS.statusMiss, lang);
    case 'shadowed':
      return tl(LABELS.statusShadowed, lang);
    case 'not-reached':
      return tl(LABELS.statusNotReached, lang);
    case 'inactive':
      return tl(LABELS.statusInactive, lang);
    default:
      return '';
  }
}

function accentFor(status: SourceStatus): string {
  switch (status) {
    case 'hit':
      return 'var(--good)';
    case 'shadowed':
      return 'var(--bad)';
    case 'miss':
    case 'not-reached':
    case 'inactive':
      return 'var(--border)';
    default:
      return 'var(--border)';
  }
}

function Stat({ label, value, color }: { label: string; value: string; color?: string }) {
  return (
    <div style={statStyle}>
      <div style={statLabelStyle}>{label}</div>
      <div style={{ ...statValueStyle, color: color ?? 'inherit' }}>{value}</div>
    </div>
  );
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 14 };
const rowStyle: CSSProperties = { display: 'flex', gap: 10, flexWrap: 'wrap' };
const stackStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 4 };
const sourceStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'flex-start',
  gap: 10,
  padding: '5px 10px',
  borderRadius: 6,
  border: '1px solid var(--border)',
  borderLeft: '3px solid var(--border)',
  background: 'var(--viz-box)',
};
const sourceHighlightStyle: CSSProperties = {
  background: 'var(--viz-highlight)',
  boxShadow: '0 0 0 2px rgba(255,204,102,0.35)',
};
const sourceDimStyle: CSSProperties = { opacity: 0.45 };
const sourceBodyStyle: CSSProperties = { flex: 1, minWidth: 0 };
const sourceNameStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 12,
  overflowWrap: 'anywhere',
};
const sourceHintStyle: CSSProperties = { fontSize: 10, opacity: 0.6 };
const propsStyle: CSSProperties = { marginTop: 4 };
const rankStyle: CSSProperties = {
  fontSize: 10,
  opacity: 0.5,
  fontFamily: 'monospace',
  width: 16,
  textAlign: 'right',
  paddingTop: 2,
};
const badgeStyle: CSSProperties = {
  fontSize: 10,
  fontWeight: 700,
  minWidth: 84,
  textAlign: 'right',
  paddingTop: 2,
};
const panelStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 8,
  padding: '6px 12px',
  background: 'var(--viz-box)',
  minWidth: 170,
};
const panelHighlightStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  background: 'var(--viz-highlight)',
};
const panelLabelStyle: CSSProperties = { fontSize: 10, opacity: 0.6 };
const panelValueStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 13,
  fontWeight: 600,
  overflowWrap: 'anywhere',
};
const panelDetailStyle: CSSProperties = { fontSize: 11, opacity: 0.8, overflowWrap: 'anywhere' };
const tagStyle: CSSProperties = {
  fontSize: 10,
  border: '1px solid var(--border)',
  borderRadius: 4,
  padding: '0 4px',
  opacity: 0.8,
};
const statsStyle: CSSProperties = { display: 'flex', gap: 18, flexWrap: 'wrap' };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 16, fontWeight: 700, fontFamily: 'monospace' };
const sectionLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6, marginBottom: 6 };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
