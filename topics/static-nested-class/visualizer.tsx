import type { CSSProperties, ReactNode } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang, type Lang } from '@app/i18n';

const LABELS = {
  types: { en: 'Types', ru: 'Типы' },
  staticMembers: { en: 'Static members', ru: 'Static members' },
  outerObjects: { en: 'Outer objects', ru: 'Объекты внешнего класса' },
  nestedObjects: { en: 'Nested objects', ru: 'Вложенные объекты' },
  enclosingType: { en: 'enclosing type', ru: 'внешний тип' },
  noHiddenOuter: { en: 'no hidden outer reference', ru: 'нет скрытой ссылки на внешний объект' },
  needsOuter: { en: 'needs an outer object', ru: 'нужен объект внешнего класса' },
  outerRef: { en: 'outer', ru: 'внешний объект' },
  fields: { en: 'fields', ru: 'поля' },
  runHint: {
    en: 'Run the code to see how static nested classes relate to outer objects.',
    ru: 'Запустите код, чтобы увидеть, как static nested class связан с внешними объектами.',
  },
};

interface TypeNode {
  id: string;
  name: string;
  role: string;
  needsOuter: boolean;
}

interface Field {
  name: string;
  value: string;
}

interface SceneObject {
  id: string;
  type: string;
  label: string;
  outerRef: string | null;
  fields: Field[];
}

interface StaticMember {
  id: string;
  name: string;
  value: string;
}

interface StaticNestedState {
  kind: 'staticNestedClass';
  types: TypeNode[];
  staticMembers: StaticMember[];
  outerObjects: SceneObject[];
  nestedObjects: SceneObject[];
}

export default function StaticNestedClassVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as StaticNestedState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const outerById = new Map(state.outerObjects.map((o) => [o.id, o]));

  const typeBoxes: Box[] = state.types.map((type) => ({
    id: type.id,
    title: type.name,
    subtitle: typeSubtitle(type, lang),
    highlighted: highlight.has(type.id),
  }));

  const memberBoxes: Box[] = state.staticMembers.map((member) => ({
    id: member.id,
    title: member.name,
    subtitle: `= ${member.value}`,
    highlighted: highlight.has(member.id),
  }));

  const outerBoxes: Box[] = state.outerObjects.map((obj) => ({
    id: obj.id,
    title: obj.label,
    subtitle: `${obj.type}${fieldSuffix(obj, lang)}`,
    highlighted: highlight.has(`outer:${obj.id}`),
  }));

  const nestedBoxes: Box[] = state.nestedObjects.map((obj) => ({
    id: obj.id,
    title: obj.label,
    subtitle: nestedSubtitle(obj, outerById, lang),
    highlighted: highlight.has(`nested:${obj.id}`),
  }));

  return (
    <div style={wrapStyle}>
      <Section title={tl(LABELS.types, lang)}>
        <BoxGroup boxes={typeBoxes} />
      </Section>
      <Section title={tl(LABELS.staticMembers, lang)}>
        <BoxGroup boxes={memberBoxes} />
      </Section>
      <div style={columnsStyle}>
        <Section title={tl(LABELS.outerObjects, lang)}>
          <BoxGroup boxes={outerBoxes} />
        </Section>
        <Section title={tl(LABELS.nestedObjects, lang)}>
          <BoxGroup boxes={nestedBoxes} />
        </Section>
      </div>
    </div>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div style={sectionStyle}>
      <div style={sectionLabelStyle}>{title}</div>
      {children}
    </div>
  );
}

function typeSubtitle(type: TypeNode, lang: Lang): string {
  if (type.needsOuter) return tl(LABELS.needsOuter, lang);
  if (type.id === 'type:static') return tl(LABELS.noHiddenOuter, lang);
  return tl(LABELS.enclosingType, lang);
}

function nestedSubtitle(obj: SceneObject, outerById: Map<string, SceneObject>, lang: Lang): string {
  const relation = obj.outerRef
    ? `${tl(LABELS.outerRef, lang)} -> ${outerById.get(obj.outerRef)?.label ?? obj.outerRef}`
    : tl(LABELS.noHiddenOuter, lang);
  return `${obj.type} · ${relation}${fieldSuffix(obj, lang)}`;
}

function fieldSuffix(obj: SceneObject, lang: Lang): string {
  if (obj.fields.length === 0) return '';
  return ` · ${tl(LABELS.fields, lang)}: ${obj.fields.map((f) => `${f.name}=${f.value}`).join(', ')}`;
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 10 };
const columnsStyle: CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
  gap: 10,
};
const sectionStyle: CSSProperties = {
  border: '1px dashed var(--border)',
  borderRadius: 8,
  padding: '8px 10px',
  display: 'flex',
  flexDirection: 'column',
  gap: 6,
};
const sectionLabelStyle: CSSProperties = {
  fontSize: 11,
  textTransform: 'uppercase',
  letterSpacing: 0,
  opacity: 0.58,
};
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
