import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { tl, useLang, type Lang, type Localized } from '@app/i18n';

const LABELS = {
  runHint: {
    en: 'Run the code to visualize the anonymous class.',
    ru: 'Запустите код, чтобы увидеть анонимный класс.',
  },
  target: { en: 'target type', ru: 'целевой тип' },
  generated: { en: 'generated class', ru: 'сгенерированный класс' },
  object: { en: 'object reference', ru: 'ссылка на объект' },
  capture: { en: 'captured locals', ru: 'захваченные locals' },
  call: { en: 'method call', ru: 'вызов метода' },
  handoff: { en: 'passed to API', ru: 'передан в API' },
  kind: { en: 'kind', ru: 'вид' },
  method: { en: 'method', ru: 'метод' },
  relation: { en: 'relation', ru: 'связь' },
  result: { en: 'result', ru: 'результат' },
  api: { en: 'API', ru: 'API' },
  empty: { en: 'empty', ru: 'пусто' },
  anonymous: { en: 'anonymous runtime class', ru: 'анонимный runtime-класс' },
  notAnonymous: { en: 'not anonymous', ru: 'не анонимный' },
};

const KIND_LABELS: Record<string, Localized> = {
  interface: { en: 'interface', ru: 'интерфейс' },
  'abstract class': { en: 'abstract class', ru: 'абстрактный класс' },
  class: { en: 'class', ru: 'класс' },
};

const RELATION_LABELS: Record<string, Localized> = {
  implements: { en: 'implements', ru: 'implements' },
  extends: { en: 'extends', ru: 'extends' },
};

interface CapturedLocal {
  name: string;
  value: string;
}

interface CallInfo {
  method: string;
  result: string;
}

interface HandoffInfo {
  api: string;
  argument: string;
}

interface AnonymousClassState {
  targetType: string;
  targetKind: string;
  method: string;
  relation: string;
  variableName: string;
  generatedClassName: string;
  anonymousClass: boolean;
  captured: CapturedLocal[];
  lastCall: CallInfo | null;
  handoff: HandoffInfo | null;
}

export default function AnonymousClassVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as AnonymousClassState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const empty = tl(LABELS.empty, lang);
  const cells: ArrayCell[] = [
    {
      key: 'target',
      label: '[target]',
      highlighted: highlight.has('card:target'),
      content: (
        <CellContent
          title={tl(LABELS.target, lang)}
          value={state.targetType || empty}
          meta={`${tl(LABELS.kind, lang)}: ${localized(KIND_LABELS, state.targetKind, lang)}; ${tl(
            LABELS.method,
            lang,
          )}: ${state.method || empty}`}
        />
      ),
    },
    {
      key: 'generated',
      label: '[class]',
      highlighted: highlight.has('card:generated'),
      content: (
        <CellContent
          title={tl(LABELS.generated, lang)}
          value={state.generatedClassName || empty}
          meta={`${tl(LABELS.relation, lang)}: ${localized(
            RELATION_LABELS,
            state.relation,
            lang,
          )}; ${state.anonymousClass ? tl(LABELS.anonymous, lang) : tl(LABELS.notAnonymous, lang)}`}
        />
      ),
    },
    {
      key: 'object',
      label: '[ref]',
      highlighted: highlight.has('card:object'),
      content: (
        <CellContent
          title={tl(LABELS.object, lang)}
          value={state.variableName || empty}
          meta={state.variableName ? `${state.variableName}: ${state.targetType}` : empty}
        />
      ),
    },
    {
      key: 'capture',
      label: '[locals]',
      highlighted: highlight.has('card:capture'),
      content: (
        <CellContent
          title={tl(LABELS.capture, lang)}
          value={state.captured.length ? state.captured.map((c) => `${c.name} = ${c.value}`).join(', ') : empty}
        />
      ),
    },
    {
      key: 'call',
      label: '[call]',
      highlighted: highlight.has('card:call'),
      content: (
        <CellContent
          title={tl(LABELS.call, lang)}
          value={state.lastCall?.method ?? empty}
          meta={
            state.lastCall
              ? `${tl(LABELS.result, lang)}: ${state.lastCall.result}`
              : empty
          }
        />
      ),
    },
    {
      key: 'handoff',
      label: '[api]',
      highlighted: highlight.has('card:handoff'),
      content: (
        <CellContent
          title={tl(LABELS.handoff, lang)}
          value={state.handoff?.argument ?? empty}
          meta={state.handoff ? `${tl(LABELS.api, lang)}: ${state.handoff.api}` : empty}
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

function localized(map: Record<string, Localized>, key: string, lang: Lang): string {
  return tl(map[key] ?? key, lang);
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
