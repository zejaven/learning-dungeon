import type { CSSProperties, ReactNode } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang } from '@app/i18n';

const LABELS = {
  classArea: { en: 'Class area', ru: 'Область класса' },
  objects: { en: 'Objects', ru: 'Объекты' },
  calls: { en: 'Calls', ru: 'Вызовы' },
  nested: { en: 'Static nested classes', ru: 'Static nested classes' },
  initialized: { en: 'initialized', ru: 'инициализирован' },
  notInitialized: { en: 'not initialized', ru: 'не инициализирован' },
  initCount: { en: 'init count', ru: 'число инициализаций' },
  staticField: { en: 'static field', ru: 'static-поле' },
  constant: { en: 'static final constant', ru: 'static final константа' },
  instanceFields: { en: 'instance fields', ru: 'instance-поля' },
  noStaticFields: { en: 'no static fields yet', ru: 'static-полей пока нет' },
  noObjects: { en: 'no objects yet', ru: 'объектов пока нет' },
  noFields: { en: 'no fields', ru: 'нет полей' },
  classTarget: { en: 'class', ru: 'класс' },
  objectTarget: { en: 'object', ru: 'объект' },
  noOuterThis: { en: 'no outer this', ru: 'нет outer this' },
  runHint: {
    en: 'Run the code to see class-level static members and object-level instance fields.',
    ru: 'Запустите код, чтобы увидеть static-члены уровня класса и instance-поля уровня объекта.',
  },
};

interface StaticField {
  name: string;
  value: string;
  constant: boolean;
}

interface InstanceField {
  name: string;
  value: string;
}

interface Instance {
  id: string;
  type: string;
  fields: InstanceField[];
}

interface Call {
  target: 'class' | 'object';
  receiver: string;
  method: string;
}

interface NestedClass {
  name: string;
}

interface StaticState {
  className: string;
  initialized: boolean;
  initializationCount: number;
  staticFields: StaticField[];
  instances: Instance[];
  calls: Call[];
  nestedClasses: NestedClass[];
}

export default function StaticVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as StaticState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const staticBoxes: Box[] = state.staticFields.map((field) => ({
    id: field.name,
    title: `${state.className}.${field.name} = ${field.value}`,
    subtitle: field.constant ? tl(LABELS.constant, lang) : tl(LABELS.staticField, lang),
    highlighted: highlight.has(`static:${field.name}`),
  }));

  const objectBoxes: Box[] = state.instances.map((instance) => ({
    id: instance.id,
    title: `${instance.type} ${instance.id}`,
    subtitle:
      instance.fields.length > 0
        ? `${tl(LABELS.instanceFields, lang)}: ${fieldsText(instance.fields)}`
        : tl(LABELS.noFields, lang),
    highlighted: highlight.has(`obj:${instance.id}`),
  }));

  const nestedBoxes: Box[] = state.nestedClasses.map((nested) => ({
    id: nested.name,
    title: nested.name,
    subtitle: tl(LABELS.noOuterThis, lang),
    highlighted: highlight.has(`nested:${nested.name}`),
  }));

  return (
    <div style={wrapStyle}>
      <div
        style={{
          ...classCardStyle,
          ...(highlight.has(`class:${state.className}`) ? highlightStyle : {}),
        }}
      >
        <div style={classNameStyle}>{state.className}</div>
        <div style={metaStyle}>
          <span style={badgeStyle}>
            {state.initialized ? tl(LABELS.initialized, lang) : tl(LABELS.notInitialized, lang)}
          </span>
          <span style={badgeStyle}>
            {tl(LABELS.initCount, lang)}: {state.initializationCount}
          </span>
        </div>
      </div>

      <Section title={tl(LABELS.classArea, lang)} empty={tl(LABELS.noStaticFields, lang)}>
        <BoxGroup boxes={staticBoxes} />
      </Section>

      <Section title={tl(LABELS.objects, lang)} empty={tl(LABELS.noObjects, lang)}>
        <BoxGroup boxes={objectBoxes} />
      </Section>

      {state.calls.length > 0 && (
        <div style={sectionStyle}>
          <div style={sectionLabelStyle}>{tl(LABELS.calls, lang)}</div>
          <div style={callsStyle}>
            {state.calls.map((call, index) => (
              <div
                key={`${call.receiver}-${call.method}-${index}`}
                style={{
                  ...callStyle,
                  ...(highlight.has(`call:${call.method}`) ? highlightStyle : {}),
                }}
              >
                <span style={badgeStyle}>
                  {call.target === 'class' ? tl(LABELS.classTarget, lang) : tl(LABELS.objectTarget, lang)}
                </span>
                <code style={codeStyle}>
                  {call.receiver}.{call.method}
                </code>
              </div>
            ))}
          </div>
        </div>
      )}

      {state.nestedClasses.length > 0 && (
        <Section title={tl(LABELS.nested, lang)} empty="">
          <BoxGroup boxes={nestedBoxes} />
        </Section>
      )}
    </div>
  );
}

function Section({
  title,
  empty,
  children,
}: {
  title: string;
  empty: string;
  children: ReactNode;
}) {
  return (
    <div style={sectionStyle}>
      <div style={sectionLabelStyle}>{title}</div>
      {empty ? <div style={emptySlotStyle}>{children}</div> : children}
    </div>
  );
}

function fieldsText(fields: InstanceField[]): string {
  return fields.map((field) => `${field.name}=${field.value}`).join(', ');
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 10 };
const classCardStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 8,
  padding: '8px 10px',
  background: 'var(--viz-box)',
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'center',
  gap: 8,
  flexWrap: 'wrap',
};
const classNameStyle: CSSProperties = { fontFamily: 'monospace', fontWeight: 700, fontSize: 16 };
const metaStyle: CSSProperties = { display: 'flex', gap: 6, flexWrap: 'wrap' };
const badgeStyle: CSSProperties = {
  fontSize: 10,
  padding: '1px 6px',
  borderRadius: 10,
  background: 'var(--viz-badge, rgba(127,127,127,0.18))',
  opacity: 0.9,
};
const highlightStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  background: 'var(--viz-highlight)',
  boxShadow: '0 0 0 2px rgba(255,204,102,0.35)',
};
const sectionStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 6 };
const sectionLabelStyle: CSSProperties = {
  fontSize: 11,
  textTransform: 'uppercase',
  letterSpacing: 0,
  opacity: 0.55,
};
const emptySlotStyle: CSSProperties = { minHeight: 24 };
const callsStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 6 };
const callStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 6,
  padding: '5px 8px',
  background: 'var(--viz-box)',
  display: 'flex',
  alignItems: 'center',
  gap: 8,
  flexWrap: 'wrap',
};
const codeStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 13 };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
