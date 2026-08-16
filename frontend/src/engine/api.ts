import type {
  AnalyzeResult,
  ChallengeResponse,
  ManualQuestion,
  ProjectFile,
  RunResult,
  SqlRunResponse,
  TheoryVersion,
  TopicDetail,
  TopicProgress,
  TopicSummary,
} from './traceTypes';
import type { AiProviderStatus } from './aiStore';
import type { Localized } from '../i18n';
import type {
  AnswerValue,
  LearningAtoms,
  LessonState,
  ReviewItem,
  ReviewSummary,
  ReviewTopic,
} from './lessonTypes';

export interface AiProvidersResponse {
  defaultProvider: string;
  providers: AiProviderStatus[];
}

export async function fetchAiProviders(): Promise<AiProvidersResponse> {
  const res = await fetch('/api/ai/providers');
  if (!res.ok) throw new Error(`Failed to load AI providers (${res.status})`);
  return res.json();
}

export async function fetchTopics(): Promise<TopicSummary[]> {
  const res = await fetch('/api/topics');
  if (!res.ok) throw new Error(`Failed to load topics (${res.status})`);
  return res.json();
}

/** App self-update/restart capability + current commits-behind (settings gear). */
export interface SystemStatus {
  /** Running under the tray launcher, so it can rebuild+restart itself. */
  supervised: boolean;
  /** Source tree present next to the app, so a rebuild is possible. */
  canRebuild: boolean;
  /** A git checkout with an upstream, so "Update" can pull from GitHub. */
  canPull: boolean;
  /** Commits the upstream is ahead of local; -1 when unknown. */
  behind: number;
  /** Short HEAD sha (informational). */
  version: string;
  /** Unique per process start; a change means the server restarted. */
  bootId: string;
}

export async function fetchSystemStatus(): Promise<SystemStatus> {
  const res = await fetch('/api/system/status');
  if (!res.ok) throw new Error(`Failed to load system status (${res.status})`);
  return res.json();
}

/** Triggers a rebuild+restart. pull=true also pulls from GitHub first. Returns 202. */
export async function postSystemUpdate(pull: boolean): Promise<void> {
  const res = await fetch('/api/system/update', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ pull }),
  });
  if (!res.ok) throw new Error(`Update request refused (${res.status})`);
}

/** Hand-added catalog questions, merged into the tree by buildCatalog. */
export async function fetchQuestions(): Promise<ManualQuestion[]> {
  try {
    const res = await fetch('/api/questions');
    return res.ok ? res.json() : [];
  } catch {
    return [];
  }
}

/** Adds a question; the AI classifies its category/difficulty and translates it. */
export async function addQuestion(text: string, provider: string): Promise<ManualQuestion> {
  const res = await fetch('/api/questions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ text, provider }),
  });
  if (!res.ok) throw new Error(await res.text().catch(() => `Add failed (${res.status})`));
  return res.json();
}

export async function deleteQuestion(id: string): Promise<void> {
  await fetch(`/api/questions/${encodeURIComponent(id)}`, { method: 'DELETE' });
}

export async function fetchTopic(id: string): Promise<TopicDetail> {
  const res = await fetch(`/api/topics/${encodeURIComponent(id)}`);
  if (!res.ok) throw new Error(`Failed to load topic '${id}' (${res.status})`);
  return res.json();
}

export async function runCode(topicId: string, code: string): Promise<RunResult> {
  const res = await fetch('/api/run', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ topicId, code }),
  });
  if (!res.ok) throw new Error(`Run failed (${res.status})`);
  return res.json();
}

export interface UsageWindow {
  utilization: number;
  resetsAt: string | null;
}

export interface UsageSnapshot {
  providerId: string;
  providerName: string;
  installed: boolean;
  downloadUrl: string;
  available: boolean;
  session: UsageWindow | null;
  weekly: UsageWindow | null;
  error: string | null;
}

