import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { tl, useLang } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to visualize method dispatch.',
    ru: 'Запустите код, чтобы визуализировать диспетчеризацию методов.',
  },
  classes: { en: 'Classes', ru: 'Классы' },
  variables: { en: 'Variables', ru: 'Переменные' },
  staticType: { en: 'static', ru: 'статический' },
  runtimeType: { en: 'runtime', ru: 'runtime' },
  call: { en: 'Call resolution', ru: 'Разрешение вызова' },
  candidates: { en: 'Candidates', ru: 'Кандидаты' },
  chosen: { en: 'runs', ru: 'выполнится' },
  bindingCompile: { en: 'compile-time · static type', ru: 'компиляция · статический тип' },
  bindingRuntime: { en: 'runtime · actual type', ru: 'runtime · фактический тип' },
};

interface Cls {
  name: string;
  parent: string | null;
  methods: string[];
}
interface Var {
  name: string;
  staticType: string;
  runtimeType: string;
}
interface Candidate {
  owner: string;
  signature: string;
  applicable: boolean;
  chosen: boolean;
}
interface Call {
  site: string;
  kind: 'OVERRIDE' | 'OVERLOAD';
  binding: 'RUNTIME' | 'COMPILE';
  basis: string;
  basisType: string;
  result: string;
  candidates: Candidate[];
}
interface DispatchState {
  classes: Cls[];
  variables: Var[];
  call: Call | null;
}

export default function DispatchVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as DispatchState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const call = state.call;

  return (
    <div style={wrapStyle}>
      <section>
        <div style={sectionLabelStyle}>{tl(LABELS.classes, lang)}</div>
        <div style={rowStyle}>
          {state.classes.map((c) => (
            <div
              key={c.name}
              style={{
                ...cardStyle,
                ...(highlight.has(`class:${c.name}`) ? cardHlStyle : {}),
              }}
            >
              <div style={cardTitleStyle}>
                {c.name}
                {c.parent && <span style={extendsStyle}> ⤣ {c.parent}</span>}
              </div>
              {c.methods.map((m) => {
                const methodChosen = call?.result === `${c.name}.${m}`;
                return (
                  <div
                    key={m}
                    style={{
                      ...methodStyle,
                      ...(methodChosen ? methodChosenStyle : {}),
                    }}
                  >
                    {m}
                  </div>
                );
              })}
            </div>
          ))}
        </div>
      </section>

      {state.variables.length > 0 && (
        <section>
          <div style={sectionLabelStyle}>{tl(LABELS.variables, lang)}</div>
          <div style={rowStyle}>
            {state.variables.map((v) => (
              <div
                key={v.name}
                style={{
                  ...varStyle,
                  ...(highlight.has(`var:${v.name}`) ? cardHlStyle : {}),
                }}
              >
                <span style={varNameStyle}>{v.name}</span>
                <span style={typeChipStyle}>
                  {tl(LABELS.staticType, lang)}: {v.staticType}
                </span>
                <span style={{ ...typeChipStyle, ...runtimeChipStyle }}>
                  {tl(LABELS.runtimeType, lang)}: {v.runtimeType}
                </span>
              </div>
            ))}
          </div>
        </section>
      )}

      {call && (
        <section style={callStyle}>
          <div style={callHeadStyle}>
            <span style={sectionLabelStyle}>{tl(LABELS.call, lang)}</span>
            <code style={callSiteStyle}>{call.site}</code>
            <span
              style={{
                ...bindingStyle,
                ...(call.binding === 'RUNTIME' ? bindingRuntimeStyle : bindingCompileStyle),
              }}
            >
              {call.kind} ·{' '}
              {call.binding === 'RUNTIME'
                ? tl(LABELS.bindingRuntime, lang)
                : tl(LABELS.bindingCompile, lang)}
            </span>
          </div>
          <div style={basisStyle}>{call.basis}</div>
          <div style={sectionLabelStyle}>{tl(LABELS.candidates, lang)}</div>
          <div style={candListStyle}>
            {call.candidates.map((c, i) => (
              <div
                key={i}
                style={{
                  ...candStyle,
                  ...(c.chosen ? candChosenStyle : c.applicable ? candApplicableStyle : candDimStyle),
                }}
              >
                <code>
                  {c.owner}.{c.signature}
                </code>
                {c.chosen && <span style={chosenTagStyle}>✓ {tl(LABELS.chosen, lang)}</span>}
              </div>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 14 };
const sectionLabelStyle: CSSProperties = {
  fontSize: 11,
  textTransform: 'uppercase',
  letterSpacing: 0.5,
  opacity: 0.6,
  marginBottom: 6,
};
const rowStyle: CSSProperties = { display: 'flex', flexWrap: 'wrap', gap: 8 };
const cardStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 8,
  padding: '6px 10px',
  background: 'var(--viz-box)',
  minWidth: 120,
};
const cardHlStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  boxShadow: '0 0 0 2px rgba(255,204,102,0.30)',
};
const cardTitleStyle: CSSProperties = { fontWeight: 700, fontSize: 14, marginBottom: 4 };
const extendsStyle: CSSProperties = { fontWeight: 400, fontSize: 11, opacity: 0.6 };
const methodStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 12,
  padding: '2px 6px',
  borderRadius: 4,
  background: 'var(--viz-badge)',
  marginTop: 3,
};
const methodChosenStyle: CSSProperties = {
  background: 'var(--viz-highlight)',
  color: 'var(--text)',
  boxShadow: 'inset 2px 0 0 var(--accent)',
  fontWeight: 700,
};
const varStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: 8,
  border: '1px solid var(--border)',
  borderRadius: 8,
  padding: '6px 10px',
  background: 'var(--viz-box)',
};
const varNameStyle: CSSProperties = { fontFamily: 'monospace', fontWeight: 700 };
const typeChipStyle: CSSProperties = {
  fontSize: 11,
  fontFamily: 'monospace',
  padding: '1px 6px',
  borderRadius: 4,
  background: 'var(--viz-badge)',
};
const runtimeChipStyle: CSSProperties = { background: 'rgba(255,204,102,0.18)' };
const callStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 8,
  padding: 10,
  background: 'var(--viz-box)',
};
const callHeadStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: 10,
  flexWrap: 'wrap',
  marginBottom: 6,
};
const callSiteStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 14, fontWeight: 700 };
const bindingStyle: CSSProperties = {
  fontSize: 11,
  fontWeight: 700,
  padding: '2px 8px',
  borderRadius: 10,
};
const bindingRuntimeStyle: CSSProperties = { background: 'rgba(255,204,102,0.22)', color: 'var(--accent)' };
const bindingCompileStyle: CSSProperties = { background: 'rgba(120,170,255,0.20)', color: '#6aa0ff' };
const basisStyle: CSSProperties = { fontSize: 12, opacity: 0.75, marginBottom: 8, fontFamily: 'monospace' };
const candListStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 4 };
const candStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: 8,
  padding: '4px 8px',
  borderRadius: 6,
  fontSize: 13,
};
const candChosenStyle: CSSProperties = {
  background: 'var(--viz-highlight)',
  boxShadow: 'inset 2px 0 0 var(--accent)',
};
const candApplicableStyle: CSSProperties = { background: 'var(--viz-box)', opacity: 0.85 };
const candDimStyle: CSSProperties = { opacity: 0.4, textDecoration: 'line-through' };
const chosenTagStyle: CSSProperties = { fontSize: 11, fontWeight: 700, color: 'var(--accent)' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
