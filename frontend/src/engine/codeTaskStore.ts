import { create } from 'zustand';
import { fetchCodeTasks, runCodeTask } from './api';
import type { TaskListItem, TaskRunResult } from './codeTaskTypes';
import { useStore } from './store';

/**
 * Code tasks of the open topic. Running is online-only and never goes through
 * the outbox: the UI needs the response, so a failure has to be loud.
 */
interface CodeTaskState {
  topicId: string | null;
  items: TaskListItem[] | null;
  loadError: string | null;
  running: boolean;
  /** The last run per task id, for this session. */
  runs: Record<string, TaskRunResult>;
  runError: string | null;
  load: (topicId: string) => Promise<void>;
  run: (taskId: string, code: string) => Promise<void>;
}

export const useCodeTasks = create<CodeTaskState>((set, get) => ({
  topicId: null,
  items: null,
  loadError: null,
  running: false,
  runs: {},
  runError: null,

  async load(topicId) {
    if (get().topicId !== topicId) {
      set({ topicId, items: null, runs: {}, runError: null });
    }
    set({ loadError: null });
    try {
      const items = await fetchCodeTasks(topicId);
      if (get().topicId === topicId) set({ items });
    } catch (e) {
      if (get().topicId === topicId) set({ loadError: (e as Error).message });
    }
  },

  async run(taskId, code) {
    const topicId = get().topicId;
    if (!topicId) return;
    set({ running: true, runError: null });
    try {
      const wasCompleted = useStore.getState().topicCompleted;
      const { result, progress, topicCompleted } = await runCodeTask(topicId, taskId, code);
      if (get().topicId !== topicId) return; // another topic was opened meanwhile
      // Whoever closes the topic last celebrates it: here, the final task.
      if (topicCompleted && !wasCompleted && useStore.getState().topic?.id === topicId) {
        useStore.getState().markTopicCompleted();
        useStore.getState().celebrateTopic();
      }
      set((s) => ({
        running: false,
        runs: { ...s.runs, [taskId]: result },
        items: s.items?.map((i) => (i.task.id === taskId ? { ...i, progress } : i)) ?? null,
      }));
    } catch (e) {
      if (get().topicId !== topicId) return;
      set({ running: false, runError: (e as Error).message });
    }
  },
}));

function draftKey(topicId: string, taskId: string): string {
  return `codetask:${topicId}:${taskId}`;
}

/** The learner's unsaved code; a convenience, so storage may fail without harm. */
export function readDraft(topicId: string, taskId: string): string | null {
  try {
    return localStorage.getItem(draftKey(topicId, taskId));
  } catch {
    return null;
  }
}

export function writeDraft(topicId: string, taskId: string, code: string): void {
  try {
    localStorage.setItem(draftKey(topicId, taskId), code);
  } catch {
    /* the draft just will not survive a reload */
  }
}

export function clearDraft(topicId: string, taskId: string): void {
  try {
    localStorage.removeItem(draftKey(topicId, taskId));
  } catch {
    /* nothing to clear */
  }
}
