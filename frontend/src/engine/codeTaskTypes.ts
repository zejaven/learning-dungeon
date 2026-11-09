import type { Localized } from '../i18n';
import type { TestResult } from './traceTypes';

/** A code task bound to one learning atom. Harness and wrong solutions stay on the server. */
export interface CodeTask {
  id: string;
  atomId: string;
  order: number;
  title: Localized;
  statement: Localized;
  hints: Localized[];
  debrief: Localized;
  starter: string;
  reference: string;
}

/** `attempts` counts runs that reached the tests, so a compile error spends no hint. */
export interface TaskProgress {
  attempts: number;
  solved: boolean;
}

export interface TaskListItem {
  task: CodeTask;
  atomTitle: Localized;
  atomSummary: Localized;
  progress: TaskProgress;
}

export interface TaskRunResult {
  tests: TestResult[];
  /** Cases the harness announced but never reported: the run died first. */
  notRun: string[];
  error: string | null;
  output: string;
  solved: boolean;
}

export interface TaskRunResponse {
  result: TaskRunResult;
  progress: TaskProgress;
  /** The topic is passed after this run; tasks are one of its gates. */
  topicCompleted: boolean;
}