/** Current selected-provider usage/status for the header meter. */
export async function fetchUsage(provider: string): Promise<UsageSnapshot> {
  const res = await fetch(`/api/usage?provider=${encodeURIComponent(provider)}`);
  if (!res.ok) throw new Error(`Failed to load usage (${res.status})`);
  return res.json();
}

/** Runs a challenge topic's solution against its hidden tests; returns results + mission flags. */
export async function runChallenge(topicId: string, code: string): Promise<ChallengeResponse> {
  const res = await fetch('/api/challenge', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ topicId, code }),
  });
  if (!res.ok) throw new Error(`Test run failed (${res.status})`);
  return res.json();
}

/** Runs a SQL topic's query against its seeded schema; returns the result + mission flags. */
export async function runSqlQuery(topicId: string, sql: string): Promise<SqlRunResponse> {
  const res = await fetch('/api/sql', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ topicId, sql }),
  });
  if (!res.ok) throw new Error(`SQL run failed (${res.status})`);
  return res.json();
}

/** Compiles (validity only) and analyzes a structural topic's project. */
export async function analyzeProject(topicId: string, files: ProjectFile[]): Promise<AnalyzeResult> {
  const res = await fetch('/api/analyze', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ topicId, files }),
  });
  if (!res.ok) throw new Error(`Analyze failed (${res.status})`);
  return res.json();
}

export async function fetchProgress(topicId: string): Promise<TopicProgress> {
  const res = await fetch(`/api/progress/${encodeURIComponent(topicId)}`);
  if (!res.ok) throw new Error(`Failed to load progress (${res.status})`);
  return res.json();
}

