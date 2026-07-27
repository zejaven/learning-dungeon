import type { CSSProperties, ReactNode } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to triage one silent production service, step by step.',
    ru: 'Запустите код, чтобы шаг за шагом разобрать одну замолчавшую службу в проде.',
  },
  symptom: { en: 'what was reported', ru: 'что сообщили' },
  probes: { en: 'probed outside in', ru: 'пробы снаружи внутрь' },
  evidence: { en: 'evidence', ru: 'улики' },
  threads: { en: 'the thread dump, grouped by top frame', ru: 'дамп потоков, сгруппированный по верхнему кадру' },
  ofTheDump: { en: 'of the dump', ru: 'от дампа' },
  pools: { en: 'bounded things requests queue for', ru: 'ограниченные ресурсы, за которыми стоит очередь' },
  queued: { en: 'queued', ru: 'в очереди' },
  saturated: { en: 'saturated', ru: 'насыщен' },
  capacityTitle: { en: 'capacity = threads / service time', ru: 'способность = потоки / время обслуживания' },
  workers: { en: 'workers', ru: 'воркеров' },
  perRequest: { en: 'per request', ru: 'на запрос' },
  arriving: { en: 'arriving', ru: 'приходит' },
  canServe: { en: 'can serve', ru: 'может обслужить' },
  poolGoneIn: { en: 'every worker busy after', ru: 'все воркеры заняты через' },
  overloaded: { en: 'the queue grows without bound', ru: 'очередь растёт без предела' },
  keepsUp: { en: 'capacity covers the arrival rate', ru: 'способности хватает на входящий поток' },
  gcTitle: { en: 'garbage collector', ru: 'сборщик мусора' },
  gcPauses: { en: 'of wall clock in pauses', ru: 'времени в паузах' },
  gcHeap: { en: 'heap full after a full GC', ru: 'кучи занято после полной сборки' },
  gcThrashing: { en: 'thrashing: alive, busy, answering nobody', ru: 'трэшинг: жив, занят, никому не отвечает' },
  gcFine: { en: 'the heap breathes — not the cause', ru: 'куча дышит — причина не в этом' },
  resources: { en: 'below the application', ru: 'ниже приложения' },
  deadlock: { en: 'the JVM found a deadlock', ru: 'JVM нашла взаимоблокировку' },
  mitigation: { en: 'service restored', ru: 'сервис восстановлен' },
  cause: { en: 'root cause', ru: 'корневая причина' },
  fixShipped: { en: 'change shipped', ru: 'выкаченное изменение' },
  blind: { en: 'no confirmed cause', ru: 'без подтверждённой причины' },
  verifiedOk: { en: 'verified: the failing request passes', ru: 'проверено: падавший запрос проходит' },
  verifiedBad: { en: 'still failing', ru: 'всё ещё падает' },
  guards: { en: 'left behind', ru: 'оставлено после себя' },
  evidenceLost: { en: 'gone with the process', ru: 'исчезло вместе с процессом' },
  // stages
  stageAlarm: { en: 'alarm', ru: 'тревога' },
  stageProbing: { en: 'probing', ru: 'прощупываем' },
  stageClassified: { en: 'classified', ru: 'классифицировано' },
  stageEvidence: { en: 'evidence captured', ru: 'улики собраны' },
  stageDiagnosing: { en: 'diagnosing', ru: 'диагностируем' },
  stageDiagnosed: { en: 'cause confirmed', ru: 'причина подтверждена' },
  stageRestored: { en: 'service restored', ru: 'сервис восстановлен' },
  stageFixed: { en: 'fixed', ru: 'исправлено' },
  stageRecovered: { en: 'recovered', ru: 'восстановлено' },
  stageStillDown: { en: 'still down', ru: 'всё ещё лежит' },
  // failure modes
  modeUnknown: { en: 'failure mode unknown', ru: 'режим сбоя неизвестен' },
  modeNotListening: { en: 'nothing is listening', ru: 'никто не слушает порт' },
  modeSilent: { en: 'accepts, answers nothing', ru: 'принимает, не отвечает' },
  modeHealthyHanging: { en: 'health green, endpoint hangs', ru: 'health зелёный, эндпоинт висит' },
  modeSlow: { en: 'slow, not hung', ru: 'медленно, но не зависло' },
  modePartly: { en: 'some instances only', ru: 'только часть экземпляров' },
  modeAnswers: { en: 'answers for me', ru: 'мне отвечает' },
  modeUnclear: { en: 'probes do not agree', ru: 'пробы не сходятся' },
  // probe layers
  layerInstances: { en: 'instances', ru: 'экземпляры' },
  layerDns: { en: 'DNS', ru: 'DNS' },
  layerTcp: { en: 'TCP', ru: 'TCP' },
  layerHealth: { en: 'health', ru: 'health' },
  layerEndpoint: { en: 'endpoint', ru: 'эндпоинт' },
  layerInside: { en: 'from inside', ru: 'изнутри' },
  // probe outcomes
  outcomeOk: { en: 'ok', ru: 'ок' },
  outcomeRefused: { en: 'refused', ru: 'отказ' },
  outcomeTimeout: { en: 'timeout', ru: 'таймаут' },
  outcomeSlow: { en: 'slow', ru: 'медленно' },
  outcomeError: { en: 'error', ru: 'ошибка' },
  outcomePartial: { en: 'partial', ru: 'частично' },
  // missteps
  restartedBlind: { en: 'restarted before capturing', ru: 'перезапустил до сбора улик' },
  evidenceLostTag: { en: 'evidence destroyed', ru: 'улики уничтожены' },
  leftUsersDown: { en: 'diagnosed while users were down', ru: 'диагностировал, пока пользователи лежали' },
  fixedWithoutACause: { en: 'changed prod with no cause', ru: 'менял прод без причины' },
  fixDidNotWork: { en: 'the fix missed', ru: 'исправление промахнулось' },
};

