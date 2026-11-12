import { ui, useLang } from '@app/i18n';

/**
 * The optional "note for the AI" of a generation row: a focus, things to skip,
 * a case to add. It travels as `note` (topic, theory version) or `comment`
 * (lesson) and lands at the end of the prompt.
 */
export function AiNoteInput({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  const lang = useLang((s) => s.lang);
  return (
    <textarea
      className="ai-note"
      rows={2}
      placeholder={ui('aiNotePlaceholder', lang)}
      title={ui('aiNoteHint', lang)}
      value={value}
      onChange={(e) => onChange(e.target.value)}
    />
  );
}
