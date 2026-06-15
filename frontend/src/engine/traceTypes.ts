import type { Localized } from '../i18n';

/** A single execution trace event emitted by an instrumented visual.* model. */
export interface TraceEvent {
  step: number;
  event: string;
  /** Bilingual description shown in the event log. */
  description: Localized;
  highlight: string[];
  /** Topic-specific snapshot; each visualizer narrows this to its own shape. */
  state: unknown;
}

export interface RunResult {
  success: boolean;
  output: string;
  traceEvents: TraceEvent[];
  error: string | null;
}

export interface TopicSummary {
  id: string;
  title: Localized;
  category: Localized;
  type: string;
  summary: Localized;
  /** True once every boss-fight question has been passed. */
  completed: boolean;
}

/** Persisted progress for a topic, restored on load. */
export interface TopicProgress {
  missions: Record<string, boolean>;
  bossFight: Record<string, BossAnswer>;
  completed: boolean;
}

export interface BossAnswer {
  questionId: string;
  answer: string;
  verdict: string | null;
  score: number | null;
  passed: boolean;
}

/** One boss-fight question with a stable id (answers are keyed by it). */
export interface BossQuestion {
  id: string;
  text: Localized;
}

export interface Example {
  id: string;
  title: Localized;
  code: string;
  explanation: Localized;
}

export interface Mission {
  id: string;
  title: Localized;
  goal: Localized;
  /** Trace event type whose presence completes this mission. */
  event: string;
}

export interface TopicDetail {
  id: string;
  title: Localized;
  category: Localized;
  type: string;
  summary: Localized;
  primitives: string[];
  explanation: Localized;
  examples: Example[];
  defaultExampleId: string;
  missions: Mission[];
  /** Example question pre-filled as the Ask AI placeholder; may be empty. */
  assistantExample: Localized;
  /** Interview questions for the Boss Fight practice mode. */
  bossFight: BossQuestion[];
}

/** Props every topic visualizer receives. */
export interface VisualizerProps {
  /** The event for the current playback step, or null before any run. */
  event: TraceEvent | null;
}