type Stage =
  | 'alarm'
  | 'probing'
  | 'classified'
  | 'evidence'
  | 'diagnosing'
  | 'diagnosed'
  | 'restored'
  | 'fixed'
  | 'recovered'
  | 'still-down';
type Outcome = 'ok' | 'refused' | 'timeout' | 'slow' | 'error' | 'partial';
type Misstep =
  | 'restarted-blind'
  | 'evidence-lost'
  | 'left-users-down'
  | 'fixed-without-a-cause'
  | 'fix-did-not-work';

interface Probe {
  layer: string;
  command: string;
  outcome: Outcome;
  detail: string;
}
interface Artifact {
  artifact: string;
  how: string;
  lost: boolean;
}
interface StackGroup {
  group: string;
  count: number;
  state: string;
  frame: string;
  share: number;
}
interface Pool {
  name: string;
  inUse: number;
  max: number;
  queued: number;
  saturated: boolean;
}
interface Gc {
  pausePercent: number;
  heapAfterGcPercent: number;
  thrashing: boolean;
}
interface Reading {
  name: string;
  value: string;
  alarming: boolean;
}
interface Deadlock {
  threadA: string;
  threadB: string;
  monitors: string;
}
interface Capacity {
  threads: number;
  serviceMillis: number;
  arrivalPerSecond: number;
  capacityPerSecond: number;
  deficitPerSecond: number;
  exhaustMillis: number;
  overloaded: boolean;
}
interface Mitigation {
  action: string;
  effect: string;
}
interface Fix {
  change: string;
  blind: boolean;
}
interface HungState {
  service: string;
  symptom: string;
  stage: Stage;
  failureMode: string;
  minutes: number;
  processRestarted: boolean;
  probes: Probe[];
  evidence: Artifact[];
  threads: StackGroup[];
  threadTotal: number;
  pools: Pool[];
  gc?: Gc;
  resources: Reading[];
  deadlock?: Deadlock;
  capacity?: Capacity;
  mitigation?: Mitigation;
  rootCause: string | null;
  causeEvidence: string | null;
  fix?: Fix;
  verified: boolean | null;
  guards: string[];
  missteps: Misstep[];
}

