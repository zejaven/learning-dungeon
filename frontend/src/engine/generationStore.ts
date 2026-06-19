import { create } from 'zustand';
import {
  fetchActiveGenerations,
  startGeneration,
  streamGenerationEvents,
  type GenerateBody,
} from './api';
import { useAi } from './aiStore';
import { parseActivity } from './aiStream';
import { useStore } from './store';
import { ui, useLang } from '../i18n';

export type GenStatus = 'running' | 'done' | 'error' | '';

export interface GenTask {
  taskId: string;
  key: string;
  status: GenStatus;
  message: string;
  log: string[];
}

interface GenerationState {
  /** Active/finished generation tasks, keyed by task key (survives dialog close). */
  tasks: Record<string, GenTask>;
  /** Which task ids already have a live SSE subscription. */
  subscribed: Record<string, boolean>;

  start: (key: string, body: GenerateBody) => Promise<void>;
  attach: (taskId: string, key: string) => void;
  refreshActive: () => Promise<void>;
}

export const useGeneration = create<GenerationState>((set, get) => ({
  tasks: {},
  subscribed: {},

  async start(key, body) {
    const existing = get().tasks[key];
    if (existing && existing.status === 'running') {
      get().attach(existing.taskId, key); // already going — just make sure we're listening
      return;
    }
    // Fresh task slot for this key (clears any previous finished run).
    set((s) => ({
      tasks: { ...s.tasks, [key]: { taskId: '', key, status: 'running', message: '', log: [] } },
    }));
    try {
      const ref = await startGeneration({ ...body, provider: useAi.getState().selectedProvider });
      set((s) => ({
        tasks: { ...s.tasks, [key]: { ...s.tasks[key], taskId: ref.taskId, status: 'running' } },
      }));
      get().attach(ref.taskId, key);
    } catch (e) {
      const message = (e as Error).message;
      set((s) => ({
        tasks: {
          ...s.tasks,
          [key]: { ...s.tasks[key], status: 'error', message, log: [`[error] ${message}`] },
        },
      }));
    }
  },

  attach(taskId, key) {
    if (!taskId || get().subscribed[taskId]) return;
    set((s) => ({
      subscribed: { ...s.subscribed, [taskId]: true },
      tasks: {
        ...s.tasks,
        [key]: s.tasks[key] ?? { taskId, key, status: 'running', message: '', log: [] },
      },
    }));

    const appendLog = (line: string) =>
      set((s) => {
        const t = s.tasks[key];
        return t ? { tasks: { ...s.tasks, [key]: { ...t, log: [...t.log, line] } } } : {};
      });
    const setStatus = (status: GenStatus, message: string) =>
      set((s) => {
        const t = s.tasks[key];
        return t ? { tasks: { ...s.tasks, [key]: { ...t, status, message } } } : {};
      });

    streamGenerationEvents(taskId, {
      onAi: (raw) => {
        const activity = parseActivity(raw);
        if (activity) appendLog(activity);
      },
      onStatus: (status, message) => {
        setStatus(status as GenStatus, message);
        if (status === 'done') {
          appendLog(ui('genFinished', useLang.getState().lang));
          // The new topic now exists; refresh the list so the tree links it.
          useStore.getState().loadTopics();
        } else if (status === 'error') {
          appendLog(`[error] ${message}`);
        }
      },
      onDone: () => set((s) => ({ subscribed: { ...s.subscribed, [taskId]: false } })),
    }).catch(() => set((s) => ({ subscribed: { ...s.subscribed, [taskId]: false } })));
  },

  async refreshActive() {
    try {
      const active = await fetchActiveGenerations();
      for (const ref of active) {
        if (!get().tasks[ref.key]) {
          set((s) => ({
            tasks: {
              ...s.tasks,
              [ref.key]: { taskId: ref.taskId, key: ref.key, status: 'running', message: '', log: [] },
            },
          }));
        }
        get().attach(ref.taskId, ref.key);
      }
    } catch {
      /* best-effort */
    }
  },
}));
