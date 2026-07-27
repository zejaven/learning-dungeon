import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to watch a property name turn into the environment variable name that overrides it.',
    ru: 'Запустите код, чтобы увидеть, как имя свойства превращается в имя переменной окружения, которая его переопределяет.',
  },
  ruleTitle: { en: 'the rule', ru: 'правило' },
  rule: {
    en: 'dots → _ · dashes deleted · UPPERCASE',
    ru: 'точки → _ · дефисы удалить · ВЕРХНИЙ РЕГИСТР',
  },
  conversionTitle: { en: 'name conversion', ru: 'преобразование имени' },
  stepStart: { en: 'as the code spells it', ru: 'как пишет код' },
  stepSeparators: { en: 'dots → underscores', ru: 'точки → подчёркивания' },
  stepDashes: { en: 'dashes deleted', ru: 'дефисы удалены' },
  stepUpper: { en: 'uppercase', ru: 'верхний регистр' },
  unchanged: { en: 'nothing to change', ru: 'менять нечего' },
  indexedTag: { en: 'list index', ru: 'индекс списка' },
  envTitle: { en: 'environment variables', ru: 'переменные окружения' },
  envHint: {
    en: 'stored by the OS verbatim — it never converts anything',
    ru: 'ОС хранит их буквально — она ничего не преобразует',
  },
  propsTitle: { en: 'what the application reads', ru: 'что читает приложение' },
  bindingTitle: { en: 'last lookup', ru: 'последний поиск' },
  matchedBy: { en: 'matched', ru: 'совпало' },
  lookingFor: { en: 'looking for', ru: 'ищем' },
  nearMissTitle: { en: 'close, but not read', ru: 'близко, но не читается' },
  getenvTitle: { en: 'System.getenv', ru: 'System.getenv' },
  getenvExact: { en: 'exact name only', ru: 'только точное имя' },
  formsTitle: { en: 'where you set it', ru: 'где это задают' },
  reasonCanonical: { en: 'canonical name', ru: 'каноническое имя' },
  reasonVerbatim: { en: 'literal dotted name — bash cannot export it', ru: 'буквальное имя с точками — bash его не экспортирует' },
  reasonLegacy: { en: 'dash spelled as _ — ambiguous', ru: 'дефис записан как _ — двусмысленно' },
  reasonLowercase: { en: 'lowercase — off convention', ru: 'нижний регистр — вне соглашения' },
  reasonNone: { en: 'no variable with this name', ru: 'переменной с таким именем нет' },
  sourceFile: { en: 'application.properties', ru: 'application.properties' },
  sourceEnv: { en: 'environment', ru: 'окружение' },
  sourceNone: { en: 'nowhere', ru: 'нигде' },
  sourceDropped: { en: 'dropped with the list', ru: 'исчезло вместе со списком' },
  statDerived: { en: 'names derived', ru: 'имён выведено' },
  statOverridden: { en: 'came from the environment', ru: 'пришло из окружения' },
  statMissed: { en: 'found no variable', ru: 'без переменной' },
};

const STEP_LABELS: Record<string, { en: string; ru: string }> = {
  start: LABELS.stepStart,
  separators: LABELS.stepSeparators,
  dashes: LABELS.stepDashes,
  upper: LABELS.stepUpper,
};

const REASON_LABELS: Record<string, { en: string; ru: string }> = {
  canonical: LABELS.reasonCanonical,
  verbatim: LABELS.reasonVerbatim,
  legacy: LABELS.reasonLegacy,
  lowercase: LABELS.reasonLowercase,
  none: LABELS.reasonNone,
};

const SOURCE_LABELS: Record<string, { en: string; ru: string }> = {
  file: LABELS.sourceFile,
  env: LABELS.sourceEnv,
  none: LABELS.sourceNone,
  dropped: LABELS.sourceDropped,
};

interface StepView {
  id: string;
  value: string;
  changed: boolean;
}
interface ConversionView {
  property: string;
  envName: string;
  done: boolean;
  indexed: boolean;
  steps: StepView[];
}
interface VariableView {
  id: string;
  name: string;
  value: string;
  role: '' | 'match' | 'near-miss';
}
interface PropertyView {
  id: string;
  key: string;
  envName: string;
  fileValue: string | null;
  value: string | null;
  source: 'none' | 'file' | 'env' | 'dropped';
}
interface BindingView {
  key: string;
  envName: string;
  matched: boolean;
  reason: string;
  variable: string;
  value: string | null;
  fileValue: string | null;
  nearMisses: string[];
}
interface GetenvView {
  name: string;
  value: string | null;
}
interface FormView {
  id: string;
  platform: string;
  snippet: string;
}
interface Stats {
  derived: number;
  overridden: number;
  missed: number;
}
interface EnvBindingState {
  conversion: ConversionView | null;
  variables: VariableView[];
  properties: PropertyView[];
  binding: BindingView | null;
  getenv: GetenvView | null;
  forms: FormView[];
  stats: Stats;
}

