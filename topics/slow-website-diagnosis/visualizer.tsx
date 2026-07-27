import type { CSSProperties, ReactNode } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to hunt one slow page down to its cause, step by step.',
    ru: 'Запустите код, чтобы шаг за шагом довести одну медленную страницу до её причины.',
  },
  complained: { en: 'complaint', ru: 'жалоба' },
  budget: { en: 'budget', ru: 'бюджет' },
  noBudget: { en: 'no budget agreed', ru: 'бюджет не согласован' },
  endToEnd: { en: 'end-to-end', ru: 'сквозное время' },
  notMeasured: { en: 'not measured', ru: 'не измерено' },
  distribution: { en: 'the shape of the latency', ru: 'форма задержки' },
  avg: { en: 'avg', ru: 'среднее' },
  whereTheTimeGoes: { en: 'where the time goes', ru: 'куда уходит время' },
  questions: { en: 'what was asked', ru: 'что спросили' },
  loadTitle: { en: 'work or queue?', ru: 'работа или очередь?' },
  idle: { en: 'idle', ru: 'простой' },
  peak: { en: 'peak', ru: 'пик' },
  queueing: { en: 'mostly queueing', ru: 'в основном очередь' },
  realWork: { en: 'the work itself', ru: 'сама работа' },
  resources: { en: 'resources', ru: 'ресурсы' },
  ceilings: { en: 'what a fix could possibly save', ru: 'сколько исправление вообще может сэкономить' },
  ofTheRequest: { en: 'of the request', ru: 'от запроса' },
  worthIt: { en: 'worth building', ru: 'стоит делать' },
  invisible: { en: 'invisible', ru: 'незаметно' },
  missingSignals: { en: 'measurements that do not exist', ru: 'измерений не существует' },
  cause: { en: 'cause', ru: 'причина' },
  fixShipped: { en: 'change shipped', ru: 'выкаченное изменение' },
  blind: { en: 'no confirmed cause', ru: 'без подтверждённой причины' },
  expected: { en: 'expected', ru: 'ожидалось' },
  result: { en: 're-measured', ru: 'перемерено' },
  faster: { en: 'faster', ru: 'быстрее' },
  noise: { en: 'noise', ru: 'шум' },
  insideBudget: { en: 'inside the budget', ru: 'внутри бюджета' },
  overBudget: { en: 'still over budget', ru: 'всё ещё выше бюджета' },
  guards: { en: 'left behind', ru: 'оставлено после себя' },
  // stages
  stageReported: { en: 'reported', ru: 'сообщено' },
  stageScoped: { en: 'scoped', ru: 'сужено' },
  stageMeasuring: { en: 'measuring', ru: 'измеряем' },
  stageLocalized: { en: 'localized', ru: 'локализовано' },
  stageConfirmed: { en: 'cause confirmed', ru: 'причина подтверждена' },
  stageFixed: { en: 'fixed', ru: 'исправлено' },
  stageVerified: { en: 'verified', ru: 'проверено' },
  stageUnchanged: { en: 'nothing moved', ru: 'ничего не сдвинулось' },
  // segment status
  statusHotspot: { en: 'hotspot', ru: 'горячая точка' },
  statusCleared: { en: 'cleared', ru: 'исключено' },
  statusDrilled: { en: 'opened up', ru: 'раскрыто' },
  // missteps
  optimizedOnAHunch: { en: 'optimised on a hunch', ru: 'оптимизировал по наитию' },
  noMeasurement: { en: 'nothing measures this', ru: 'это ничем не измеряется' },
  chasedASmallSlice: { en: 'drilled into a small slice', ru: 'углубился в мелкий срез' },
  drilledBeforeMeasuring: { en: 'drilled into something unmeasured', ru: 'углубился в неизмеренное' },
  changedWithoutACause: { en: 'changed prod with no cause', ru: 'изменил прод без причины' },
  noMeasurableGain: { en: 'the graph did not move', ru: 'график не сдвинулся' },
  neverRemeasured: { en: 'never re-measured', ru: 'так и не перемерил' },
};

type SegmentStatus = 'measured' | 'hotspot' | 'cleared' | 'drilled';
type Misstep =
  | 'optimized-on-a-hunch'
  | 'no-measurement'
  | 'chased-a-small-slice'
  | 'drilled-before-measuring'
  | 'changed-without-a-cause'
  | 'no-measurable-gain'
  | 'never-remeasured';

