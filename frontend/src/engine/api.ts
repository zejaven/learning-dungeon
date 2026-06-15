import type { RunResult, TopicDetail, TopicProgress, TopicSummary } from './traceTypes';

export async function fetchTopics(): Promise<TopicSummary[]> {
  const res = await fetch('/api/topics');
  if (!res.ok) throw new Error(`Failed to load topics (${res.status})`);
  return res.json();
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

export interface SseHandlers {
  onClaude?: (raw: string) => void;
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
    if (name === 'claude') {
      handlers.onClaude?.(data);
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
  catalogId?: string;
  categoryId?: string;
  difficulty?: number;
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
