import type { CSSProperties, ReactNode } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to triage one production incident, step by step.',
    ru: 'Запустите код, чтобы шаг за шагом разобрать один инцидент в проде.',
  },
  reported: { en: 'reported', ru: 'сообщено' },
  severity: { en: 'severity', ru: 'критичность' },
  facts: { en: 'what was asked', ru: 'что спросили' },
  noAnswer: { en: 'no answer yet', ru: 'ответа пока нет' },
  open: { en: 'open', ru: 'открыто' },
  reproduction: { en: 'reproduction', ru: 'воспроизведение' },
  reproduced: { en: 'reproduced', ru: 'воспроизвелось' },
  notReproduced: { en: 'did not reproduce', ru: 'не воспроизвелось' },
  signals: { en: 'signals', ru: 'сигналы' },
  blastRadius: { en: 'blast radius', ru: 'радиус поражения' },
  ofTraffic: { en: 'of traffic', ru: 'трафика' },
  mitigation: { en: 'mitigation', ru: 'смягчение' },
  suspects: { en: 'suspects', ru: 'подозреваемые' },
  rootCause: { en: 'root cause', ru: 'корневая причина' },
  testGap: { en: 'why the tests passed', ru: 'почему тесты прошли' },
  fixApplied: { en: 'change shipped', ru: 'выкаченное изменение' },
  guards: { en: 'follow-ups', ru: 'последующие задачи' },
  missteps: { en: 'missteps', ru: 'промахи' },
  // phases
  clarify: { en: 'clarify', ru: 'уточнить' },
  reproducePhase: { en: 'reproduce', ru: 'воспроизвести' },
  measure: { en: 'measure', ru: 'измерить' },
  mitigate: { en: 'mitigate', ru: 'смягчить' },
  diagnose: { en: 'diagnose', ru: 'диагностировать' },
  fix: { en: 'fix', ru: 'исправить' },
  verify: { en: 'verify', ru: 'проверить' },
  prevent: { en: 'prevent', ru: 'предотвратить' },
  // suspect status
  statusOpen: { en: 'open', ru: 'открыт' },
  statusRuledOut: { en: 'ruled out', ru: 'исключён' },
  statusConfirmed: { en: 'confirmed', ru: 'подтверждён' },
  // incident stage
  stageReported: { en: 'reported', ru: 'сообщено' },
  stageConfirmed: { en: 'confirmed', ru: 'подтверждено' },
  stageNotReproduced: { en: 'not reproduced', ru: 'не воспроизведено' },
  stageMitigated: { en: 'mitigated', ru: 'смягчено' },
  stageDiagnosed: { en: 'diagnosed', ru: 'диагностировано' },
  stageFixed: { en: 'fixed', ru: 'исправлено' },
  stageReopened: { en: 'reopened', ru: 'открыто заново' },
  stageClosed: { en: 'closed', ru: 'закрыто' },
  // severity
  sevUnknown: { en: 'unknown', ru: 'неизвестна' },
  sevNone: { en: 'none', ru: 'нет' },
  sevLow: { en: 'low', ru: 'низкая' },
  sevHigh: { en: 'high', ru: 'высокая' },
  sevCritical: { en: 'critical', ru: 'критическая' },
  // missteps
  dismissedReport: { en: 'dismissed the report', ru: 'отмахнулся от сообщения' },
  flyingBlind: { en: 'no signal for this', ru: 'нет сигнала об этом' },
  diagnosedWhileDown: { en: 'diagnosed while down', ru: 'диагностика при лежащем сервисе' },
  blindFix: { en: 'changed prod on a guess', ru: 'изменил прод по догадке' },
  fixDidNotFixIt: { en: 'the fix did not fix it', ru: 'исправление не помогло' },
  nothingToVerify: { en: 'nothing to verify against', ru: 'не с чем сверять' },
};

type PhaseName =
  | 'clarify'
  | 'reproduce'
  | 'measure'
  | 'mitigate'
  | 'diagnose'
  | 'fix'
  | 'verify'
  | 'prevent';
type PhaseState = 'pending' | 'done' | 'skipped';
type Verdict = 'normal' | 'anomalous' | 'missing';
type SuspectStatus = 'open' | 'ruled-out' | 'confirmed';
type Misstep =
  | 'dismissed-report'
  | 'flying-blind'
  | 'diagnosed-while-down'
  | 'blind-fix'
  | 'fix-did-not-fix-it'
  | 'nothing-to-verify-against';