interface Segment {
  name: string;
  millis: number;
  share: number;
  tool: string;
  status: SegmentStatus;
  note: string;
}
interface Level {
  name: string;
  totalMs: number;
  segments: Segment[];
}
interface Percentiles {
  avg: number;
  p50: number;
  p95: number;
  p99: number;
}
interface Fact {
  question: string;
  answer: string;
}
interface Load {
  idleMs: number;
  peakMs: number;
  queueing: boolean;
}
interface Resource {
  name: string;
  reading: string;
  saturated: boolean;
}
interface Ceiling {
  change: string;
  segment: string;
  speedup: number;
  savedMs: number;
  gainPercent: number;
  worthIt: boolean;
}
interface Fix {
  change: string;
  expectedMs: number;
  blind: boolean;
}
interface Result {
  beforeMs: number;
  afterMs: number;
  gainPercent: number;
  improved: boolean;
  metBudget: boolean;
}
interface HuntState {
  site: string;
  journey: string;
  complaint: string;
  stage: string;
  minutes: number;
  budgetMs: number;
  budgetLabel: string | null;
  endToEndMs: number;
  percentiles?: Percentiles;
  levels: Level[];
  hotspot: string | null;
  facts: Fact[];
  load?: Load;
  resources: Resource[];
  ceilings: Ceiling[];
  missingSignals: string[];
  cause: string | null;
  causeEvidence: string | null;
  fix?: Fix;
  result?: Result;
  guards: string[];
  missteps: Misstep[];
}