export async function saveMissions(topicId: string, missionIds: string[]): Promise<void> {
  await fetch(`/api/progress/${encodeURIComponent(topicId)}/missions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ missionIds }),
  });
}

export interface SaveBossAnswerPayload {
  questionId: string;
  questionText: string;
  answer: string;
  verdict: string | null;
  score: number | null;
  passed: boolean;
}

export async function saveBossAnswer(
  topicId: string,
  payload: SaveBossAnswerPayload,
): Promise<{ topicCompleted: boolean }> {
  const res = await fetch(`/api/progress/${encodeURIComponent(topicId)}/boss-fight`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  if (!res.ok) throw new Error(`Failed to save answer (${res.status})`);
  return res.json();
}

/** One saved Ask-AI question and its answer (mirrors the backend AssistantQa). */
export interface AssistantQa {
  id: number;
  question: string;
  answer: string;
  createdAt: string;
}

/** A topic's Ask-AI history, oldest first. Best-effort: returns [] on failure. */
export async function fetchAssistantHistory(topicId: string): Promise<AssistantQa[]> {
  try {
    const res = await fetch(`/api/progress/${encodeURIComponent(topicId)}/assistant`);
    return res.ok ? res.json() : [];
  } catch {
    return [];
  }
}

/** Appends one asked question + answer; returns it with its generated id. */
export async function saveAssistantQa(
  topicId: string,
  payload: { question: string; answer: string },
): Promise<AssistantQa> {
  const res = await fetch(`/api/progress/${encodeURIComponent(topicId)}/assistant`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  if (!res.ok) throw new Error(`Failed to save question (${res.status})`);
  return res.json();
}

export async function deleteAssistantQa(topicId: string, id: number): Promise<void> {
  await fetch(`/api/progress/${encodeURIComponent(topicId)}/assistant/${id}`, { method: 'DELETE' });
}

export interface SseHandlers {
  onAi?: (raw: string) => void;
  onStatus?: (status: string, message: string) => void;
  onError?: (message: string) => void;
  onDone?: () => void;
  signal?: AbortSignal;
}

/** Reads a fetch Response as an SSE stream, dispatching named events. */
async function consumeSse(res: Response, handlers: SseHandlers): Promise<void> {
  if (!res.ok || !res.body) {
    handlers.onError?.(`Stream request failed (${res.status})`);
    return;
  }
  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  const dispatch = (rawEvent: string) => {
    let name = 'message';
    const dataLines: string[] = [];
    for (const line of rawEvent.split('\n')) {
      if (line.startsWith('event:')) name = line.slice(6).trim();
      else if (line.startsWith('data:')) dataLines.push(line.slice(5).replace(/^ /, ''));
    }
    const data = dataLines.join('\n');
    if (name === 'ai' || name === 'claude') {
      handlers.onAi?.(data);
    } else if (name === 'status') {
      try {
        const parsed = JSON.parse(data);
        handlers.onStatus?.(parsed.status, parsed.message);
      } catch {
        /* ignore */
      }
    }
  };

  // eslint-disable-next-line no-constant-condition
  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    let idx;
    while ((idx = buffer.indexOf('\n\n')) >= 0) {
      const rawEvent = buffer.slice(0, idx);
      buffer = buffer.slice(idx + 2);
      if (rawEvent.trim()) dispatch(rawEvent);
    }
  }
  handlers.onDone?.();
}

/**
 * Opens a POST request that streams Server-Sent Events. The browser EventSource
 * API is GET-only, so we read the stream manually and dispatch named events.
 */
export async function streamSse(url: string, body: unknown, handlers: SseHandlers): Promise<void> {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify(body),
    signal: handlers.signal,
  });
  await consumeSse(res, handlers);
}

// --- Topic generation tasks (server-tracked, reconnectable) ---------------

export interface GenerateBody {
  question: string;
  provider?: string;
  catalogId?: string;
  categoryId?: string;
  difficulty?: number;
  /** Optional explanation-style instruction (analogy theme); '' / omitted = default. */
  style?: string;
  /** Display name of the chosen style (recorded in the topic), e.g. 'Sports'. */
  styleName?: string;
  /** Content languages to generate ('en'/'ru'); omitted = both. */
  languages?: string[];
  /** Subject area the new topic belongs to; omitted = java. */
  domainId?: string;
}

/** Lists a topic's theory versions (v1 = on-disk, 2+ = restyled regenerations). */
export async function fetchVersions(topicId: string): Promise<TheoryVersion[]> {
  const res = await fetch(`/api/topics/${encodeURIComponent(topicId)}/versions`);
  if (!res.ok) return [];
  return res.json();
}

/** Regenerates the topic's explanation in a style, storing it as a new version. */
export async function regenerateVersion(
  topicId: string,
  style: string,
  styleName: string,
  provider: string,
  languages?: string[],
): Promise<TheoryVersion> {
  const res = await fetch(`/api/topics/${encodeURIComponent(topicId)}/versions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ style, styleName, provider, languages }),
  });
  if (!res.ok) throw new Error(await res.text().catch(() => `Regenerate failed (${res.status})`));
  return res.json();
}

/** Translates an existing version into one more language, in place. */
export async function addVersionLanguage(
  topicId: string,
  versionNo: number,
  lang: string,
  provider: string,
): Promise<void> {
  const res = await fetch(
    `/api/topics/${encodeURIComponent(topicId)}/versions/${versionNo}/languages`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ lang, provider }),
    },
  );
  if (!res.ok) throw new Error(await res.text().catch(() => `Translation failed (${res.status})`));
}

export interface StyleDto {
  name: string;
  instruction: string;
}

/** User-saved generation styles (built-in presets live on the frontend). */
export async function fetchStyles(): Promise<StyleDto[]> {
  try {
    const res = await fetch('/api/styles');
    return res.ok ? res.json() : [];
  } catch {
    return [];
  }
}

export async function saveStyle(style: StyleDto): Promise<void> {
  await fetch('/api/styles', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(style),
  });
}

export async function deleteStyle(name: string): Promise<void> {
  await fetch(`/api/styles/${encodeURIComponent(name)}`, { method: 'DELETE' });
}

export interface GenTaskRef {
  taskId: string;
  key: string;
  status: string;
}