interface Phase {
  name: PhaseName;
  state: PhaseState;
  detail: string;
}
interface Fact {
  question: string;
  answer: string | null;
  known: boolean;
}
interface Reproduction {
  request: string;
  failed: boolean;
  observed: string;
}
interface Signal {
  name: string;
  value: string;
  verdict: Verdict;
}
interface BlastRadius {
  affected: string;
  share: number;
  clients: string;
}
interface Mitigation {
  action: string;
  effect: string;
}
interface Suspect {
  name: string;
  why: string;
  status: SuspectStatus;
  evidence: string;
}
interface TriageState {
  service: string;
  endpoint: string;
  report: string;
  stage: string;
  severity: string;
  minutes: number;
  phases: Phase[];
  facts: Fact[];
  reproduction?: Reproduction;
  signals: Signal[];
  blastRadius?: BlastRadius;
  mitigation?: Mitigation;
  suspects: Suspect[];
  rootCause: string | null;
  testGap: string | null;
  fixApplied: string | null;
  verified: boolean | null;
  guards: string[];
  missteps: Misstep[];
  openQuestions: number;
}

export default function IncidentTriageVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as TriageState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);

  return (
    <div style={wrapStyle}>
      <div style={headerStyle}>
        <span style={pillStyle}>{state.service}</span>
        <span style={pillStyle}>{state.endpoint}</span>
        <span style={pillStyle}>T+{state.minutes}m</span>
        <span style={pillStyle}>{tl(stageLabel(state.stage), lang)}</span>
        <span style={{ ...pillStyle, color: severityColor(state.severity) }}>
          {tl(LABELS.severity, lang)}: {tl(severityLabel(state.severity), lang)}
        </span>
      </div>

      <div style={reportStyle}>
        <span style={quoteStyle}>«{state.report}»</span>
        <span style={reportMetaStyle}>— {tl(LABELS.reported, lang)}</span>
      </div>

      <div style={trackStyle}>
        {state.phases.map((phase) => (
          <div
            key={phase.name}
            style={{
              ...phaseStyle,
              ...phaseStateStyle(phase.state),
              ...(highlight.has(`phase:${phase.name}`) ? phaseActiveStyle : {}),
            }}
          >
            <div style={phaseNameStyle}>{tl(phaseLabel(phase.name), lang)}</div>
            {phase.detail && <div style={phaseDetailStyle}>{phase.detail}</div>}
          </div>
        ))}
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

      {state.facts.length > 0 && (
        <Section
          title={`${tl(LABELS.facts, lang)}${
            state.openQuestions > 0 ? ` · ${state.openQuestions} ${tl(LABELS.open, lang)}` : ''
          }`}
        >
          <div style={listStyle}>
            {state.facts.map((fact, index) => (
              <div
                key={`${fact.question}-${index}`}
                style={{
                  ...rowStyle,
                  ...(highlight.has(`fact:${index + 1}`) ? rowActiveStyle : {}),
                  ...(fact.known ? {} : rowDimStyle),
                }}
              >
                <span style={questionStyle}>{fact.question}</span>
                <span style={fact.known ? answerStyle : unknownStyle}>
                  {fact.known ? fact.answer : tl(LABELS.noAnswer, lang)}
                </span>
              </div>
            ))}
          </div>
        </Section>
      )}

      {state.reproduction && (
        <Section title={tl(LABELS.reproduction, lang)}>
          <div
            style={{
              ...rowStyle,
              ...(highlight.has('repro') ? rowActiveStyle : {}),
              borderColor: state.reproduction.failed ? 'var(--bad)' : 'var(--good)',
            }}
          >
            <span style={monoStyle}>{state.reproduction.request}</span>
            <span
              style={{
                ...answerStyle,
                color: state.reproduction.failed ? 'var(--bad)' : 'var(--good)',
              }}
            >
              {state.reproduction.observed} ·{' '}
              {tl(state.reproduction.failed ? LABELS.reproduced : LABELS.notReproduced, lang)}
            </span>
          </div>
        </Section>
      )}

      {state.signals.length > 0 && (
        <Section title={tl(LABELS.signals, lang)}>
          <BoxGroup boxes={state.signals.map((signal) => signalBox(signal, highlight))} />
        </Section>
      )}

      {state.blastRadius && (
        <Section title={tl(LABELS.blastRadius, lang)}>
          <div style={{ ...rowStyle, ...(highlight.has('scope') ? rowActiveStyle : {}) }}>
            <span style={questionStyle}>{state.blastRadius.affected}</span>
            <span style={answerStyle}>{state.blastRadius.clients}</span>
          </div>
          <div style={barTrackStyle}>
            <div
              style={{
                ...barFillStyle,
                width: `${Math.min(100, Math.max(0, state.blastRadius.share))}%`,
                background: severityColor(state.severity),
              }}
            />
          </div>
          <div style={barCaptionStyle}>
            {state.blastRadius.share}% {tl(LABELS.ofTraffic, lang)}
          </div>
        </Section>
      )}

      {state.mitigation && (
        <Section title={tl(LABELS.mitigation, lang)}>
          <div style={{ ...rowStyle, borderColor: 'var(--good)' }}>
            <span style={questionStyle}>{state.mitigation.action}</span>
            <span style={{ ...answerStyle, color: 'var(--good)' }}>{state.mitigation.effect}</span>
          </div>
        </Section>
      )}

      {state.suspects.length > 0 && (
        <Section title={tl(LABELS.suspects, lang)}>
          <div style={listStyle}>
            {state.suspects.map((suspect) => (
              <SuspectRow
                key={suspect.name}
                suspect={suspect}
                active={highlight.has(`suspect:${suspect.name}`)}
                lang={lang}
              />
            ))}
          </div>
        </Section>
      )}

      {state.rootCause && (
        <Note
          label={tl(LABELS.rootCause, lang)}
          text={state.rootCause}
          tone="cause"
          active={highlight.has('rootcause')}
        />
      )}
      {state.testGap && (
        <Note
          label={tl(LABELS.testGap, lang)}
          text={state.testGap}
          tone="neutral"
          active={highlight.has('testgap')}
        />
      )}
      {state.fixApplied && (
        <Note
          label={tl(LABELS.fixApplied, lang)}
          text={state.fixApplied}
          tone={state.verified === null ? 'neutral' : state.verified ? 'good' : 'bad'}
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
                <span style={questionStyle}>{guard}</span>
              </div>
            ))}
          </div>
        </Section>
      )}
    </div>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div>
      <div style={sectionLabelStyle}>{title}</div>
      {children}
    </div>
  );
}

