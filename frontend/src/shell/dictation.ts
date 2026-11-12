import { useCallback, type Dispatch, type RefObject, type SetStateAction } from 'react';

/**
 * Puts dictated text where the caret is (replacing a selection), adding a
 * space only where the text would otherwise touch a word. Line breaks around
 * the caret are kept: dictating under a quote must not glue onto the quote.
 *
 * @returns the new value and the caret position right after the inserted text
 */
export function insertDictation(
  value: string,
  start: number,
  end: number,
  text: string,
): { value: string; caret: number } {
  const from = Math.max(0, Math.min(start, value.length));
  const to = Math.max(from, Math.min(end, value.length));
  const before = value.slice(0, from);
  const after = value.slice(to);
  const lead = before && !/\s$/.test(before) ? ' ' : '';
  const trail = after && !/^\s/.test(after) ? ' ' : '';
  const inserted = `${lead}${text}${trail}`;
  return { value: before + inserted + after, caret: before.length + lead.length + text.length };
}

/**
 * The `onText` of a `VoiceInputButton` that writes into a textarea at its caret.
 * The textarea keeps its selection after losing focus to the Speak button, so
 * the position read at the end of the recording is where the user left it.
 */
export function useDictationInsert(
  ref: RefObject<HTMLTextAreaElement | null>,
  setValue: Dispatch<SetStateAction<string>>,
) {
  return useCallback(
    (text: string) => {
      const el = ref.current;
      let caret = -1;
      setValue((current) => {
        // Without the element (unmounted mid-transcription) fall back to the end.
        const start = el ? el.selectionStart : current.length;
        const end = el ? el.selectionEnd : current.length;
        const next = insertDictation(current, start, end, text);
        caret = next.caret;
        return next.value;
      });
      // React rewrites the value, which moves the caret to the end; put it back
      // after the inserted text so the next dictation continues from there.
      requestAnimationFrame(() => {
        const node = ref.current;
        if (node && caret >= 0) node.setSelectionRange(caret, caret);
      });
    },
    [ref, setValue],
  );
}
