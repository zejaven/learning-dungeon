import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { tl, useLang } from '@app/i18n';

const LABELS = {
  bindings: { en: 'Bindings', ru: 'Связи' },
  local: { en: 'local variable', ru: 'локальная' },
  static: { en: 'static constant', ru: 'константа' },
  field: { en: 'field', ru: 'поле' },
  parameter: { en: 'parameter', ru: 'параметр' },
  method: { en: 'method', ru: 'метод' },
  class: { en: 'class', ru: 'класс' },
  locked: { en: 'locked', ru: 'заблокировано' },
  unlocked: { en: 'unlocked (blank final)', ru: 'не заблокировано (пустое final)' },
  mutableObject: { en: 'object still mutable', ru: 'объект ещё изменяем' },
  noOverride: { en: 'cannot override', ru: 'нельзя переопределить' },
  noExtend: { en: 'cannot extend', ru: 'нельзя наследовать' },
  statusLocked: { en: 'binding locked', ru: 'связь заблокирована' },
  statusAssigned: { en: 'assigned once', ru: 'присвоено один раз' },
  statusBlocked: { en: 'rejected: cannot reassign a final', ru: 'отклонено: нельзя переприсвоить final' },
  statusMutated: { en: 'object contents changed, binding unchanged', ru: 'содержимое объекта изменилось, связь та же' },
  stays: { en: 'stays', ru: 'остаётся' },
  runHint: {
    en: 'Run the code to see each final binding, where it appears, and whether it is locked.',
    ru: 'Запустите код, чтобы увидеть каждую final-связь, где она встречается и заблокирована ли она.',
  },
};

type Context = 'local' | 'static' | 'field' | 'parameter' | 'method' | 'class';
type Status = 'locked' | 'assigned' | 'blocked' | 'mutated';

interface Binding {
  name: string;
  context: Context;
  type: string | null;
  value: string | null;
  locked: boolean;
  mutable: boolean;
}
interface Note {
  expr: string;
  status: Status;
  detail: string | null;
}
interface SceneState {
  bindings: Binding[];
  note: Note | null;
}

const CONTEXT_LABEL: Record<Context, keyof typeof LABELS> = {
  local: 'local',
  static: 'static',
  field: 'field',
  parameter: 'parameter',
  method: 'method',
  class: 'class',
};

const STATUS_LABEL: Record<Status, keyof typeof LABELS> = {
  locked: 'statusLocked',
  assigned: 'statusAssigned',
  blocked: 'statusBlocked',
  mutated: 'statusMutated',
};

