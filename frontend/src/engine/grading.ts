import type { Lang } from '../i18n';
import type { AnswerValue, Exercise } from './lessonTypes';

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
      const typed = normalize(answer.value);
      // Technical tokens are usually identical in both languages; accepting
      // either list is deliberately forgiving.
      const accepted = [...(exercise.answers[lang] ?? []), ...(exercise.answers.en ?? []), ...(exercise.answers.ru ?? [])];
      return accepted.some((a) => normalize(a) === typed);
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
      // Matched pairs lock in the UI, so the final mapping is always right;
      // the exercise counts as failed when any wrong match was attempted.
      if (answer.kind !== 'pairs') return false;
      const allMatched = exercise.pairs.every((p) => answer.matches[p.id] === p.id);
      return allMatched && answer.mistakes === 0;
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
