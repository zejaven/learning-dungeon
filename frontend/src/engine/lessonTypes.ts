import type { Lang, Localized } from '../i18n';

/**
 * Types for the "Learn by micro-actions" lesson mode. They mirror the backend
 * LessonDtos / the learning-atoms.json schema (see prompts/generate-learning-atoms.md).
 */

/** Post-answer explanation; each side is at most 1-2 sentences of theory. */
export interface ExerciseFeedback {
  correct: Localized;
  incorrect: Localized;
}

/** One choice of a multiple-choice-style exercise. */
export interface ExerciseOption {
  id: string;
  text: Localized;
  correct: boolean;
  /** Misconception explanation for a wrong option; null/absent on the correct one. */
  feedback?: Localized | null;
}

/**
 * A per-language list of strings (fill-blank answers, word-bank tokens); a
 * language the lesson was not generated in is simply absent.
 */
export type LocalizedList = Partial<Record<Lang, string[]>>;

export interface SortStep {
  id: string;
  text: Localized;
}

export interface MatchPair {
  id: string;
  left: Localized;
  right: Localized;
}

export type ExerciseType =
  | 'multiple_choice'
  | 'true_false'
  | 'fill_blank'
  | 'word_bank'
  | 'sort_steps'
  | 'match_pairs'
  | 'predict_output'
  | 'spot_bug';

interface ExerciseBase {
  id: string;
  type: ExerciseType;
  prompt: Localized;
  /** Optional code snippet; code is never localized. */
  code?: string | null;
  codeLang?: string | null;
  /** Optional static Mermaid diagram per language (shown above the input). */
  mermaid?: Localized | null;
  /**
   * Optional teaching card shown AFTER answering (Markdown, may embed a
   * ```mermaid``` diagram). Teaches the concept; feedback stays a short verdict.
   */
  reveal?: Localized | null;
  feedback: ExerciseFeedback;
}

export interface ChoiceExercise extends ExerciseBase {
  type: 'multiple_choice' | 'predict_output' | 'spot_bug';
  options: ExerciseOption[];
}

export interface TrueFalseExercise extends ExerciseBase {
  type: 'true_false';
  answer: boolean;
}

export interface FillBlankExercise extends ExerciseBase {
  type: 'fill_blank';
  /** Statement with one or more ___ per language. */
  text: Localized;
  /** Accepted answers per blank, in order (supports multiple ___). */
  blanks?: LocalizedList[];
  /** Legacy single-blank accepted answers; use `blanks` for one or more blanks. */
  answers?: LocalizedList;
}

export interface WordBankExercise extends ExerciseBase {
  type: 'word_bank';
  /** Tokens in the correct order; the UI shuffles tokens + distractors. */
  tokens: LocalizedList;
  distractors?: LocalizedList | null;
}

export interface SortStepsExercise extends ExerciseBase {
  type: 'sort_steps';
  /** Steps in the correct order; the UI shuffles. */
  steps: SortStep[];
}

export interface MatchPairsExercise extends ExerciseBase {
  type: 'match_pairs';
  pairs: MatchPair[];
}

export type Exercise =
  | ChoiceExercise
  | TrueFalseExercise
  | FillBlankExercise
  | WordBankExercise
  | SortStepsExercise
  | MatchPairsExercise;

/** One knowledge atom: a single idea with its discovery and practice exercises. */
export interface LearningAtom {
  id: string;
  title: Localized;
  summary: Localized;
  discovery: Exercise[];
  practice: Exercise[];
  /** A capstone atom synthesizes across atoms; its practice is a final block before the boss. */
  capstone?: boolean;
}

/** The whole learning-atoms.json payload. */
export interface LearningAtoms {
  schemaVersion: number;
  topicId: string;
  sourceVersion: number;
  aiProvider: string;
  aiModel: string;
  atoms: LearningAtom[];
}

export type UnitKind = 'discovery' | 'practice' | 'capstone' | 'mistakes' | 'boss';

/** One derived lesson unit (a circle in the unit track). */
export interface LessonUnit {
  id: string;
  kind: UnitKind;
  /** Exercises of the unit; empty for boss units (the question id is in the unit id). */
  exerciseIds: string[];
}

/** One saved answer, restored when the learner revisits a unit. */
export interface SavedAnswer {
  exerciseId: string;
  correct: boolean;
  /** JSON of the AnswerValue the learner submitted. */
  answerJson: string;
}

/** Lesson progress. Unit/lesson state is derived on the client from `answers`. */
export interface LessonState {
  lessonCompleted: boolean;
  answers: SavedAnswer[];
}

/** What the learner submitted, per exercise type (persisted as JSON). */
export type AnswerValue =
  | { kind: 'option'; optionId: string }
  | { kind: 'bool'; value: boolean }
  // `values` holds one typed answer per blank; `value` is the legacy single-blank shape.
  | { kind: 'text'; values?: string[]; value?: string }
  | { kind: 'order'; ids: string[] }
  | { kind: 'tokens'; tokens: string[] }
  | { kind: 'pairs'; matches: Record<string, string> };

// --- Global review ---------------------------------------------------------

/**
 * A topic with pooled practice exercises: how many are still in the review list
 * ({@link pending}), the topic {@link total}, and whether it participates.
 */
export interface ReviewTopic {
  topicId: string;
  title: Localized;
  pending: number;
  total: number;
  enabled: boolean;
}

/** One review-list exercise, self-contained (topic title included for the header). */
export interface ReviewItem {
  topicId: string;
  topicTitle: Localized;
  atomId: string;
  exercise: Exercise;
}
