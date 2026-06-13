/** A single execution trace event emitted by an instrumented visual.* model. */
export interface TraceEvent {
  step: number;
  event: string;
  description: string;
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
  title: string;
  category: string;
  type: string;
  summary: string;
}

export interface Example {
  id: string;
  title: string;
  code: string;
  explanation: string;
}

export interface Mission {
  id: string;
  title: string;
  goal: string;
  /** Trace event type whose presence completes this mission. */
  event: string;
}

export interface TopicDetail {
  id: string;
  title: string;
  category: string;
  type: string;
  summary: string;
  primitives: string[];
  explanation: string;
  examples: Example[];
  defaultExampleId: string;
  missions: Mission[];
}

/** Props every topic visualizer receives. */
export interface VisualizerProps {
  /** The event for the current playback step, or null before any run. */
  event: TraceEvent | null;
}