export default function EnvBindingVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as EnvBindingState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);

  return (
    <div style={wrapStyle}>
      <div style={ruleStyle}>
        <span style={panelLabelStyle}>{tl(LABELS.ruleTitle, lang)}</span>
        <span style={ruleTextStyle}>{tl(LABELS.rule, lang)}</span>
      </div>

      {state.conversion && (
        <Conversion
          conversion={state.conversion}
          lang={lang}
          highlighted={highlight.has('conversion')}
        />
      )}

      <div style={rowStyle}>
        {state.binding && <BindingPanel binding={state.binding} lang={lang} />}
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
      </div>

      {state.variables.length > 0 && (
        <div>
          <div style={sectionLabelStyle}>{tl(LABELS.envTitle, lang)}</div>
          <BoxGroup boxes={variableBoxes(state.variables)} />
          <div style={sourceHintStyle}>{tl(LABELS.envHint, lang)}</div>
        </div>
      )}

      {state.properties.length > 0 && (
        <div>
          <div style={sectionLabelStyle}>{tl(LABELS.propsTitle, lang)}</div>
          <div style={stackStyle}>
            {state.properties.map((property) => (
              <PropertyRow
                key={property.id}
                property={property}
                lang={lang}
                highlighted={highlight.has(`property:${property.id}`)}
              />
            ))}
          </div>
        </div>
      )}

      {state.forms.length > 0 && (
        <div>
          <div style={sectionLabelStyle}>{tl(LABELS.formsTitle, lang)}</div>
          <div style={stackStyle}>
            {state.forms.map((form) => (
              <div key={form.id} style={formStyle}>
                <div style={formPlatformStyle}>{form.platform}</div>
                <code style={formSnippetStyle}>{form.snippet}</code>
              </div>
            ))}
          </div>
        </div>
      )}

      <div style={statsStyle}>
        <Stat label={tl(LABELS.statDerived, lang)} value={String(state.stats.derived)} />
        <Stat
          label={tl(LABELS.statOverridden, lang)}
          value={String(state.stats.overridden)}
          color={state.stats.overridden > 0 ? 'var(--good)' : undefined}
        />
        <Stat
          label={tl(LABELS.statMissed, lang)}
          value={String(state.stats.missed)}
          color={state.stats.missed > 0 ? 'var(--bad)' : undefined}
        />
      </div>
    </div>
  );
}

function Conversion({
  conversion,
  lang,
  highlighted,
}: {
  conversion: ConversionView;
  lang: Lang;
  highlighted: boolean;
}) {
  return (
    <div style={{ ...panelStyle, ...(highlighted ? panelHighlightStyle : {}) }}>
      <div style={panelLabelStyle}>
        {tl(LABELS.conversionTitle, lang)}
        {conversion.indexed && <span style={tagStyle}> {tl(LABELS.indexedTag, lang)}</span>}
      </div>
      <div style={stepsStyle}>
        {conversion.steps.map((step, i) => (
          <div key={step.id} style={stepWrapStyle}>
            {i > 0 && <span style={arrowStyle}>→</span>}
            <div style={{ ...stepStyle, ...(step.changed ? stepChangedStyle : {}) }}>
              <code style={stepValueStyle}>{step.value}</code>
              <div style={stepLabelStyle}>
                {tl(STEP_LABELS[step.id] ?? LABELS.stepStart, lang)}
                {i > 0 && !step.changed ? ` · ${tl(LABELS.unchanged, lang)}` : ''}
              </div>
            </div>
          </div>
        ))}
      </div>
      {conversion.done && (
        <div style={resultStyle}>
          <code>{conversion.property}</code> <span style={arrowStyle}>→</span>{' '}
          <code style={resultNameStyle}>{conversion.envName}</code>
        </div>
      )}
    </div>
  );
}

function BindingPanel({ binding, lang }: { binding: BindingView; lang: Lang }) {
  return (
    <div style={{ ...panelStyle, ...(binding.matched ? panelHighlightStyle : {}) }}>
      <div style={panelLabelStyle}>{tl(LABELS.bindingTitle, lang)}</div>
      <div style={panelValueStyle}>
        {binding.key} ={' '}
        <span style={{ color: binding.matched ? 'var(--good)' : 'var(--bad)' }}>
          {binding.value ?? (binding.fileValue ?? 'null')}
        </span>
      </div>
      <div style={panelDetailStyle}>
        {binding.matched
          ? `${tl(LABELS.matchedBy, lang)}: ${binding.variable} — ${tl(
              REASON_LABELS[binding.reason] ?? LABELS.reasonCanonical,
              lang,
            )}`
          : `${tl(LABELS.lookingFor, lang)}: ${binding.envName} — ${tl(LABELS.reasonNone, lang)}`}
      </div>
      {binding.nearMisses.length > 0 && (
        <div style={panelDetailStyle}>
          {tl(LABELS.nearMissTitle, lang)}: {binding.nearMisses.join(', ')}
        </div>
      )}
    </div>
  );
}

