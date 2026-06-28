import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { tl, useLang } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to visualize equals() comparisons.',
    ru: 'Запустите код, чтобы визуализировать сравнения equals().',
  },
  scenario: { en: 'scenario', ru: 'сценарий' },
  objects: { en: 'Objects', ru: 'Объекты' },
  matrix: { en: 'Comparison matrix', ru: 'Матрица сравнений' },
  checks: { en: 'Contract checks', ru: 'Проверки контракта' },
  probes: { en: 'Collection probes', ru: 'Проверки коллекций' },
  method: { en: 'method', ru: 'метод' },
  fields: { en: 'fields', ru: 'поля' },
  trueValue: { en: 'true', ru: 'true' },
  falseValue: { en: 'false', ru: 'false' },
  missing: { en: 'not compared', ru: 'не сравнивали' },
  ok: { en: 'ok', ru: 'норма' },
  broken: { en: 'broken', ru: 'нарушено' },
  symmetry: { en: 'symmetry', ru: 'симметрия' },
  transitivity: { en: 'transitivity', ru: 'транзитивность' },
  noChecks: { en: 'No contract check yet.', ru: 'Проверки контракта пока нет.' },
  noProbes: { en: 'No collection probe yet.', ru: 'Проверки коллекции пока нет.' },
};

interface EqObject {
  id: string;
  label: string;
  type: string;
  fields: string[];
}

interface EqComparison {
  id: string;
  left: string;
  right: string;
  expression: string;
  method: string;
  result: boolean;
  event: string;
}

interface EqCheck {
  kind: string;
  ids: string[];
  ok: boolean;
  details: string;
}

interface EqProbe {
  expression: string;
  result: boolean;
}

interface EqualityState {
  scenario: string;
  objects: EqObject[];
  comparisons: EqComparison[];
  checks: EqCheck[];
  probes: EqProbe[];
}

