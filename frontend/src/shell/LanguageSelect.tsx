import { useEffect, useRef, useState } from 'react';
import { useGenLangs } from '@app/engine/genLangStore';
import { LANG_CODES, langChip, langName, orderLangs, type Lang } from '@app/languages';
import { ui, useLang } from '@app/i18n';

interface Props {
  /** Languages to offer; defaults to every registered one. */
  options?: Lang[];
  /** Label before the control; pass '' to hide it. */
  label?: string;
  /** Controlled selection. Omit to bind to the global generation-language store. */
  value?: Lang[];
  onChange?: (langs: Lang[]) => void;
  /**
   * Render the checkbox list in flow instead of a floating popover. Dialogs need
   * this: `.dialog` clips overflow, which would cut a popover off.
   */
  inline?: boolean;
  disabled?: boolean;
}

/**
 * Generation-language picker: a dropdown with one checkbox per language. Sits
 * next to the style selector, since both are parameters of the same AI run.
 * The last selected language cannot be unchecked, and its checkbox is disabled
 * so the rule is visible instead of silently ignoring the click.
 */
export function LanguageSelect({ options, label, value, onChange, inline, disabled }: Props) {
  const lang = useLang((s) => s.lang);
  const storeSelected = useGenLangs((s) => s.selected);
  const storeToggle = useGenLangs((s) => s.toggle);
  const [open, setOpen] = useState(false);
  const boxRef = useRef<HTMLDivElement>(null);

  const codes = options ?? LANG_CODES;
  const selected = value ?? storeSelected;
  const toggle = (code: Lang) => {
    if (!onChange) {
      storeToggle(code);
      return;
    }
    const next = selected.includes(code)
      ? selected.filter((c) => c !== code)
      : orderLangs([...selected, code]);
    if (next.length) onChange(next);
  };

  useEffect(() => {
    if (!open || inline) return undefined;
    function onDocMouseDown(e: MouseEvent) {
      if (!boxRef.current?.contains(e.target as Node)) setOpen(false);
    }
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') setOpen(false);
    }
    document.addEventListener('mousedown', onDocMouseDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDocMouseDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open, inline]);

  const summary =
    selected.length === codes.length ? ui('allLanguages', lang) : selected.map(langChip).join(', ');

  const list = (
    <div className="lang-select-menu" role="group" aria-label={ui('genLangsLabel', lang)}>
      {codes.map((code) => {
        const checked = selected.includes(code);
        return (
          <label className="lang-select-item" key={code}>
            <input
              type="checkbox"
              checked={checked}
              // The last selected language stays on: a lesson or topic must be
              // generated in at least one.
              disabled={disabled || (checked && selected.length === 1)}
              onChange={() => toggle(code)}
            />{' '}
            {langName(code)}
          </label>
        );
      })}
    </div>
  );

  return (
    <div className={`lang-select${inline ? ' inline' : ''}`} ref={boxRef}>
      {label !== '' && <span className="style-label">{label ?? ui('genLangsLabel', lang)}</span>}
      {inline ? (
        list
      ) : (
        <>
          <button
            type="button"
            className="lang-select-trigger"
            aria-haspopup="true"
            aria-expanded={open}
            disabled={disabled}
            onClick={() => setOpen((v) => !v)}
          >
            {summary} <span className="lang-select-caret">▾</span>
          </button>
          {open && list}
        </>
      )}
    </div>
  );
}