export default function FinalVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as SceneState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);

  return (
    <div style={wrapStyle}>
      <div style={sectionLabelStyle}>{tl(LABELS.bindings, lang)}</div>
      <div style={slotsStyle}>
        {state.bindings.length === 0 && <span style={dashStyle}>—</span>}
        {state.bindings.map((b) => {
          const isHi = highlight.has(`binding:${b.name}`);
          const sealed = b.context === 'method' || b.context === 'class';
          return (
            <div
              key={b.name}
              style={{
                ...slotStyle,
                ...(b.mutable ? slotMutableStyle : {}),
                ...(isHi ? slotHighlightStyle : {}),
              }}
            >
              <div style={slotHeadStyle}>
                <span style={lockStyle}>{b.locked ? '🔒' : '🔓'}</span>
                {b.type && <span style={typeStyle}>{b.type}</span>}
                <span style={nameStyle}>{b.name}</span>
              </div>
              {b.value != null && <div style={valueStyle}>{b.value}</div>}
              <div style={metaRowStyle}>
                <span style={badgeStyle}>{tl(LABELS[CONTEXT_LABEL[b.context]], lang)}</span>
                <span style={b.locked ? badgeStyle : badgeOpenStyle}>
                  {tl(b.locked ? LABELS.locked : LABELS.unlocked, lang)}
                </span>
                {b.mutable && <span style={badgeWarnStyle}>{tl(LABELS.mutableObject, lang)}</span>}
                {b.context === 'method' && <span style={badgeStyle}>{tl(LABELS.noOverride, lang)}</span>}
                {b.context === 'class' && <span style={badgeStyle}>{tl(LABELS.noExtend, lang)}</span>}
              </div>
            </div>
          );
        })}
      </div>

      {state.note && (
        <div
          style={{
            ...noteStyle,
            ...(state.note.status === 'blocked' ? noteBlockedStyle : {}),
            ...(state.note.status === 'mutated' ? noteMutatedStyle : {}),
          }}
        >
          <code style={noteExprStyle}>{state.note.expr}</code>
          <span style={noteArrowStyle}>→</span>
          <span style={noteResultStyle}>
            {tl(LABELS[STATUS_LABEL[state.note.status]], lang)}
            {state.note.detail != null && state.note.status === 'blocked' && (
              <>
                {' '}
                ({tl(LABELS.stays, lang)} <code>{state.note.detail}</code>)
              </>
            )}
          </span>
        </div>
      )}
    </div>
  );
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 10 };
const sectionLabelStyle: CSSProperties = {
  fontSize: 11,
  textTransform: 'uppercase',
  letterSpacing: 0.5,
  opacity: 0.55,
};
const slotsStyle: CSSProperties = { display: 'flex', flexWrap: 'wrap', gap: 8 };
const dashStyle: CSSProperties = { opacity: 0.4 };
const slotStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 8,
  padding: '6px 10px',
  background: 'var(--viz-box)',
  minWidth: 120,
  display: 'flex',
  flexDirection: 'column',
  gap: 4,
};
const slotMutableStyle: CSSProperties = { borderStyle: 'dashed' };
const slotHighlightStyle: CSSProperties = {
  borderColor: 'var(--accent)',
  background: 'var(--viz-highlight)',
  boxShadow: '0 0 0 2px rgba(255,204,102,0.35)',
};
const slotHeadStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'baseline',
  gap: 6,
  fontFamily: 'monospace',
};
const lockStyle: CSSProperties = { fontSize: 12 };
const typeStyle: CSSProperties = { fontSize: 12, opacity: 0.7 };
const nameStyle: CSSProperties = { fontWeight: 700, fontSize: 14 };
const valueStyle: CSSProperties = {
  fontFamily: 'monospace',
  fontSize: 15,
  fontWeight: 600,
  wordBreak: 'break-all',
};
const metaRowStyle: CSSProperties = { display: 'flex', flexWrap: 'wrap', gap: 4 };
const badgeStyle: CSSProperties = {
  fontSize: 10,
  padding: '1px 6px',
  borderRadius: 10,
  background: 'var(--viz-badge, rgba(127,127,127,0.18))',
  opacity: 0.85,
};
const badgeOpenStyle: CSSProperties = {
  ...badgeStyle,
  background: 'rgba(120,170,255,0.22)',
};
const badgeWarnStyle: CSSProperties = {
  ...badgeStyle,
  background: 'rgba(255,170,90,0.25)',
};
const noteStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: 8,
  flexWrap: 'wrap',
  padding: '6px 10px',
  border: '1px dashed var(--border)',
  borderRadius: 8,
};
const noteBlockedStyle: CSSProperties = {
  borderColor: 'rgba(255,90,90,0.7)',
  background: 'rgba(255,90,90,0.12)',
};
const noteMutatedStyle: CSSProperties = {
  borderColor: 'rgba(120,200,120,0.7)',
  background: 'rgba(120,200,120,0.12)',
};
const noteExprStyle: CSSProperties = { fontFamily: 'monospace', fontSize: 13 };
const noteArrowStyle: CSSProperties = { opacity: 0.6 };
const noteResultStyle: CSSProperties = { fontSize: 13, fontWeight: 600 };
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
