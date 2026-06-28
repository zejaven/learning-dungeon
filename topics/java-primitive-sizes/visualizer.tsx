import type { CSSProperties } from 'react';
import type { VisualizerProps } from '@app/engine/traceTypes';
import { ArrayGrid, type ArrayCell } from '@app/primitives/ArrayGrid';
import { BoxGroup, type Box } from '@app/primitives/BoxGroup';
import { tl, useLang } from '@app/i18n';

const LABELS = {
  bytes: { en: 'bytes', ru: 'байт' },
  bits: { en: 'bits', ru: 'бит' },
  range: { en: 'range', ru: 'диапазон' },
  fixed: { en: 'fixed by Java', ru: 'фиксировано Java' },
  notSpecified: { en: 'JVM-specific storage', ru: 'хранение зависит от JVM' },
  integer: { en: 'integer', ru: 'целый' },
  floating: { en: 'floating point', ru: 'плавающая точка' },
  character: { en: 'character code unit', ru: 'кодовая единица символа' },
  logical: { en: 'logical', ru: 'логический' },
  runHint: {
    en: 'Run the code to see primitive sizes.',
    ru: 'Запустите код, чтобы увидеть размеры примитивов.',
  },
  tableNote: {
    en: 'Memorize the table, then call out boolean as the exception.',
    ru: 'Запомните таблицу, затем отдельно проговорите boolean как исключение.',
  },
  integerNote: {
    en: 'Integer widths follow the 1, 2, 4, 8 byte pattern.',
    ru: 'Ширина целых типов идет по схеме 1, 2, 4, 8 байт.',
  },
  floatingNote: {
    en: 'float is 4 bytes; double is 8 bytes.',
    ru: 'float занимает 4 байта; double занимает 8 байт.',
  },
  charNote: {
    en: 'char is a 16-bit UTF-16 code unit.',
    ru: 'char - это 16-битная кодовая единица UTF-16.',
  },
  booleanNote: {
    en: 'boolean has true/false values, but no portable byte count.',
    ru: 'boolean имеет значения true/false, но не имеет переносимого числа байт.',
  },
  storageNote: {
    en: 'Value width is separate from object layout, padding, headers, and JVM slots.',
    ru: 'Ширина значения отделена от раскладки объекта, выравнивания, заголовков и JVM slots.',
  },
  valueWidth: {
    en: 'value width',
    ru: 'ширина значения',
  },
  footprint: {
    en: 'full footprint',
    ru: 'полный след',
  },
  stackSlots: {
    en: 'JVM slots',
    ru: 'JVM slots',
  },
  valueWidthSub: {
    en: 'the table answers this',
    ru: 'на это отвечает таблица',
  },
  footprintSub: {
    en: 'headers and padding can add memory',
    ru: 'заголовки и выравнивание добавляют память',
  },
  stackSlotsSub: {
    en: 'locals are counted in JVM slots',
    ru: 'локальные переменные считаются в JVM slots',
  },
};

type Focus = 'table' | 'integer' | 'floating' | 'char' | 'boolean' | 'storage';
type Family = 'integer' | 'floating' | 'character' | 'logical';
type Storage = 'fixed' | 'not-specified';

interface PrimitiveRow {
  type: string;
  family: Family;
  bits: number | null;
  bytes: number | null;
  storage: Storage;
  range: string;
}

interface PrimitiveSizesState {
  focus: Focus;
  rows: PrimitiveRow[];
}

const FAMILY_LABEL: Record<Family, keyof typeof LABELS> = {
  integer: 'integer',
  floating: 'floating',
  character: 'character',
  logical: 'logical',
};

const NOTE_BY_FOCUS: Record<Focus, keyof typeof LABELS> = {
  table: 'tableNote',
  integer: 'integerNote',
  floating: 'floatingNote',
  char: 'charNote',
  boolean: 'booleanNote',
  storage: 'storageNote',
};

export default function PrimitiveSizesVisualizer({ event }: VisualizerProps) {
  const lang = useLang((s) => s.lang);
  const state = event?.state as PrimitiveSizesState | undefined;
  if (!state) {
    return <div style={hintStyle}>{tl(LABELS.runHint, lang)}</div>;
  }

  const highlight = new Set(event?.highlight ?? []);
  const cells: ArrayCell[] = state.rows.map((row) => ({
    key: row.type,
    label: row.type,
    highlighted: highlight.has(`type:${row.type}`),
    content: (
      <div style={rowContentStyle}>
        <div style={sizeStyle}>
          {row.bytes == null ? (
            <span style={warnStyle}>{tl(LABELS.notSpecified, lang)}</span>
          ) : (
            <span>
              {row.bytes} {tl(LABELS.bytes, lang)}
            </span>
          )}
          <span style={bitStyle}>
            {row.bits == null ? '' : `${row.bits} ${tl(LABELS.bits, lang)}`}
          </span>
        </div>
        <div style={metaStyle}>
          <span style={badgeStyle}>{tl(LABELS[FAMILY_LABEL[row.family]], lang)}</span>
          <span style={badgeStyle}>
            {row.storage === 'fixed' ? tl(LABELS.fixed, lang) : tl(LABELS.notSpecified, lang)}
          </span>
          <span>
            {tl(LABELS.range, lang)}: <code>{row.range}</code>
          </span>
        </div>
      </div>
    ),
  }));

  return (
    <div style={wrapStyle}>
      <div style={noteStyle}>{tl(LABELS[NOTE_BY_FOCUS[state.focus]], lang)}</div>
      {state.focus === 'storage' && <BoxGroup boxes={storageBoxes(lang)} />}
      <ArrayGrid cells={cells} />
    </div>
  );
}

function storageBoxes(lang: 'en' | 'ru'): Box[] {
  return [
    {
      id: 'value-width',
      title: tl(LABELS.valueWidth, lang),
      subtitle: tl(LABELS.valueWidthSub, lang),
      highlighted: true,
    },
    {
      id: 'full-footprint',
      title: tl(LABELS.footprint, lang),
      subtitle: tl(LABELS.footprintSub, lang),
    },
    {
      id: 'jvm-slots',
      title: tl(LABELS.stackSlots, lang),
      subtitle: tl(LABELS.stackSlotsSub, lang),
    },
  ];
}

const wrapStyle: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 10 };
const noteStyle: CSSProperties = {
  border: '1px solid var(--border)',
  borderRadius: 8,
  padding: '8px 10px',
  background: 'var(--viz-box)',
  fontSize: 13,
};
const rowContentStyle: CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: 4,
  width: '100%',
};
const sizeStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'baseline',
  gap: 8,
  fontFamily: 'monospace',
  fontWeight: 700,
};
const bitStyle: CSSProperties = { fontSize: 12, opacity: 0.65, fontWeight: 500 };
const warnStyle: CSSProperties = { color: 'var(--accent)' };
const metaStyle: CSSProperties = {
  display: 'flex',
  flexWrap: 'wrap',
  gap: 6,
  fontSize: 11,
  opacity: 0.78,
};
const badgeStyle: CSSProperties = {
  padding: '1px 6px',
  borderRadius: 6,
  background: 'var(--viz-badge, rgba(127,127,127,0.18))',
};
const hintStyle: CSSProperties = { opacity: 0.5, fontSize: 14, padding: 16 };