export default function EqualityVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as EqualityState | undefined;

  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const latestByPair = new Map(state.comparisons.map((c) => [c.id, c]));
  const objectCells: ArrayCell[] = state.objects.map((obj) => ({
    key: obj.id,
    label: obj.id,
    highlighted: highlight.has(`object:${obj.id}`),
    content: (
      <div style={objectContentStyle}>
        <div style={objectTitleStyle}>
          <span>{obj.label}</span>
          <code style={typeStyle}>{obj.type}</code>
        </div>
        <div style={fieldStyle}>
          <span style={mutedStyle}>{tl(LABELS.fields, lang)}:</span> {obj.fields.join(', ')}
        </div>
      </div>
    ),
  }));

  const matrixCells: ArrayCell[] = state.objects.map((left) => ({
    key: left.id,
    label: `${left.id}.equals(...)`,
    highlighted: highlight.has(`object:${left.id}`),
    content: (
      <div style={matrixRowStyle}>
        {state.objects.map((right) => {
          const comparison = latestByPair.get(`${left.id}->${right.id}`);
          return (
            <div
              key={right.id}
              style={{
                ...matrixCellStyle,
                ...(comparison?.result === true ? trueCellStyle : {}),
                ...(comparison?.result === false ? falseCellStyle : {}),
                ...(highlight.has(`comparison:${left.id}->${right.id}`) ? activeCellStyle : {}),
              }}
              title={comparison ? comparison.expression : undefined}
            >
              <div style={targetStyle}>{right.id}</div>
              <div style={resultStyle}>{resultText(comparison?.result, lang)}</div>
              {comparison && (
                <div style={methodStyle}>
                  {tl(LABELS.method, lang)}: {comparison.method}
                </div>
              )}
            </div>
          );
        })}
      </div>
    ),
  }));

  return (
    <div style={wrapStyle}>
      <div style={scenarioStyle}>
        <span style={mutedStyle}>{tl(LABELS.scenario, lang)}:</span> {state.scenario}
      </div>

      <section>
        <div style={sectionLabelStyle}>{tl(LABELS.objects, lang)}</div>
        <ArrayGrid cells={objectCells} />
      </section>

      <section>
        <div style={sectionLabelStyle}>{tl(LABELS.matrix, lang)}</div>
        <ArrayGrid cells={matrixCells} />
      </section>

      <section>
        <div style={sectionLabelStyle}>{tl(LABELS.checks, lang)}</div>
        {state.checks.length === 0 ? (
          <div style={emptyStyle}>{tl(LABELS.noChecks, lang)}</div>
        ) : (
          <div style={listStyle}>
            {state.checks.map((check, i) => (
              <div key={i} style={{ ...checkStyle, ...(check.ok ? okStyle : brokenStyle) }}>
                <span style={badgeStyle}>{kindText(check.kind, lang)}</span>
                <span style={statusStyle}>{check.ok ? tl(LABELS.ok, lang) : tl(LABELS.broken, lang)}</span>
                <code style={detailsStyle}>{check.details}</code>
              </div>
            ))}
          </div>
        )}
      </section>

      <section>
        <div style={sectionLabelStyle}>{tl(LABELS.probes, lang)}</div>
        {state.probes.length === 0 ? (
          <div style={emptyStyle}>{tl(LABELS.noProbes, lang)}</div>
        ) : (
          <div style={listStyle}>
            {state.probes.map((probe, i) => (
              <div key={i} style={probeStyle}>
                <code>{probe.expression}</code>
                <span style={{ ...probeResultStyle, ...(probe.result ? trueCellStyle : falseCellStyle) }}>
                  {resultText(probe.result, lang)}
                </span>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

function resultText(value: boolean | undefined, lang: 'en' | 'ru') {
  if (value === undefined) return tl(LABELS.missing, lang);
  return value ? tl(LABELS.trueValue, lang) : tl(LABELS.falseValue, lang);
}

function kindText(kind: string, lang: 'en' | 'ru') {
  if (kind === 'symmetry') return tl(LABELS.symmetry, lang);
  if (kind === 'transitivity') return tl(LABELS.transitivity, lang);
  return kind;
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 14 };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
const scenarioStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 8,
  padding: '8px 10px',
  background: 'var(--viz-box)',
  fontFamily: 'monospace',
  fontSize: 13,
};
const sectionLabelStyle: CSSProperties = {
  fontSize: 11,
  textTransform: 'uppercase',
  letterSpacing: 0,
  opacity: 0.62,
  marginBottom: 6,
};
const mutedStyle: CSSProperties = { opacity: 0.65 };
const objectContentStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 4 };
const objectTitleStyle: CSSProperties = { display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' };
const typeStyle: CSSProperties = {
  fontSize: 12,
  padding: '1px 6px',
  borderRadius: 4,
  background: 'var(--viz-badge)',
};
const fieldStyle: CSSProperties = { fontSize: 12, fontFamily: 'monospace', opacity: 0.85 };
const matrixRowStyle: CSSProperties = { display: 'flex', gap: 6, flexWrap: 'wrap' };
const matrixCellStyle: CSSProperties = {
  width: 112,
  minHeight: 62,
  border: '1px solid var(--border)',
  borderRadius: 8,
  padding: '5px 6px',
  background: 'var(--viz-box)',
};
const trueCellStyle: CSSProperties = {
  background: 'rgba(82, 196, 126, 0.14)',
  borderColor: 'rgba(82, 196, 126, 0.48)',
};
const falseCellStyle: CSSProperties = {
  background: 'rgba(255, 107, 107, 0.13)',
  borderColor: 'rgba(255, 107, 107, 0.45)',
};
const activeCellStyle: CSSProperties = {
  boxShadow: '0 0 0 2px rgba(255,204,102,0.30)',
};
const targetStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 12, opacity: 0.72 };
const resultStyle: CSSProperties = { fontSize: 17, fontWeight: 700, marginTop: 2 };
const methodStyle: CSSProperties = { marginTop: 3, fontSize: 10, fontFamily: 'monospace', opacity: 0.72 };
const listStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 5 };
const checkStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: 8,
  flexWrap: 'wrap',
  border: '1px solid var(--border)',
  borderRadius: 8,
  padding: '6px 8px',
  background: 'var(--viz-box)',
};
const okStyle: CSSProperties = { borderColor: 'rgba(82, 196, 126, 0.42)' };
const brokenStyle: CSSProperties = { borderColor: 'rgba(255, 107, 107, 0.48)' };
const badgeStyle: CSSProperties = {
  fontSize: 11,
  fontWeight: 700,
  padding: '1px 6px',
  borderRadius: 4,
  background: 'var(--viz-badge)',
};
const statusStyle: CSSProperties = { fontSize: 12, fontWeight: 700 };
const detailsStyle: CSSProperties = { fontSize: 12, opacity: 0.8 };
const probeStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'space-between',
  gap: 10,
  border: '1px solid var(--border)',
  borderRadius: 8,
  padding: '6px 8px',
  background: 'var(--viz-box)',
};
const probeResultStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 6,
  padding: '2px 8px',
  fontWeight: 700,
};
const emptyStyle: CSSProperties = { opacity: 0.55, fontSize: 13, padding: 8 };
