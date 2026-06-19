import { create } from 'zustand';
import {
  addQuestion,
  analyzeProject,
  fetchProgress,
  fetchQuestions,
  fetchTopic,
  fetchTopics,
  fetchVersions,
  regenerateVersion,
  runChallenge,
  runCode,
  runSqlQuery,
  saveMissions,
} from './api';
import { useAi } from './aiStore';
import { evaluateStructureMission } from './structure';
import type {
  ClassGraph,
  ManualQuestion,
  SqlRunResult,
  TestResult,
  TheoryVersion,
  TopicDetail,
  TopicSummary,
  TraceEvent,
} from './traceTypes';

interface AppState {
  topics: TopicSummary[];
  topicsError: string | null;
  /** Hand-added catalog questions, merged into the tree via buildCatalog. */
  manualQuestions: ManualQuestion[];

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

  // --- Structural (design-pattern) topics: a multi-file project + class graph ---
  /** Virtual filesystem (path → content) for the current structural topic. */
  files: Record<string, string>;
  /** Path of the file open in the editor, or null. */
  activePath: string | null;
  /** Latest analyzed class graph, or null before the first analysis. */
  graph: ClassGraph | null;
  analyzing: boolean;
  analyzeError: string | null;

  // --- SQL topics: a query + its result table ---
  sqlQuery: string;
  sqlResult: SqlRunResult | null;
  runningSql: boolean;

  // --- Challenge topics: the solution editor reuses `code`; this is its tests ---
  testResults: TestResult[] | null;
  runningTests: boolean;

  // --- Theory versions (styled regenerations) of the current topic ---
  theoryVersions: TheoryVersion[];
  activeVersionNo: number;
  generatingVersion: boolean;

  loadTopics: () => Promise<void>;
  loadQuestions: () => Promise<void>;
  addManualQuestion: (text: string) => Promise<ManualQuestion>;
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

  selectFile: (path: string) => void;
  setFileContent: (path: string, content: string) => void;
  createFile: (path: string) => void;
  deleteFile: (path: string) => void;
  renameFile: (oldPath: string, newPath: string) => void;
  analyze: () => Promise<void>;

  setSqlQuery: (sql: string) => void;
  runSql: () => Promise<void>;

  runTests: () => Promise<void>;

  loadVersions: (topicId: string) => Promise<void>;
  setActiveVersion: (versionNo: number) => void;
  regenerateTheory: (style: string, styleName: string) => Promise<void>;
}

function persistActiveVersion(topicId: string | undefined, versionNo: number): void {
  if (!topicId) return;
  try {
    localStorage.setItem(`theoryVersion:${topicId}`, String(versionNo));
  } catch {
    /* ignore */
  }
}

function loadActiveVersion(topicId: string): number | null {
  try {
    const raw = localStorage.getItem(`theoryVersion:${topicId}`);
    return raw ? Number(raw) : null;
  } catch {
    return null;
  }
}

/** Persists a structural topic's project to localStorage (survives reload). */
function persistProject(topicId: string | undefined, files: Record<string, string>): void {
  if (!topicId) return;
  try {
    localStorage.setItem(`project:${topicId}`, JSON.stringify(files));
  } catch {
    /* storage may be unavailable */
  }
}

function loadProject(topicId: string): Record<string, string> | null {
  try {
    const raw = localStorage.getItem(`project:${topicId}`);
    return raw ? (JSON.parse(raw) as Record<string, string>) : null;
  } catch {
    return null;
  }
}

function seedFiles(topic: TopicDetail): Record<string, string> {
  const out: Record<string, string> = {};
  for (const f of topic.starterFiles ?? []) out[f.path] = f.content;
  return out;
}

/** Skeleton inserted when the user creates a new .java file. */
function javaTemplate(path: string): string {
  const base = path.split('/').pop() ?? 'NewClass.java';
  const name = base.replace(/\.java$/, '') || 'NewClass';
  return `public class ${name} {\n}\n`;
}

/** The opening query for a SQL topic (from its starter/query.sql). */
function seedSqlQuery(topic: TopicDetail): string {
  const q = (topic.starterFiles ?? []).find((f) => f.path.endsWith('query.sql'));
  return q?.content ?? '';
}

function persistSql(topicId: string | undefined, sql: string): void {
  if (!topicId) return;
  try {
    localStorage.setItem(`sql:${topicId}`, sql);
  } catch {
    /* storage may be unavailable */
  }
}