export default function LatencyHuntVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as HuntState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);

  return (
    <div style={wrapStyle}>
      <div style={headerStyle}>
        <span style={pillStyle}>{state.site}</span>
        <span style={pillStyle}>{state.journey}</span>
        <span style={pillStyle}>T+{state.minutes}m</span>
        <span style={pillStyle}>{tl(stageLabel(state.stage), lang)}</span>
        <span style={{ ...pillStyle, ...(highlight.has('budget') ? pillActiveStyle : {}) }}>
          {state.budgetMs > 0
            ? `${tl(LABELS.budget, lang)}: ${state.budgetMs}ms`
            : tl(LABELS.noBudget, lang)}
        </span>
        <span style={{ ...pillStyle, color: totalColor(state) }}>
          {tl(LABELS.endToEnd, lang)}:{' '}
          {state.endToEndMs > 0 ? `${state.endToEndMs}ms` : tl(LABELS.notMeasured, lang)}
        </span>
      </div>

      <div style={complaintStyle}>
        <span style={quoteStyle}>«{state.complaint}»</span>
        <span style={complaintMetaStyle}>— {tl(LABELS.complained, lang)}</span>
      </div>

      {state.missteps.length > 0 && (
        <div style={tagRowStyle}>
          {state.missteps.map((misstep) => (
            <span key={misstep} style={badTagStyle}>
              {tl(misstepLabel(misstep), lang)}
            </span>
          ))}
        </div>
      )}

      {state.percentiles && (
        <Section title={tl(LABELS.distribution, lang)} active={highlight.has('percentiles')}>
          <BoxGroup boxes={percentileBoxes(state.percentiles, lang)} />
        </Section>
      )}

      {state.levels.some((level) => level.segments.length > 0) && (
        <Section title={tl(LABELS.whereTheTimeGoes, lang)}>
          <div style={listStyle}>
            {state.levels.map((level, index) => (
              <LevelRow
                key={`${level.name}-${index}`}
                level={level}
                depth={index}
                active={highlight.has(`level:${index}`)}
                highlight={highlight}
                lang={lang}
              />
            ))}
          </div>
        </Section>
      )}

      {state.load && (
        <Section title={tl(LABELS.loadTitle, lang)} active={highlight.has('load')}>
          <div style={listStyle}>
            <CompareBar
              label={tl(LABELS.idle, lang)}
              millis={state.load.idleMs}
              max={Math.max(state.load.idleMs, state.load.peakMs)}
              color="var(--good)"
            />
            <CompareBar
              label={tl(LABELS.peak, lang)}
              millis={state.load.peakMs}
              max={Math.max(state.load.idleMs, state.load.peakMs)}
              color={state.load.queueing ? 'var(--bad)' : 'var(--accent)'}
            />
          </div>
          <div style={captionStyle}>
            {tl(state.load.queueing ? LABELS.queueing : LABELS.realWork, lang)}
          </div>
        </Section>
      )}

      {state.resources.length > 0 && (
        <Section title={tl(LABELS.resources, lang)}>
          <BoxGroup boxes={state.resources.map((resource) => resourceBox(resource, highlight))} />
        </Section>
      )}

      {state.ceilings.length > 0 && (
        <Section title={tl(LABELS.ceilings, lang)}>
          <div style={listStyle}>
            {state.ceilings.map((ceiling, index) => (
              <div
                key={`${ceiling.change}-${index}`}
                style={{
                  ...rowStyle,
                  ...(highlight.has(`ceiling:${index + 1}`) ? rowActiveStyle : {}),
                  ...(ceiling.worthIt ? {} : rowDimStyle),
                }}
              >
                <span style={nameStyle}>
                  {ceiling.change} · {ceiling.speedup}x
                </span>
                <span
                  style={{
                    ...valueStyle,
                    color: ceiling.worthIt ? 'var(--good)' : 'var(--bad)',
                  }}
                >
                  -{ceiling.savedMs}ms = {ceiling.gainPercent}% {tl(LABELS.ofTheRequest, lang)} ·{' '}
                  {tl(ceiling.worthIt ? LABELS.worthIt : LABELS.invisible, lang)}
                </span>
              </div>
            ))}
          </div>
        </Section>
      )}

      {state.facts.length > 0 && (
        <Section title={tl(LABELS.questions, lang)}>
          <div style={listStyle}>
            {state.facts.map((fact, index) => (
              <div
                key={`${fact.question}-${index}`}
                style={{
                  ...rowStyle,
                  ...(highlight.has(`fact:${index + 1}`) ? rowActiveStyle : {}),
                }}
              >
                <span style={nameStyle}>{fact.question}</span>
                <span style={valueStyle}>{fact.answer}</span>
              </div>
            ))}
          </div>
        </Section>
      )}

      {state.missingSignals.length > 0 && (
        <Section title={tl(LABELS.missingSignals, lang)}>
          <BoxGroup
            boxes={state.missingSignals.map((signal) => ({
              id: signal,
              title: signal,
              subtitle: '—',
              dim: true,
            }))}
          />
        </Section>
      )}

      {state.cause && (
        <Note
          label={tl(LABELS.cause, lang)}
          text={state.cause}
          detail={state.causeEvidence ?? undefined}
          tone="cause"
          active={highlight.has('cause')}
        />
      )}

      {state.fix && (
        <Note
          label={`${tl(LABELS.fixShipped, lang)}${state.fix.blind ? ` · ${tl(LABELS.blind, lang)}` : ''}`}
          text={state.fix.change}
          detail={
            state.fix.expectedMs > 0
              ? `${tl(LABELS.expected, lang)}: -${state.fix.expectedMs}ms`
              : undefined
          }
          tone={state.fix.blind ? 'bad' : 'neutral'}
          active={highlight.has('fix')}
        />
      )}

      {state.result && (
        <Note
          label={tl(LABELS.result, lang)}
          text={`${state.result.beforeMs}ms → ${state.result.afterMs}ms`}
          detail={
            state.result.improved
              ? `${state.result.gainPercent}% ${tl(LABELS.faster, lang)}${
                  state.budgetMs > 0
                    ? ` · ${tl(state.result.metBudget ? LABELS.insideBudget : LABELS.overBudget, lang)}`
                    : ''
                }`
              : `${state.result.gainPercent}% · ${tl(LABELS.noise, lang)}`
          }
          tone={state.result.improved ? 'good' : 'bad'}
          active={highlight.has('result')}
        />
      )}

      {state.guards.length > 0 && (
        <Section title={tl(LABELS.guards, lang)}>
          <div style={listStyle}>
            {state.guards.map((guard, index) => (
              <div
                key={guard}
                style={{
                  ...rowStyle,
                  ...(highlight.has(`guard:${index + 1}`) ? rowActiveStyle : {}),
                }}
              >
                <span style={nameStyle}>{guard}</span>
              </div>
            ))}
          </div>
        </Section>
      )}
    </div>
  );
}