/** Starts (or reuses) a generation task; returns its id + key. */
export async function startGeneration(body: GenerateBody): Promise<GenTaskRef> {
  const res = await fetch('/api/topics/generate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`Failed to start generation (${res.status})`);
  return res.json();
}

/** Lists generation tasks still running, so the UI can reattach after a reload. */
export async function fetchActiveGenerations(): Promise<GenTaskRef[]> {
  const res = await fetch('/api/topics/generate/active');
  if (!res.ok) return [];
  return res.json();
}

/** Attaches to a task's event stream (replays its history, then live events). */
export async function streamGenerationEvents(
  taskId: string,
  handlers: SseHandlers,
): Promise<void> {
  const res = await fetch(`/api/topics/generate/${encodeURIComponent(taskId)}/events`, {
    method: 'GET',
    headers: { Accept: 'text/event-stream' },
    signal: handlers.signal,
  });
  await consumeSse(res, handlers);
}

// --- "Learn by micro-actions" lesson ---------------------------------------

/** The topic's learning atoms, or null when no lesson has been generated yet. */
export async function fetchAtoms(topicId: string): Promise<LearningAtoms | null> {
  const res = await fetch(`/api/topics/${encodeURIComponent(topicId)}/atoms`);
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(`Failed to load lesson (${res.status})`);
  return res.json();
}

export async function fetchLessonState(topicId: string): Promise<LessonState | null> {
  const res = await fetch(`/api/lesson/${encodeURIComponent(topicId)}/state`);
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(`Failed to load lesson state (${res.status})`);
  return res.json();
}

export interface ExerciseAnswerPayload {
  exerciseId: string;
  atomId: string;
  unitId: string;
  /** 'lesson' | 'review' */
  context: string;
  answer: AnswerValue;
  correct: boolean;
}

/** Best-effort append-only answer log; a failure never blocks the lesson flow. */
export async function saveExerciseAnswer(topicId: string, payload: ExerciseAnswerPayload): Promise<void> {
  try {
    await fetch(`/api/lesson/${encodeURIComponent(topicId)}/answer`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ...payload, answer: undefined, answerJson: JSON.stringify(payload.answer) }),
    });
  } catch {
    /* offline logging is not worth interrupting the lesson */
  }
}

/** Recomputes lesson completion from the persisted answers + boss passes. */
export async function recomputeLesson(topicId: string): Promise<{ lessonCompleted: boolean }> {
  const res = await fetch(`/api/lesson/${encodeURIComponent(topicId)}/recompute`, { method: 'POST' });
  if (!res.ok) throw new Error(`Failed to recompute lesson (${res.status})`);
  const body = await res.json();
  return { lessonCompleted: !!body.lessonCompleted };
}