function PropertyRow({
  property,
  lang,
  highlighted,
}: {
  property: PropertyView;
  lang: Lang;
  highlighted: boolean;
}) {
  return (
    <div
      style={{
        ...propertyStyle,
        ...(highlighted ? sourceHighlightStyle : {}),
        borderLeftColor: accentFor(property.source),
      }}
    >
      <code style={keyStyle}>{property.key}</code>
      <span style={arrowStyle}>→</span>
      <code style={envNameStyle}>{property.envName}</code>
      <span style={{ ...valueStyle, color: accentFor(property.source) }}>
        {property.value ?? 'null'}
      </span>
      <span style={badgeStyle}>{tl(SOURCE_LABELS[property.source] ?? LABELS.sourceNone, lang)}</span>
    </div>
  );
}

function variableBoxes(variables: VariableView[]): Box[] {
  return variables.map((variable) => ({
    id: variable.id,
    title: variable.name,
    subtitle: variable.value,
    highlighted: variable.role === 'match',
    dim: variable.role === 'near-miss',
  }));
}

function accentFor(source: string): string {
  switch (source) {
    case 'env':
      return 'var(--good)';
    case 'dropped':
    case 'none':
      return 'var(--bad)';
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
const ruleStyle: CSSProperties = { display: 'flex', alignItems: 'baseline', gap: 8 };
const ruleTextStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 12, opacity: 0.85 };
const stepsStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  flexWrap: 'wrap',
  gap: 4,
  marginTop: 4,
};
const stepWrapStyle: CSSProperties = { display: 'flex', alignItems: 'center', gap: 4 };
const stepStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 6,
  padding: '3px 8px',
  background: 'var(--viz-box)',
  opacity: 0.65,
};
const stepChangedStyle: CSSProperties = { opacity: 1, borderColor: 'var(--accent)' };
const stepValueStyle: CSSProperties = { fontSize: 12, overflowWrap: 'anywhere' };
const stepLabelStyle: CSSProperties = { fontSize: 10, opacity: 0.6 };
const resultStyle: CSSProperties = { marginTop: 8, fontSize: 14, overflowWrap: 'anywhere' };
const resultNameStyle: CSSProperties = { fontWeight: 700, color: 'var(--good)' };
const propertyStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: 8,
  flexWrap: 'wrap',
  padding: '4px 10px',
  borderRadius: 6,
  border: '1px solid var(--border)',
  borderLeft: '3px solid var(--border)',
  background: 'var(--viz-box)',
};
const sourceHighlightStyle: CSSProperties = {
  background: 'var(--viz-highlight)',
  boxShadow: '0 0 0 2px rgba(255,204,102,0.35)',
};
const keyStyle: CSSProperties = { fontSize: 12, opacity: 0.85 };
const envNameStyle: CSSProperties = { fontSize: 12, fontWeight: 600 };
const valueStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 12, marginLeft: 'auto' };
const badgeStyle: CSSProperties = { fontSize: 10, opacity: 0.6, minWidth: 110, textAlign: 'right' };
const formStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 6,
  padding: '4px 10px',
  background: 'var(--viz-box)',
};
const formPlatformStyle: CSSProperties = { fontSize: 10, opacity: 0.6 };
const formSnippetStyle: CSSProperties = {
  fontSize: 12,
  whiteSpace: 'pre-wrap',
  overflowWrap: 'anywhere',
};
const panelStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 8,
  padding: '6px 12px',
  background: 'var(--viz-box)',
  minWidth: 190,
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
const sourceHintStyle: CSSProperties = { fontSize: 10, opacity: 0.6, marginTop: 4 };
const tagStyle: CSSProperties = {
  fontSize: 10,
  border: '1px solid var(--border)',
  borderRadius: 4,
  padding: '0 4px',
  marginLeft: 4,
  opacity: 0.8,
};
const arrowStyle: CSSProperties = { opacity: 0.5, fontSize: 12 };
const statsStyle: CSSProperties = { display: 'flex', gap: 18, flexWrap: 'wrap' };
const statStyle: CSSProperties = { textAlign: 'center' };
const statLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6 };
const statValueStyle: CSSProperties = { fontSize: 16, fontWeight: 700, fontFamily: 'monospace' };
const sectionLabelStyle: CSSProperties = { fontSize: 11, opacity: 0.6, marginBottom: 6 };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