function Note({
  label,
  text,
  tone,
  active,
}: {
  label: string;
  text: string;
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
    </div>
  );
}

function SuspectRow({
  suspect,
  active,
  lang,
}: {
  suspect: Suspect;
  active: boolean;
  lang: Lang;
}) {
  const tone =
    suspect.status === 'confirmed'
      ? { borderColor: 'var(--accent)' }
      : suspect.status === 'ruled-out'
        ? { opacity: 0.5, borderStyle: 'dashed' as const }
        : {};
  return (
    <div style={{ ...rowStyle, ...tone, ...(active ? rowActiveStyle : {}) }}>
      <span style={questionStyle}>{suspect.name}</span>
      <span style={statusTagStyle(suspect.status)}>{tl(suspectLabel(suspect.status), lang)}</span>
      <span style={evidenceStyle}>{suspect.evidence || suspect.why}</span>
    </div>
  );
}

function signalBox(signal: Signal, highlight: Set<string>): Box {
  return {
    id: signal.name,
    title: signal.name,
    subtitle: signal.value,
    highlighted: signal.verdict === 'anomalous' || highlight.has(`signal:${signal.name}`),
    dim: signal.verdict === 'missing',
  };
}

function phaseLabel(name: PhaseName) {
  switch (name) {
    case 'reproduce':
      return LABELS.reproducePhase;
    case 'clarify':
      return LABELS.clarify;
    case 'measure':
      return LABELS.measure;
    case 'mitigate':
      return LABELS.mitigate;
    case 'diagnose':
      return LABELS.diagnose;
    case 'fix':
      return LABELS.fix;
    case 'verify':
      return LABELS.verify;
    default:
      return LABELS.prevent;
  }
}

function stageLabel(stage: string) {
  switch (stage) {
    case 'confirmed':
      return LABELS.stageConfirmed;
    case 'not-reproduced':
      return LABELS.stageNotReproduced;
    case 'mitigated':
      return LABELS.stageMitigated;
    case 'diagnosed':
      return LABELS.stageDiagnosed;
    case 'fixed':
      return LABELS.stageFixed;
    case 'reopened':
      return LABELS.stageReopened;
    case 'closed':
      return LABELS.stageClosed;
    default:
      return LABELS.stageReported;
  }
}

