import { create } from 'zustand';
import { fetchProgress, fetchTopic, fetchTopics, runCode, saveMissions } from './api';
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
  /** Boss-fight evaluation results, keyed by stable question id. */
  bossFightResults: Record<string, BossFightResult>;
  /** Whether the current topic is fully completed (all questions passed). */
  topicCompleted: boolean;
  /** Drives the celebration overlay when a topic is finished. */
  celebrating: boolean;

  loadTopics: () => Promise<void>;
  selectTopic: (id: string) => Promise<void>;
  setCode: (code: string) => void;
  loadExample: (exampleId: string) => void;
  resetCode: () => void;
  run: () => Promise<void>;
  setStep: (index: number) => void;
  stepNext: () => void;
  stepPrev: () => void;
  setBossFightResult: (questionId: string, result: BossFightResult) => void;
  markTopicCompleted: () => void;
  setCelebrating: (value: boolean) => void;
}

/** One graded boss-fight answer; `passed` is score >= 6. */
export interface BossFightResult {
  answer: string;
  evaluation: string;
  score: number | null;
  passed: boolean;
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
  bossFightResults: {},
  topicCompleted: false,
  celebrating: false,

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
        bossFightResults: {},
        topicCompleted: false,
        celebrating: false,
      });
      // Restore saved progress (best-effort; never blocks opening the topic).
      try {
        const progress = await fetchProgress(id);
        if (get().topic?.id !== id) return; // a newer topic was selected meanwhile
        const bossFightResults: Record<string, BossFightResult> = {};
        for (const [questionId, a] of Object.entries(progress.bossFight)) {
          bossFightResults[questionId] = {
            answer: a.answer,
            evaluation: a.verdict ?? '',
            score: a.score,
            passed: a.passed,
          };
        }
        set({
          completedMissions: { ...progress.missions },
          bossFightResults,
          topicCompleted: progress.completed,
        });
      } catch {
        /* progress is optional; ignore if the DB is unavailable */
      }
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
      const newlyCompleted: string[] = [];
      for (const mission of topic.missions) {
        if (result.traceEvents.some((ev) => ev.event === mission.event) && !completed[mission.id]) {
          completed[mission.id] = true;
          newlyCompleted.push(mission.id);
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
      if (newlyCompleted.length > 0) {
        saveMissions(topic.id, newlyCompleted).catch(() => {
          /* persistence is best-effort */
        });
      }
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

  setBossFightResult(questionId, result) {
    set({ bossFightResults: { ...get().bossFightResults, [questionId]: result } });
  },

  markTopicCompleted() {
    const id = get().topic?.id;
    set({
      topicCompleted: true,
      celebrating: true,
      topics: id
        ? get().topics.map((t) => (t.id === id ? { ...t, completed: true } : t))
        : get().topics,
    });
  },

  setCelebrating(value) {
    set({ celebrating: value });
  },
}));
