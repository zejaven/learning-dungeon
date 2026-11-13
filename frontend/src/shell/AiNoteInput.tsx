import { useEffect, useRef, useState } from 'react';
import { ui, useLang } from '@app/i18n';

/**
 * The optional "note for the AI" of a generation row: a focus, things to skip,
 * a case to add. It travels as `note` (topic, theory version) or `comment`
 * (lesson) and lands at the end of the prompt.
 *
 * Most runs need none, so it starts as a small button and opens into a textarea
 * on demand. It stays open while it holds text, and folds back when it is left
 * empty: on blur, or when the row resets the value (another question picked).
 */
export function AiNoteInput({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  const lang = useLang((s) => s.lang);
  const [expanded, setExpanded] = useState(false);
  const ref = useRef<HTMLTextAreaElement | null>(null);

  useEffect(() => {
    if (value === '' && document.activeElement !== ref.current) setExpanded(false);
  }, [value]);

  if (!expanded && value === '') {
    return (
      <button type="button" className="ai-note-toggle" title={ui('aiNoteHint', lang)} onClick={() => setExpanded(true)}>
        💬 {ui('aiNoteOpen', lang)}
      </button>
    );
  }
  return (
    <textarea
      ref={ref}
      className="ai-note"
      rows={2}
      autoFocus={expanded && value === ''}
      placeholder={ui('aiNotePlaceholder', lang)}
      title={ui('aiNoteHint', lang)}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      onBlur={(e) => {
        // Folding while a button is being pressed would move that button from
        // under the pointer, and the click would land on whatever took its place.
        if (value.trim() === '' && !(e.relatedTarget instanceof HTMLButtonElement)) setExpanded(false);
      }}
    />
  );
}
