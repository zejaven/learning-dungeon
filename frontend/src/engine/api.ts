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
import type {
  AnswerValue,
  AtomsResponse,
  LessonState,
  ReviewSession,
  ReviewSummary,
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
): Promise<TheoryVersion> {
  const res = await fetch(`/api/topics/${encodeURIComponent(topicId)}/versions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ style, styleName, provider }),
  });
  if (!res.ok) throw new Error(await res.text().catch(() => `Regenerate failed (${res.status})`));
  return res.json();
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
export async function fetchAtoms(topicId: string): Promise<AtomsResponse | null> {
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
  atomsHash: string;
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

/** Marks a unit complete; 409 means the lesson was regenerated meanwhile. */
export async function completeUnit(
  topicId: string,
  unitId: string,
  atomsHash: string,
): Promise<{ lessonCompleted: boolean; stale: boolean }> {
  const res = await fetch(`/api/lesson/${encodeURIComponent(topicId)}/unit-complete`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ unitId, atomsHash }),
  });
  if (res.status === 409) return { lessonCompleted: false, stale: true };
  if (!res.ok) throw new Error(`Failed to save unit (${res.status})`);
  const body = await res.json();
  return { lessonCompleted: !!body.lessonCompleted, stale: false };
}

/** Starts (or reuses) the atoms-generation task for the topic (key `atoms:<id>`). */
export async function startAtomsGeneration(
  topicId: string,
  provider: string,
  versionNo: number,
): Promise<GenTaskRef> {
  const res = await fetch(`/api/topics/${encodeURIComponent(topicId)}/atoms/generate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ provider, versionNo }),
  });
  if (!res.ok) throw new Error(`Failed to start lesson generation (${res.status})`);
  return res.json();
}

// --- Global review ----------------------------------------------------------

export async function fetchReviewSummary(): Promise<ReviewSummary> {
  const res = await fetch('/api/review/summary');
  if (!res.ok) throw new Error(`Failed to load review summary (${res.status})`);
  return res.json();
}

/** Returns the active session if one exists, else shuffles the pool into a new one. */
export async function startReviewSession(): Promise<ReviewSession> {
  const res = await fetch('/api/review/session/start', { method: 'POST' });
  if (!res.ok) throw new Error(`Failed to start review (${res.status})`);
  return res.json();
}

export async function fetchActiveReviewSession(): Promise<ReviewSession | null> {
  const res = await fetch('/api/review/session/active');
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(`Failed to load review session (${res.status})`);
  return res.json();
}

export async function answerReview(
  sessionId: number,
  payload: { itemIndex: number; correct: boolean; answer: AnswerValue },
): Promise<{ queue: number[]; position: number; finished: boolean }> {
  const res = await fetch(`/api/review/session/${sessionId}/answer`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      itemIndex: payload.itemIndex,
      correct: payload.correct,
      answerJson: JSON.stringify(payload.answer),
    }),
  });
  if (!res.ok) throw new Error(`Failed to save review answer (${res.status})`);
  return res.json();
}

export async function abandonReviewSession(sessionId: number): Promise<void> {
  await fetch(`/api/review/session/${sessionId}/abandon`, { method: 'POST' });
}