function loadSql(topicId: string): string | null {
  try {
    return localStorage.getItem(`sql:${topicId}`);
  } catch {
    return null;
  }
}

/** The editable Solution.java for a challenge topic (from its starter/). */
function seedSolution(topic: TopicDetail): string {
  const f = (topic.starterFiles ?? []).find((file) => file.path.endsWith('Solution.java'));
  return f?.content ?? '';
}

function persistChallenge(topicId: string | undefined, code: string): void {
  if (!topicId) return;
  try {
    localStorage.setItem(`challenge:${topicId}`, code);
  } catch {
    /* storage may be unavailable */
  }
}

function loadChallenge(topicId: string): string | null {
  try {
    return localStorage.getItem(`challenge:${topicId}`);
  } catch {
    return null;
  }
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
  manualQuestions: [],
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
  files: {},
  activePath: null,
  graph: null,
  analyzing: false,
  analyzeError: null,
  sqlQuery: '',
  sqlResult: null,
  runningSql: false,
  testResults: null,
  runningTests: false,
  theoryVersions: [],
  activeVersionNo: 1,
  generatingVersion: false,

  async loadTopics() {
    // Loads the list of generated topics (used for completion flags and to know
    // which catalog entries already have theory). The app opens on the home
    // screen, so no topic is auto-selected.
    try {
      const topics = await fetchTopics();
      set({ topics, topicsError: null });
    } catch (e) {
      set({ topicsError: (e as Error).message });
    }
  },

  async loadQuestions() {
    const manualQuestions = await fetchQuestions();
    set({ manualQuestions });
  },

  async addManualQuestion(text) {
    const q = await addQuestion(text, useAi.getState().selectedProvider);
    set({ manualQuestions: [...get().manualQuestions, q] });
    return q;
  },

  async selectTopic(id) {
    set({ loadingTopic: true });
    try {
      const topic = await fetchTopic(id);
      const structural = topic.mode === 'structural';
      const files = structural ? (loadProject(id) ?? seedFiles(topic)) : {};
      const sqlQuery = topic.mode === 'sql' ? (loadSql(id) ?? seedSqlQuery(topic)) : '';
      // The challenge editor reuses `code`; seed it from the saved/starter Solution.
      const code =
        topic.mode === 'challenge' ? (loadChallenge(id) ?? seedSolution(topic)) : defaultCodeFor(topic);
      set({
        topic,
        loadingTopic: false,
        code,
        output: '',
        runError: null,
        events: [],
        stepIndex: -1,
        completedMissions: {},
        bossFightResults: {},
        topicCompleted: false,
        celebrating: false,
        files,
        activePath: structural ? (Object.keys(files)[0] ?? null) : null,
        graph: null,
        analyzing: false,
        analyzeError: null,
        sqlQuery,
        sqlResult: null,
        runningSql: false,
        testResults: null,
        runningTests: false,
        theoryVersions: [],
        activeVersionNo: 1,
        generatingVersion: false,
      });
      // Load theory versions (best-effort) so the version dropdown is populated.
      get().loadVersions(id);
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
    // Challenge solutions are persisted so work survives a reload.
    if (get().topic?.mode === 'challenge') {
      persistChallenge(get().topic?.id, code);
    }
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

  selectFile(path) {
    set({ activePath: path });
  },

  setFileContent(path, content) {
    const files = { ...get().files, [path]: content };
    set({ files });
    persistProject(get().topic?.id, files);
  },

  createFile(path) {
    const clean = path.trim();
    if (!clean || get().files[clean] !== undefined) return;
    const content = clean.endsWith('.java') ? javaTemplate(clean) : '';
    const files = { ...get().files, [clean]: content };
    set({ files, activePath: clean });
    persistProject(get().topic?.id, files);
  },

  deleteFile(path) {
    const files = { ...get().files };
    if (files[path] === undefined) return;
    delete files[path];
    const activePath = get().activePath === path ? (Object.keys(files)[0] ?? null) : get().activePath;
    set({ files, activePath });
    persistProject(get().topic?.id, files);
  },

  renameFile(oldPath, newPath) {
    const clean = newPath.trim();
    const files = { ...get().files };
    if (!clean || files[oldPath] === undefined || files[clean] !== undefined) return;
    files[clean] = files[oldPath];
    delete files[oldPath];
    const activePath = get().activePath === oldPath ? clean : get().activePath;
    set({ files, activePath });
    persistProject(get().topic?.id, files);
  },

  async analyze() {
    const { topic, files } = get();
    if (!topic || topic.mode !== 'structural') return;
    set({ analyzing: true, analyzeError: null });
    try {
      const projectFiles = Object.entries(files).map(([path, content]) => ({ path, content }));
      const res = await analyzeProject(topic.id, projectFiles);
      // Re-check structure missions against the fresh graph (sticky, like trace
      // missions: once achieved they stay completed and are persisted).
      const completed = { ...get().completedMissions };
      const newly: string[] = [];
      for (const m of topic.missions) {
        if (m.type === 'structure' && !completed[m.id] && evaluateStructureMission(m.requires, res.graph)) {
          completed[m.id] = true;
          newly.push(m.id);
        }
      }
      set({
        analyzing: false,
        graph: res.graph,
        analyzeError: res.ok ? null : res.error,
        completedMissions: completed,
      });
      if (newly.length > 0) {
        saveMissions(topic.id, newly).catch(() => {
          /* persistence is best-effort */
        });
      }
    } catch (e) {
      set({ analyzing: false, analyzeError: (e as Error).message });
    }
  },

  setSqlQuery(sql) {
    set({ sqlQuery: sql });
    persistSql(get().topic?.id, sql);
  },

  async runSql() {
    const { topic, sqlQuery } = get();
    if (!topic || topic.mode !== 'sql') return;
    set({ runningSql: true });
    try {
      const res = await runSqlQuery(topic.id, sqlQuery);
      // Mission flags come from the server (it compares to each expectedSql).
      const completed = { ...get().completedMissions };
      const newly: string[] = [];
      for (const [id, pass] of Object.entries(res.missions)) {
        if (pass && !completed[id]) {
          completed[id] = true;
          newly.push(id);
        }
      }
      set({
        runningSql: false,
        sqlResult: { columns: res.columns, rows: res.rows, error: res.error },
        completedMissions: completed,
      });
      if (newly.length > 0) {
        saveMissions(topic.id, newly).catch(() => {
          /* persistence is best-effort */
        });
      }
    } catch (e) {
      set({ runningSql: false, sqlResult: { columns: [], rows: [], error: (e as Error).message } });
    }
  },

  async runTests() {
    const { topic, code } = get();
    if (!topic || topic.mode !== 'challenge') return;
    set({ runningTests: true });
    try {
      const res = await runChallenge(topic.id, code);
      const completed = { ...get().completedMissions };
      const newly: string[] = [];
      for (const [id, pass] of Object.entries(res.missions)) {
        if (pass && !completed[id]) {
          completed[id] = true;
          newly.push(id);
        }
      }
      // A compile/runtime error is surfaced as a single failed "case".
      const tests: TestResult[] = res.error
        ? [{ name: 'compile / run', passed: false, expected: '', actual: res.error }]
        : res.tests;
      set({ runningTests: false, testResults: tests, completedMissions: completed });
      if (newly.length > 0) {
        saveMissions(topic.id, newly).catch(() => {
          /* persistence is best-effort */
        });
      }
    } catch (e) {
      set({
        runningTests: false,
        testResults: [{ name: 'error', passed: false, expected: '', actual: (e as Error).message }],
      });
    }
  },

  async loadVersions(topicId) {
    try {
      const versions = await fetchVersions(topicId);
      if (get().topic?.id !== topicId || versions.length === 0) return; // stale or none
      const saved = loadActiveVersion(topicId);
      const max = Math.max(...versions.map((v) => v.versionNo));
      const active = saved && versions.some((v) => v.versionNo === saved) ? saved : max;
      set({ theoryVersions: versions, activeVersionNo: active });
    } catch {
      /* versions are a nice-to-have */
    }
  },

  setActiveVersion(versionNo) {
    set({ activeVersionNo: versionNo });
    persistActiveVersion(get().topic?.id, versionNo);
  },

  async regenerateTheory(style, styleName) {
    const topic = get().topic;
    if (!topic) return;
    set({ generatingVersion: true });
    try {
      const v = await regenerateVersion(topic.id, style, styleName, useAi.getState().selectedProvider);
      if (get().topic?.id !== topic.id) return; // switched away meanwhile
      const versions = [...get().theoryVersions, v];
      set({ theoryVersions: versions, activeVersionNo: v.versionNo, generatingVersion: false });
      persistActiveVersion(topic.id, v.versionNo);
    } catch (e) {
      set({ generatingVersion: false, runError: (e as Error).message });
    }
  },
}));