function LevelRow({
  level,
  depth,
  active,
  highlight,
  lang,
}: {
  level: Level;
  depth: number;
  active: boolean;
  highlight: Set<string>;
  lang: Lang;
}) {
  if (level.segments.length === 0) {
    return null;
  }
  return (
    <div style={{ ...levelStyle, marginLeft: depth * 14, ...(active ? rowActiveStyle : {}) }}>
      <div style={levelHeadStyle}>
        <span style={nameStyle}>{level.name}</span>
        <span style={valueStyle}>{level.totalMs}ms</span>
      </div>
      <div style={barTrackStyle}>
        {level.segments
          .filter((segment) => segment.millis > 0)
          .map((segment) => (
            <div
              key={segment.name}
              style={{
                ...barPartStyle,
                flexGrow: segment.millis,
                background: segmentColor(segment.status),
                opacity: segment.status === 'cleared' ? 0.3 : 1,
                outline: highlight.has(`segment:${segment.name}`)
                  ? '2px solid var(--accent)'
                  : 'none',
              }}
              title={`${segment.name}: ${segment.millis}ms`}
            />
          ))}
      </div>
      <div style={listStyle}>
        {level.segments.map((segment) => (
          <div
            key={segment.name}
            style={{
              ...segmentRowStyle,
              ...(highlight.has(`segment:${segment.name}`) ? rowActiveStyle : {}),
              ...(segment.status === 'cleared' ? rowDimStyle : {}),
            }}
          >
            <span style={swatchStyle(segment.status)} />
            <span style={nameStyle}>{segment.name}</span>
            {segment.status !== 'measured' && (
              <span style={statusTagStyle(segment.status)}>
                {tl(segmentStatusLabel(segment.status), lang)}
              </span>
            )}
            <span style={valueStyle}>
              {segment.millis}ms · {segment.share}%
            </span>
            <span style={detailStyle}>{segment.note || segment.tool}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function CompareBar({
  label,
  millis,
  max,
  color,
}: {
  label: string;
  millis: number;
  max: number;
  color: string;
}) {
  return (
    <div style={compareRowStyle}>
      <span style={compareLabelStyle}>{label}</span>
      <div style={compareTrackStyle}>
        <div
          style={{
            ...compareFillStyle,
            width: `${max > 0 ? Math.max(2, (millis * 100) / max) : 0}%`,
            background: color,
          }}
        />
      </div>
      <span style={valueStyle}>{millis}ms</span>
    </div>
  );
}

function Section({
  title,
  active,
  children,
}: {
  title: string;
  active?: boolean;
  children: ReactNode;
}) {
  return (
    <div style={active ? sectionActiveStyle : undefined}>
      <div style={sectionLabelStyle}>{title}</div>
      {children}
    </div>
  );
}

function Note({
  label,
  text,
  detail,
  tone,
  active,
}: {
  label: string;
  text: string;
  detail?: string;
  tone: 'cause' | 'neutral' | 'good' | 'bad';
  active?: boolean;
}) {
  const color =
    tone === 'cause'
      ? 'var(--accent)'
      : tone === 'good'
        ? 'var(--good)'
        : tone === 'bad'
          ? 'var(--bad)'
          : 'var(--border)';
  return (
    <div style={{ ...noteStyle, borderColor: color, ...(active ? rowActiveStyle : {}) }}>
      <div style={sectionLabelStyle}>{label}</div>
      <div style={noteTextStyle}>{text}</div>
      {detail && <div style={detailStyle}>{detail}</div>}
    </div>
  );
}

function percentileBoxes(percentiles: Percentiles, lang: Lang): Box[] {
  return [
    { id: 'avg', title: tl(LABELS.avg, lang), subtitle: `${percentiles.avg}ms`, dim: true },
    { id: 'p50', title: 'p50', subtitle: `${percentiles.p50}ms` },
    { id: 'p95', title: 'p95', subtitle: `${percentiles.p95}ms`, highlighted: true },
    { id: 'p99', title: 'p99', subtitle: `${percentiles.p99}ms`, highlighted: true },
  ];
}

function resourceBox(resource: Resource, highlight: Set<string>): Box {
  return {
    id: resource.name,
    title: resource.name,
    subtitle: resource.reading,
    highlighted: resource.saturated || highlight.has(`resource:${resource.name}`),
  };
}

function segmentColor(status: SegmentStatus) {
  switch (status) {
    case 'hotspot':
      return 'var(--bad)';
    case 'drilled':
      return 'var(--accent)';
    case 'cleared':
      return 'var(--border)';
    default:
      return 'var(--viz-active)';
  }
}

function totalColor(state: HuntState) {
  if (state.budgetMs <= 0 || state.endToEndMs <= 0) {
    return undefined;
  }
  return state.endToEndMs <= state.budgetMs ? 'var(--good)' : 'var(--bad)';
}

function stageLabel(stage: string) {
  switch (stage) {
    case 'scoped':
      return LABELS.stageScoped;
    case 'measuring':
      return LABELS.stageMeasuring;
    case 'localized':
      return LABELS.stageLocalized;
    case 'confirmed':
      return LABELS.stageConfirmed;
    case 'fixed':
      return LABELS.stageFixed;
    case 'verified':
      return LABELS.stageVerified;
    case 'unchanged':
      return LABELS.stageUnchanged;
    default:
      return LABELS.stageReported;
  }
}

function segmentStatusLabel(status: SegmentStatus) {
  switch (status) {
    case 'hotspot':
      return LABELS.statusHotspot;
    case 'cleared':
      return LABELS.statusCleared;
    default:
      return LABELS.statusDrilled;
  }
}

function misstepLabel(misstep: Misstep) {
  switch (misstep) {
    case 'optimized-on-a-hunch':
      return LABELS.optimizedOnAHunch;
    case 'no-measurement':
      return LABELS.noMeasurement;
    case 'chased-a-small-slice':
      return LABELS.chasedASmallSlice;
    case 'drilled-before-measuring':
      return LABELS.drilledBeforeMeasuring;
    case 'changed-without-a-cause':
      return LABELS.changedWithoutACause;
    case 'no-measurable-gain':
      return LABELS.noMeasurableGain;
    default:
      return LABELS.neverRemeasured;
  }
}

function statusTagStyle(status: SegmentStatus): CSSProperties {
  const base: CSSProperties = {
    fontSize: 11,
    padding: '1px 6px',
    borderRadius: 4,
    whiteSpace: 'nowrap',
  };
  if (status === 'hotspot') {
    return { ...base, border: '1px solid var(--bad)', color: 'var(--bad)' };
  }
  if (status === 'drilled') {
    return { ...base, border: '1px solid var(--accent)', color: 'var(--accent)' };
  }
  return { ...base, border: '1px solid var(--border)', opacity: 0.7 };
}

function swatchStyle(status: SegmentStatus): CSSProperties {
  return {
    width: 8,
    height: 8,
    borderRadius: 2,
    background: segmentColor(status),
    flex: '0 0 auto',
  };
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
const pillActiveStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  background: 'var(--viz-highlight)',
};
const complaintStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'baseline',
  gap: 10,
  flexWrap: 'wrap',
  padding: '8px 12px',
  borderRadius: 8,
  background: 'var(--viz-box)',
  border: '1px solid var(--viz-active)',
};
const quoteStyle: CSSProperties = { fontSize: 14, fontWeight: 600 };
const complaintMetaStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const listStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 6 };
const levelStyle: CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: 6,
  padding: '8px 10px',
  borderRadius: 8,
  border: '1px solid var(--border)',
  background: 'var(--viz-box)',
};
const levelHeadStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'baseline',
  gap: 10,
  flexWrap: 'wrap',
};
const barTrackStyle: CSSProperties = {
  display: 'flex',
  height: 10,
  borderRadius: 3,
  overflow: 'hidden',
  border: '1px solid var(--border)',
  gap: 1,
};
const barPartStyle: CSSProperties = { flexBasis: 0, minWidth: 2 };
const segmentRowStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'baseline',
  gap: 8,
  flexWrap: 'wrap',
  padding: '3px 6px',
  borderRadius: 6,
  border: '1px solid transparent',
};
const rowStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'baseline',
  gap: 10,
  flexWrap: 'wrap',
  padding: '6px 10px',
  borderRadius: 8,
  border: '1px solid var(--border)',
  background: 'var(--viz-box)',
};
const rowActiveStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  background: 'var(--viz-highlight)',
};
const rowDimStyle: CSSProperties = { opacity: 0.55, borderStyle: 'dashed' };
const nameStyle: CSSProperties = { fontSize: 12, fontWeight: 600 };
const valueStyle: CSSProperties = {
  fontSize: 11,
  opacity: 0.8,
  marginLeft: 'auto',
  fontFamily: 'monospace',
};
const detailStyle: CSSProperties = { fontSize: 11, opacity: 0.6, flexBasis: '100%' };
const compareRowStyle: CSSProperties = { display: 'flex', alignItems: 'center', gap: 8 };
const compareLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.7, minWidth: 56 };
const compareTrackStyle: CSSProperties = {
  flex: 1,
  height: 8,
  borderRadius: 4,
  border: '1px solid var(--border)',
  overflow: 'hidden',
};
const compareFillStyle: CSSProperties = { height: '100%' };
const captionStyle: CSSProperties = { fontSize: 11, opacity: 0.6, marginTop: 4 };
const noteStyle: CSSProperties = {
  padding: '8px 12px',
  borderRadius: 8,
  border: '1px solid var(--border)',
  background: 'var(--viz-box)',
};
const noteTextStyle: CSSProperties = { fontSize: 12 };
const tagRowStyle: CSSProperties = { display: 'flex', gap: 6, flexWrap: 'wrap' };
const badTagStyle: CSSProperties = {
  fontSize: 11,
  padding: '1px 6px',
  borderRadius: 4,
  border: '1px solid var(--bad)',
  color: 'var(--bad)',
};
const sectionLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6, marginBottom: 6 };
const sectionActiveStyle: CSSProperties = {
  padding: '6px 8px',
  borderRadius: 8,
  border: '1px solid var(--accent)',
};
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
