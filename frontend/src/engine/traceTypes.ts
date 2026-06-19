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
  /** Catalog category id this topic belongs to (for the home tree). */
  categoryId: string;
  /** Display name for a newly-invented category; '' for known categories. */
  categoryName: string;
  /** Difficulty 1-3 (Junior/Middle/Senior); 0 if unset. */
  difficulty: number;
  /** Originating catalog question id, if generated from one; '' otherwise. */
  catalogId: string;
  /** 'trace' | 'structural' | 'theory'. */
  mode: string;
  /** AI provider that generated this topic, e.g. claude/codex/kimi. */
  aiProvider: string;
  /** Model name used for generation, if known. */
  aiModel: string;
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
  /** Trace event type whose presence completes this mission (trace topics). */
  event: string;
  /** 'event' (default) or 'structure' (checked against the class graph). */
  type: string;
  /** Structural predicates evaluated against the class graph; raw rule maps. */
  requires: StructureRule[];
}

/** One predicate of a structure mission (see engine/structure.ts). */
export interface StructureRule {
  kind: string;
  [key: string]: unknown;
}

/** A file of a structural topic's project (path + content). */
export interface ProjectFile {
  path: string;
  content: string;
}

/** A declared type in the analyzed class graph. */
export interface ClassNode {
  name: string;
  /** class | interface | abstractClass | enum */
  kind: string;
}

/** A relationship edge: extends | implements | association. */
export interface ClassEdge {
  from: string;
  to: string;
  kind: string;
}

export interface ClassGraph {
  nodes: ClassNode[];
  edges: ClassEdge[];
}

export interface AnalyzeResult {
  ok: boolean;
  error: string | null;
  graph: ClassGraph;
}

/** Result table of a SQL query (or an error). */
export interface SqlRunResult {
  columns: string[];
  rows: string[][];
  error: string | null;
}

/** SQL endpoint response: the result table plus per-mission pass flags. */
export interface SqlRunResponse extends SqlRunResult {
  missions: Record<string, boolean>;
}

/** One test case result for a challenge topic. */
export interface TestResult {
  name: string;
  passed: boolean;
  expected: string;
  actual: string;
}

/** Challenge endpoint response: test cases, error, and per-mission pass flags. */
export interface ChallengeResponse {
  tests: TestResult[];
  error: string | null;
  missions: Record<string, boolean>;
}

/** A hand-added catalog question (category chosen by the AI). */
export interface ManualQuestion {
  /** Catalog entry id, of the form `manual-<rowId>`. */
  id: string;
  categoryId: string;
  categoryName: string | null;
  difficulty: number;
  question: Localized;
}

/** One theory version of a topic (v1 = on-disk; 2+ = restyled regenerations). */
export interface TheoryVersion {
  versionNo: number;
  style: string;
  en: string;
  ru: string;
  createdAt: string | null;
  aiProvider: string;
  aiModel: string;
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
  /** 'trace' (default behavioural engine) or 'structural' (class-graph engine). */
  mode: string;
  /** Seed files for a structural topic's editor; empty for trace topics. */
  starterFiles: ProjectFile[];
  /** Explanation style recorded in topic.yaml. */
  style: string;
  /** AI provider that generated this topic, e.g. claude/codex/kimi. */
  aiProvider: string;
  /** Model name used for generation, if known. */
  aiModel: string;
}

/** Props every topic visualizer receives. */
export interface VisualizerProps {
  /** The event for the current playback step, or null before any run. */
  event: TraceEvent | null;
}
