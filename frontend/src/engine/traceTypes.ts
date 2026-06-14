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
}

/** Props every topic visualizer receives. */
export interface VisualizerProps {
  /** The event for the current playback step, or null before any run. */
  event: TraceEvent | null;
}