export default function HungServiceVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as HungState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);

  return (
    <div style={wrapStyle}>
      <div style={headerStyle}>
        <span style={pillStyle}>{state.service}</span>
        <span style={pillStyle}>T+{state.minutes}m</span>
        <span style={pillStyle}>{tl(stageLabel(state.stage), lang)}</span>
        <span
          style={{
            ...pillStyle,
            ...(highlight.has(`mode:${state.failureMode}`) ? pillActiveStyle : {}),
            color: modeColor(state.failureMode),
          }}
        >
          {tl(modeLabel(state.failureMode), lang)}
        </span>
      </div>

      <div style={symptomStyle}>
        <span style={quoteStyle}>«{state.symptom}»</span>
        <span style={symptomMetaStyle}>— {tl(LABELS.symptom, lang)}</span>
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

      {state.probes.length > 0 && (
        <Section title={tl(LABELS.probes, lang)}>
          <div style={listStyle}>
            {state.probes.map((probe) => (
              <div
                key={probe.layer}
                style={{
                  ...rowStyle,
                  ...(highlight.has(`probe:${probe.layer}`) ? rowActiveStyle : {}),
                }}
              >
                <span style={layerStyle}>{tl(layerLabel(probe.layer), lang)}</span>
                <span style={monoStyle}>{probe.command}</span>
                <span style={outcomeTagStyle(probe.outcome)}>
                  {tl(outcomeLabel(probe.outcome), lang)}
                </span>
                <span style={detailStyle}>{probe.detail}</span>
              </div>
            ))}
          </div>
        </Section>
      )}

      {state.evidence.length > 0 && (
        <Section title={tl(LABELS.evidence, lang)}>
          <BoxGroup boxes={state.evidence.map((artifact, index) => evidenceBox(artifact, index, lang))} />
        </Section>
      )}

      {state.threads.length > 0 && (
        <Section title={`${tl(LABELS.threads, lang)} · ${state.threadTotal}`}>
          <div style={listStyle}>
            {state.threads.map((group) => (
              <div
                key={group.group}
                style={{
                  ...rowStyle,
                  ...(highlight.has(`threads:${group.group}`) ? rowActiveStyle : {}),
                }}
              >
                <span style={nameStyle}>{group.group}</span>
                <span style={threadStateStyle(group.state)}>{group.state}</span>
                <span style={valueStyle}>
                  {group.count} · {group.share}% {tl(LABELS.ofTheDump, lang)}
                </span>
                <div style={barTrackStyle}>
                  <div
                    style={{
                      ...barFillStyle,
                      width: `${Math.max(2, group.share)}%`,
                      background: group.share >= 50 ? 'var(--bad)' : 'var(--viz-active)',
                    }}
                  />
                </div>
                <span style={detailStyle}>{group.frame}</span>
              </div>
            ))}
          </div>
        </Section>
      )}

      {state.deadlock && (
        <Note
          label={tl(LABELS.deadlock, lang)}
          text={`${state.deadlock.threadA} ⇄ ${state.deadlock.threadB}`}
          detail={state.deadlock.monitors}
          tone="bad"
          active={highlight.has('deadlock')}
        />
      )}

      {state.pools.length > 0 && (
        <Section title={tl(LABELS.pools, lang)}>
          <div style={listStyle}>
            {state.pools.map((pool) => (
              <div
                key={pool.name}
                style={{
                  ...rowStyle,
                  ...(highlight.has(`pool:${pool.name}`) ? rowActiveStyle : {}),
                }}
              >
                <span style={nameStyle}>{pool.name}</span>
                {pool.saturated && <span style={badTagStyle}>{tl(LABELS.saturated, lang)}</span>}
                <span style={valueStyle}>
                  {pool.inUse}/{pool.max}
                  {pool.queued > 0 ? ` · ${pool.queued} ${tl(LABELS.queued, lang)}` : ''}
                </span>
                <div style={barTrackStyle}>
                  <div
                    style={{
                      ...barFillStyle,
                      width: `${pool.max > 0 ? Math.min(100, Math.max(2, (pool.inUse * 100) / pool.max)) : 0}%`,
                      background: pool.saturated ? 'var(--bad)' : 'var(--good)',
                    }}
                  />
                </div>
              </div>
            ))}
          </div>
        </Section>
      )}

      {state.capacity && (
        <Section title={tl(LABELS.capacityTitle, lang)} active={highlight.has('capacity')}>
          <BoxGroup boxes={capacityBoxes(state.capacity, lang)} />
          <div style={captionStyle}>
            {state.capacity.overloaded
              ? `${tl(LABELS.overloaded, lang)} · ${tl(LABELS.poolGoneIn, lang)} ${state.capacity.exhaustMillis}ms`
              : tl(LABELS.keepsUp, lang)}
          </div>
        </Section>
      )}

      {state.gc && (
        <Section title={tl(LABELS.gcTitle, lang)} active={highlight.has('gc')}>
          <BoxGroup
            boxes={[
              {
                id: 'pauses',
                title: `${state.gc.pausePercent}%`,
                subtitle: tl(LABELS.gcPauses, lang),
                highlighted: state.gc.thrashing,
              },
              {
                id: 'heap',
                title: `${state.gc.heapAfterGcPercent}%`,
                subtitle: tl(LABELS.gcHeap, lang),
                highlighted: state.gc.thrashing,
              },
            ]}
          />
          <div style={captionStyle}>
            {tl(state.gc.thrashing ? LABELS.gcThrashing : LABELS.gcFine, lang)}
          </div>
        </Section>
      )}

      {state.resources.length > 0 && (
        <Section title={tl(LABELS.resources, lang)}>
          <BoxGroup
            boxes={state.resources.map((reading) => ({
              id: reading.name,
              title: reading.name,
              subtitle: reading.value,
              highlighted: reading.alarming || highlight.has(`resource:${reading.name}`),
            }))}
          />
        </Section>
      )}

      {state.mitigation && (
        <Note
          label={tl(LABELS.mitigation, lang)}
          text={state.mitigation.action}
          detail={state.mitigation.effect}
          tone={state.processRestarted && state.missteps.includes('restarted-blind') ? 'bad' : 'good'}
          active={highlight.has('mitigation')}
        />
      )}

      {state.rootCause && (
        <Note
          label={tl(LABELS.cause, lang)}
          text={state.rootCause}
          detail={state.causeEvidence ?? undefined}
          tone="cause"
          active={highlight.has('rootcause')}
        />
      )}

      {state.fix && (
        <Note
          label={`${tl(LABELS.fixShipped, lang)}${state.fix.blind ? ` · ${tl(LABELS.blind, lang)}` : ''}`}
          text={state.fix.change}
          detail={
            state.verified === null
              ? undefined
              : tl(state.verified ? LABELS.verifiedOk : LABELS.verifiedBad, lang)
          }
          tone={state.fix.blind || state.verified === false ? 'bad' : state.verified ? 'good' : 'neutral'}
          active={highlight.has('fix') || highlight.has('verify')}
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

function evidenceBox(artifact: Artifact, index: number, lang: Lang): Box {
  return {
    id: `${artifact.artifact}-${index}`,
    title: artifact.artifact,
    subtitle: artifact.lost ? tl(LABELS.evidenceLost, lang) : artifact.how,
    dim: artifact.lost,
  };
}

function capacityBoxes(capacity: Capacity, lang: Lang): Box[] {
  return [
    {
      id: 'threads',
      title: `${capacity.threads}`,
      subtitle: tl(LABELS.workers, lang),
    },
    {
      id: 'service',
      title: `${capacity.serviceMillis}ms`,
      subtitle: tl(LABELS.perRequest, lang),
      highlighted: capacity.overloaded,
    },
    {
      id: 'capacity',
      title: `${capacity.capacityPerSecond}/s`,
      subtitle: tl(LABELS.canServe, lang),
    },
    {
      id: 'arrival',
      title: `${capacity.arrivalPerSecond}/s`,
      subtitle: tl(LABELS.arriving, lang),
      highlighted: capacity.overloaded,
    },
  ];
}

function stageLabel(stage: Stage) {
  switch (stage) {
    case 'probing':
      return LABELS.stageProbing;
    case 'classified':
      return LABELS.stageClassified;
    case 'evidence':
      return LABELS.stageEvidence;
    case 'diagnosing':
      return LABELS.stageDiagnosing;
    case 'diagnosed':
      return LABELS.stageDiagnosed;
    case 'restored':
      return LABELS.stageRestored;
    case 'fixed':
      return LABELS.stageFixed;
    case 'recovered':
      return LABELS.stageRecovered;
    case 'still-down':
      return LABELS.stageStillDown;
    default:
      return LABELS.stageAlarm;
  }
}

function modeLabel(mode: string) {
  switch (mode) {
    case 'not-listening':
      return LABELS.modeNotListening;
    case 'accepting-but-silent':
      return LABELS.modeSilent;
    case 'healthy-but-hanging':
      return LABELS.modeHealthyHanging;
    case 'slow-not-hung':
      return LABELS.modeSlow;
    case 'partly-down':
      return LABELS.modePartly;
    case 'answers-for-me':
      return LABELS.modeAnswers;
    case 'unclear':
      return LABELS.modeUnclear;
    default:
      return LABELS.modeUnknown;
  }
}

function modeColor(mode: string) {
  if (mode === 'unknown' || mode === 'unclear') {
    return undefined;
  }
  return 'var(--accent)';
}

function layerLabel(layer: string) {
  switch (layer) {
    case 'instances':
      return LABELS.layerInstances;
    case 'dns':
      return LABELS.layerDns;
    case 'tcp':
      return LABELS.layerTcp;
    case 'health':
      return LABELS.layerHealth;
    case 'endpoint':
      return LABELS.layerEndpoint;
    default:
      return LABELS.layerInside;
  }
}

function outcomeLabel(outcome: Outcome) {
  switch (outcome) {
    case 'refused':
      return LABELS.outcomeRefused;
    case 'timeout':
      return LABELS.outcomeTimeout;
    case 'slow':
      return LABELS.outcomeSlow;
    case 'error':
      return LABELS.outcomeError;
    case 'partial':
      return LABELS.outcomePartial;
    default:
      return LABELS.outcomeOk;
  }
}

function misstepLabel(misstep: Misstep) {
  switch (misstep) {
    case 'restarted-blind':
      return LABELS.restartedBlind;
    case 'evidence-lost':
      return LABELS.evidenceLostTag;
    case 'left-users-down':
      return LABELS.leftUsersDown;
    case 'fixed-without-a-cause':
      return LABELS.fixedWithoutACause;
    default:
      return LABELS.fixDidNotWork;
  }
}

function outcomeTagStyle(outcome: Outcome): CSSProperties {
  const base: CSSProperties = {
    fontSize: 11,
    padding: '1px 6px',
    borderRadius: 4,
    whiteSpace: 'nowrap',
    fontFamily: 'monospace',
  };
  if (outcome === 'ok') {
    return { ...base, border: '1px solid var(--good)', color: 'var(--good)' };
  }
  if (outcome === 'slow' || outcome === 'partial') {
    return { ...base, border: '1px solid var(--accent)', color: 'var(--accent)' };
  }
  return { ...base, border: '1px solid var(--bad)', color: 'var(--bad)' };
}

function threadStateStyle(state: string): CSSProperties {
  const base: CSSProperties = {
    fontSize: 11,
    padding: '1px 6px',
    borderRadius: 4,
    whiteSpace: 'nowrap',
    fontFamily: 'monospace',
  };
  if (state === 'BLOCKED') {
    return { ...base, border: '1px solid var(--bad)', color: 'var(--bad)' };
  }
  if (state === 'RUNNABLE') {
    return { ...base, border: '1px solid var(--accent)', color: 'var(--accent)' };
  }
  return { ...base, border: '1px solid var(--border)', opacity: 0.8 };
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
const symptomStyle: CSSProperties = {
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
const symptomMetaStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const listStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 6 };
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
const layerStyle: CSSProperties = {
  fontSize: 11,
  fontWeight: 600,
  minWidth: 74,
  textTransform: 'uppercase',
  opacity: 0.75,
};
const nameStyle: CSSProperties = { fontSize: 12, fontWeight: 600 };
const monoStyle: CSSProperties = { fontSize: 11, fontFamily: 'monospace', opacity: 0.75 };
const valueStyle: CSSProperties = {
  fontSize: 11,
  opacity: 0.8,
  marginLeft: 'auto',
  fontFamily: 'monospace',
};
const detailStyle: CSSProperties = { fontSize: 11, opacity: 0.6, flexBasis: '100%' };
const barTrackStyle: CSSProperties = {
  flexBasis: '100%',
  height: 8,
  borderRadius: 4,
  border: '1px solid var(--border)',
  overflow: 'hidden',
};
const barFillStyle: CSSProperties = { height: '100%' };
const captionStyle: CSSProperties = { fontSize: 11, opacity: 0.6, marginTop: 6 };
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