/** Starts (or reuses) the atoms-generation task for the topic (key `atoms:<id>`). */
export async function startAtomsGeneration(
  topicId: string,
  provider: string,
  versionNo: number,
  mode: 'full' | 'augment' = 'full',
  comment = '',
  languages?: string[],
): Promise<GenTaskRef> {
  const res = await fetch(`/api/topics/${encodeURIComponent(topicId)}/atoms/generate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ provider, versionNo, mode, comment, languages }),
  });
  if (!res.ok) throw new Error(`Failed to start lesson generation (${res.status})`);
  return res.json();
}

// --- Bulk generation (backend-side loop over missing theory/lessons) --------

export type BulkKind = 'theory' | 'atoms';
export type BulkItemStatus = 'pending' | 'running' | 'done' | 'error' | 'skipped';

/** One unit of work; theory items carry the same fields startGeneration takes. */
export interface BulkItemInput {
  id: string;
  label: Localized;
  question?: string;
  catalogId?: string;
  categoryId?: string;
  difficulty?: number;
  style?: string;
  styleName?: string;
}

export interface BulkItemView {
  id: string;
  label: Localized;
  status: BulkItemStatus;
  taskKey: string | null;
}

export interface BulkRunView {
  kind: BulkKind;
  domainId: string;
  provider: string;
  /** Null in continuous mode (no deadline). */
  endTime: string | null;
  maxPercent: number;
  maxWeeklyPercent: number;
  items: BulkItemView[];
  currentIndex: number;
  phase: string;
  waitUntil: string | null;
  stopRequested: boolean;
  startedAt: string;
  finishedAt: string | null;
  utilization: number | null;
  resetsAt: string | null;
  maxDeltaObserved: number | null;
  weeklyUtilization: number | null;
  weeklyResetsAt: string | null;
}

export interface BulkStatus {
  active: boolean;
  run: BulkRunView | null;
}

export interface StartBulkBody {
  kind: BulkKind;
  provider: string;
  domainId: string;
  /**
   * Time-of-day "HH:mm"; already past today means tomorrow. Empty = continuous
   * mode: run until the items run out or the user stops, keeping every 5-hour
   * window under maxPercent.
   */
  endTime: string;
  /** Cap for the 5-hour window. */
  maxPercent: number;
  /** Cap for the 7-day window; reaching it ends the run instead of pausing. */
  maxWeeklyPercent: number;
  items: BulkItemInput[];
  /** Content languages to generate ('en'/'ru'); omitted = both. */
  languages?: string[];
}

/** Starts the bulk loop; throws with the server's message on 400/409. */
export async function startBulk(body: StartBulkBody): Promise<BulkStatus> {
  const res = await fetch('/api/bulk/start', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => null);
    throw new Error(err?.error ?? `Failed to start bulk generation (${res.status})`);
  }
  return res.json();
}

export async function fetchBulkStatus(): Promise<BulkStatus> {
  const res = await fetch('/api/bulk/status');
  if (!res.ok) throw new Error(`Failed to load bulk status (${res.status})`);
  return res.json();
}

/** Graceful stop: the in-flight generation finishes before the run ends. */
export async function stopBulk(): Promise<BulkStatus> {
  const res = await fetch('/api/bulk/stop', { method: 'POST' });
  if (!res.ok) throw new Error(`Failed to stop bulk generation (${res.status})`);
  return res.json();
}

/** Clears a finished run so the strip disappears. */
export async function dismissBulk(): Promise<void> {
  await fetch('/api/bulk/dismiss', { method: 'POST' });
}

// --- Global review ----------------------------------------------------------

export async function fetchReviewSummary(): Promise<ReviewSummary> {
  const res = await fetch('/api/review/summary');
  if (!res.ok) throw new Error(`Failed to load review summary (${res.status})`);
  return res.json();
}

/** Topics with pooled exercises, each flagged whether it participates in review. */
export async function fetchReviewTopics(): Promise<ReviewTopic[]> {
  const res = await fetch('/api/review/topics');
  if (!res.ok) throw new Error(`Failed to load review topics (${res.status})`);
  return res.json();
}

export async function setReviewTopicEnabled(topicId: string, enabled: boolean): Promise<void> {
  const res = await fetch(`/api/review/topics/${encodeURIComponent(topicId)}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ enabled }),
  });
  if (!res.ok) throw new Error(`Failed to update review topic (${res.status})`);
}

/** The current review list: pending exercises of enabled topics (client shuffles). */
export async function fetchReviewList(): Promise<ReviewItem[]> {
  const res = await fetch('/api/review/list');
  if (!res.ok) throw new Error(`Failed to load review list (${res.status})`);
  return res.json();
}

/** Records a review answer; a correct answer drops the exercise from the list. */
export async function markReviewAnswer(
  topicId: string,
  exerciseId: string,
  correct: boolean,
): Promise<void> {
  const res = await fetch('/api/review/answer', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ topicId, exerciseId, correct }),
  });
  if (!res.ok) throw new Error(`Failed to save review answer (${res.status})`);
}

/** Per-topic "start again": returns the topic's answered exercises to the list. */
export async function restartReviewTopic(topicId: string): Promise<void> {
  const res = await fetch(`/api/review/topics/${encodeURIComponent(topicId)}/restart`, {
    method: 'POST',
  });
  if (!res.ok) throw new Error(`Failed to restart topic (${res.status})`);
}

/** Global "start again": returns every answered exercise, across all topics, to the list. */
export async function restartReviewAll(): Promise<void> {
  const res = await fetch('/api/review/restart', { method: 'POST' });
  if (!res.ok) throw new Error(`Failed to restart review (${res.status})`);
}
