import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { tl, useLang } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to visualize how a lambda becomes a method argument.',
    ru: 'Запустите код, чтобы увидеть, как lambda становится аргументом метода.',
  },
  target: { en: 'target type', ru: 'целевой тип' },
  lambda: { en: 'lambda value', ru: 'lambda-значение' },
  method: { en: 'receiving method', ru: 'принимающий метод' },
  invoke: { en: 'call point', ru: 'точка вызова' },
  data: { en: 'data / result', ru: 'данные / результат' },
  signature: { en: 'SAM', ru: 'SAM' },
  parameter: { en: 'parameter', ru: 'параметр' },
  argument: { en: 'argument', ru: 'аргумент' },
  result: { en: 'result', ru: 'результат' },
  captured: { en: 'captured', ru: 'захвачено' },
  phase: { en: 'phase', ru: 'этап' },
  empty: { en: 'empty', ru: 'пусто' },
};

interface CapturedLocal {
  name: string;
  value: string;
}

interface LambdaState {
  targetType: string;
  samSignature: string;
  lambdaName: string;
  lambdaExpression: string;
  receivingMethod: string;
  parameterName: string;
  lastCall: string;
  argument: string;
  result: string;
  phase: string;
  captured: CapturedLocal[];
}

export default function LambdaParameterVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as LambdaState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const empty = tl(LABELS.empty, lang);
  const captured = state.captured.length
    ? state.captured.map((c) => `${c.name} = ${c.value}`).join(', ')
    : empty;

  const dataLines = [
    state.argument ? `${tl(LABELS.argument, lang)}: ${state.argument}` : '',
    state.result ? `${tl(LABELS.result, lang)}: ${state.result}` : '',
    state.captured.length ? `${tl(LABELS.captured, lang)}: ${captured}` : '',
  ].filter(Boolean);

  const cells: ArrayCell[] = [
    {
      key: 'target',
      label: '[type]',
      highlighted: highlight.has('card:target'),
      content: (
        <CellContent
          title={tl(LABELS.target, lang)}
          value={state.targetType || empty}
          meta={`${tl(LABELS.signature, lang)}: ${state.samSignature || empty}`}
        />
      ),
    },
    {
      key: 'lambda',
      label: '[lambda]',
      highlighted: highlight.has('card:lambda'),
      content: (
        <CellContent
          title={tl(LABELS.lambda, lang)}
          value={state.lambdaName || empty}
          meta={state.lambdaExpression || empty}
        />
      ),
    },
    {
      key: 'method',
      label: '[method]',
      highlighted: highlight.has('card:method'),
      content: (
        <CellContent
          title={tl(LABELS.method, lang)}
          value={state.receivingMethod || empty}
          meta={`${tl(LABELS.parameter, lang)}: ${state.parameterName || empty}`}
        />
      ),
    },
    {
      key: 'invoke',
      label: '[call]',
      highlighted: highlight.has('card:invoke'),
      content: (
        <CellContent
          title={tl(LABELS.invoke, lang)}
          value={state.lastCall || empty}
          meta={`${tl(LABELS.phase, lang)}: ${state.phase}`}
        />
      ),
    },
    {
      key: 'data',
      label: '[data]',
      highlighted: highlight.has('card:data') || highlight.has('card:capture'),
      content: (
        <CellContent
          title={tl(LABELS.data, lang)}
          value={dataLines.length ? dataLines.join(' | ') : empty}
        />
      ),
    },
  ];

  return (
    <div style={wrapStyle}>
      <ArrayGrid cells={cells} />
    </div>
  );
}

function CellContent({ title, value, meta }: { title: string; value: string; meta?: string }) {
  return (
    <div style={cellStyle}>
      <div style={titleStyle}>{title}</div>
      <div style={valueStyle}>{value}</div>
      {meta ? <div style={metaStyle}>{meta}</div> : null}
    </div>
  );
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 12 };
const cellStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 2, minWidth: 0 };
const titleStyle: CSSProperties = { fontSize: 11, opacity: 0.62 };
const valueStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 14,
  color: 'var(--text)',
  overflowWrap: 'anywhere',
};
const metaStyle: CSSProperties = { fontSize: 12, opacity: 0.72, overflowWrap: 'anywhere' };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