function severityLabel(severity: string) {
  switch (severity) {
    case 'none':
      return LABELS.sevNone;
    case 'low':
      return LABELS.sevLow;
    case 'high':
      return LABELS.sevHigh;
    case 'critical':
      return LABELS.sevCritical;
    default:
      return LABELS.sevUnknown;
  }
}

function suspectLabel(status: SuspectStatus) {
  switch (status) {
    case 'ruled-out':
      return LABELS.statusRuledOut;
    case 'confirmed':
      return LABELS.statusConfirmed;
    default:
      return LABELS.statusOpen;
  }
}

function misstepLabel(misstep: Misstep) {
  switch (misstep) {
    case 'dismissed-report':
      return LABELS.dismissedReport;
    case 'flying-blind':
      return LABELS.flyingBlind;
    case 'diagnosed-while-down':
      return LABELS.diagnosedWhileDown;
    case 'blind-fix':
      return LABELS.blindFix;
    case 'fix-did-not-fix-it':
      return LABELS.fixDidNotFixIt;
    default:
      return LABELS.nothingToVerify;
  }
}

function severityColor(severity: string) {
  switch (severity) {
    case 'critical':
    case 'high':
      return 'var(--bad)';
    case 'low':
      return 'var(--accent)';
    case 'none':
      return 'var(--good)';
    default:
      return undefined;
  }
}

function phaseStateStyle(state: PhaseState): CSSProperties {
  switch (state) {
    case 'done':
      return { borderColor: 'var(--good)' };
    case 'skipped':
      return { borderColor: 'var(--bad)', borderStyle: 'dashed', opacity: 0.6 };
    default:
      return { opacity: 0.45 };
  }
}

function statusTagStyle(status: SuspectStatus): CSSProperties {
  const base: CSSProperties = { fontSize: 11, padding: '1px 6px', borderRadius: 4, whiteSpace: 'nowrap' };
  if (status === 'confirmed') {
    return { ...base, border: '1px solid var(--accent)', color: 'var(--accent)' };
  }
  if (status === 'ruled-out') {
    return { ...base, border: '1px solid var(--border)', opacity: 0.7 };
  }
  return { ...base, border: '1px solid var(--bad)', color: 'var(--bad)' };
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
const reportStyle: CSSProperties = {
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
const reportMetaStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const trackStyle: CSSProperties = { display: 'flex', gap: 6, flexWrap: 'wrap' };
const phaseStyle: CSSProperties = {
  flex: '1 1 110px',
  minWidth: 96,
  border: '1px solid var(--border)',
  borderRadius: 6,
  padding: '5px 8px',
  background: 'var(--viz-box)',
};
const phaseActiveStyle: CSSProperties = { boxShadow: '0 0 0 2px rgba(255,204,102,0.35)' };
const phaseNameStyle: CSSProperties = { fontSize: 11, fontWeight: 600, textTransform: 'uppercase' };
const phaseDetailStyle: CSSProperties = { fontSize: 11, opacity: 0.7 };
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
const rowActiveStyle: CSSProperties = { borderColor: 'var(--accent)', background: 'var(--viz-highlight)' };
const rowDimStyle: CSSProperties = { borderStyle: 'dashed' };
const questionStyle: CSSProperties = { fontSize: 12, fontWeight: 600 };
const answerStyle: CSSProperties = { fontSize: 11, opacity: 0.8, marginLeft: 'auto', fontFamily: 'monospace' };
const unknownStyle: CSSProperties = { fontSize: 11, marginLeft: 'auto', color: 'var(--bad)', fontStyle: 'italic' };
const evidenceStyle: CSSProperties = { fontSize: 11, opacity: 0.7, flexBasis: '100%' };
const monoStyle: CSSProperties = { fontSize: 12, fontFamily: 'monospace' };
const barTrackStyle: CSSProperties = {
  height: 6,
  borderRadius: 3,
  background: 'var(--viz-box)',
  border: '1px solid var(--border)',
  marginTop: 6,
  overflow: 'hidden',
};
const barFillStyle: CSSProperties = { height: '100%' };
const barCaptionStyle: CSSProperties = { fontSize: 11, opacity: 0.6, marginTop: 4 };
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
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
