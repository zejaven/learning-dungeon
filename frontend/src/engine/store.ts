import { create } from 'zustand';
import { fetchTopic, fetchTopics, runCode } from './api';
import type { TopicDetail, TopicSummary, TraceEvent } from './traceTypes';

interface AppState {
  topics: TopicSummary[];
  topicsError: string | null;

  topic: TopicDetail | null;
  loadingTopic: boolean;

  code: string;

  running: boolean;
  output: string;
  runError: string | null;
  events: TraceEvent[];
  stepIndex: number; // index into events; -1 when there are none
  completedMissions: Record<string, boolean>;

  loadTopics: () => Promise<void>;
  selectTopic: (id: string) => Promise<void>;
  setCode: (code: string) => void;
  loadExample: (exampleId: string) => void;
  resetCode: () => void;
  run: () => Promise<void>;
  setStep: (index: number) => void;
  stepNext: () => void;
  stepPrev: () => void;
}

function defaultCodeFor(topic: TopicDetail): string {
  const def = topic.examples.find((e) => e.id === topic.defaultExampleId);
  return (def ?? topic.examples[0])?.code ?? '';
}

export const useStore = create<AppState>((set, get) => ({
  topics: [],
  topicsError: null,
  topic: null,
  loadingTopic: false,
  code: '',
  running: false,
  output: '',
  runError: null,
  events: [],
  stepIndex: -1,
  completedMissions: {},

  async loadTopics() {
    try {
      const topics = await fetchTopics();
      set({ topics, topicsError: null });
      const current = get().topic;
      if (!current && topics.length > 0) {
        await get().selectTopic(topics[0].id);
      }
    } catch (e) {
      set({ topicsError: (e as Error).message });
    }
  },

  async selectTopic(id) {
    set({ loadingTopic: true });
    try {
      const topic = await fetchTopic(id);
      set({
        topic,
        loadingTopic: false,
        code: defaultCodeFor(topic),
        output: '',
        runError: null,
        events: [],
        stepIndex: -1,
        completedMissions: {},
      });
    } catch (e) {
      set({ loadingTopic: false, runError: (e as Error).message });
    }
  },

  setCode(code) {
    set({ code });
  },

  loadExample(exampleId) {
    const topic = get().topic;
    if (!topic) return;
    const ex = topic.examples.find((e) => e.id === exampleId);
    if (ex) set({ code: ex.code });
  },

  resetCode() {
    const topic = get().topic;
    if (topic) set({ code: defaultCodeFor(topic) });
  },

  async run() {
    const { topic, code } = get();
    if (!topic) return;
    set({ running: true, runError: null });
    try {
      const result = await runCode(topic.id, code);
      const completed = { ...get().completedMissions };
      for (const mission of topic.missions) {
        if (result.traceEvents.some((ev) => ev.event === mission.event)) {
          completed[mission.id] = true;
        }
      }
      set({
        running: false,
        output: result.output,
        runError: result.error,
        events: result.traceEvents,
        stepIndex: result.traceEvents.length - 1,
        completedMissions: completed,
      });
    } catch (e) {
      set({ running: false, runError: (e as Error).message });
    }
  },

  setStep(index) {
    const { events } = get();
    if (events.length === 0) return;
    const clamped = Math.max(0, Math.min(events.length - 1, index));
    set({ stepIndex: clamped });
  },

  stepNext() {
    get().setStep(get().stepIndex + 1);
  },

  stepPrev() {
    get().setStep(get().stepIndex - 1);
  },
}));
