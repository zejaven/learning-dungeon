import type { Lang } from '../i18n';
import type { AnswerValue, FillBlankExercise, LocalizedList } from './lessonTypes';
import type { Exercise } from './lessonTypes';

/** Accepted answers per blank, from `blanks` or the legacy single-blank `answers`. */
export function fillBlanks(exercise: FillBlankExercise): LocalizedList[] {
  if (exercise.blanks && exercise.blanks.length > 0) return exercise.blanks;
  return exercise.answers ? [exercise.answers] : [];
}

/** Typed answers per blank, tolerating the legacy single-string `value` shape. */
export function textValues(answer: AnswerValue): string[] {
  if (answer.kind !== 'text') return [];
  if (answer.values) return answer.values;
  return answer.value != null ? [answer.value] : [];
}

/**
 * Deterministic client-side grading for micro-exercises. Shared by the lesson
 * flow and the global review screen; the backend trusts the computed flag (it
 * only persists results — same trust model as trace missions).
 */
export function grade(exercise: Exercise, answer: AnswerValue, lang: Lang): boolean {
  switch (exercise.type) {
    case 'multiple_choice':
    case 'predict_output':
    case 'spot_bug': {
      if (answer.kind !== 'option') return false;
      const chosen = exercise.options.find((o) => o.id === answer.optionId);
      return !!chosen?.correct;
    }
    case 'true_false':
      return answer.kind === 'bool' && answer.value === exercise.answer;
    case 'fill_blank': {
      if (answer.kind !== 'text') return false;
      const blanks = fillBlanks(exercise);
      const typed = textValues(answer);
      if (blanks.length === 0 || typed.length !== blanks.length) return false;
      // Technical tokens are usually identical in both languages; accepting
      // either list is deliberately forgiving.
      return blanks.every((b, i) => {
        const accepted = [...(b[lang] ?? []), ...(b.en ?? []), ...(b.ru ?? [])].map(normalize);
        return accepted.includes(normalize(typed[i] ?? ''));
      });
    }
    case 'word_bank': {
      if (answer.kind !== 'tokens') return false;
      const correct = exercise.tokens[lang] ?? exercise.tokens.en ?? [];
      return answer.tokens.length === correct.length
        && answer.tokens.every((t, i) => t === correct[i]);
    }
    case 'sort_steps': {
      if (answer.kind !== 'order') return false;
      const correct = exercise.steps.map((s) => s.id);
      return answer.ids.length === correct.length
        && answer.ids.every((id, i) => id === correct[i]);
    }
    case 'match_pairs': {
      if (answer.kind !== 'pairs') return false;
      return exercise.pairs.every((p) => answer.matches[p.id] === p.id);
    }
    default:
      return false;
  }
}

function normalize(s: string): string {
  return s.trim().toLowerCase();
}

/** Deterministic-ish shuffle for exercise options/tokens (Fisher-Yates). */
export function shuffled<T>(items: T[]): T[] {
  const out = [...items];
  for (let i = out.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [out[i], out[j]] = [out[j], out[i]];
  }
  return out;
}
