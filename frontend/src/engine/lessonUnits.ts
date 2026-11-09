import type { BossQuestion } from './traceTypes';
import type { LearningAtoms, LearningAtom, LessonUnit } from './lessonTypes';

/**
 * Derives the lesson's unit sequence (the Duolingo-style circles) from the
 * atoms file and the boss-fight questions. Units are derived, never stored.
 *
 * IMPORTANT: this is a mirror of the backend algorithm in
 * backend/.../lesson/LessonUnits.java (pinned there by LessonUnitsTest); any
 * change must be applied to both sides, or circle completion will desync from
 * the server's lesson-completion recompute.
 */

export const PRACTICE_CHUNK = 5;
const MIN_LAST_CHUNK = 3;

function chunk(flat: string[]): string[][] {
  const chunks: string[][] = [];
  for (let i = 0; i < flat.length; i += PRACTICE_CHUNK) {
    chunks.push(flat.slice(i, i + PRACTICE_CHUNK));
  }
  if (chunks.length > 1 && chunks[chunks.length - 1].length < MIN_LAST_CHUNK) {
    const tail = chunks.pop()!;
    chunks[chunks.length - 1].push(...tail);
  }
  return chunks;
}

/** Round-robin the practice of the given atoms (1st of each, then 2nd, ...). */
function roundRobinPractice(atoms: LearningAtom[]): string[] {
  const flat: string[] = [];
  const longest = Math.max(0, ...atoms.map((a) => a.practice?.length ?? 0));
  for (let round = 0; round < longest; round++) {
    for (const atom of atoms) {
      const practice = atom.practice ?? [];
      if (round < practice.length) flat.push(practice[round].id);
    }
  }
  return flat;
}

/** What the chain needs of a code task. */
export interface TaskRef {
  id: string;
  kind: string; // 'practice' | 'final'
  order: number;
}

/** The practice chunk (1-based) that task k of n follows: evenly spread over the chunks. */
function slot(k: number, n: number, chunkCount: number): number {
  const ceil = Math.floor((k * chunkCount + n) / (n + 1));
  return Math.max(1, Math.min(chunkCount, ceil));
}

const taskUnit = (t: TaskRef): LessonUnit => ({ id: `t:${t.id}`, kind: 'task', exerciseIds: [] });

/**
 * Code tasks join as `task` units: practice tasks spread over the practice
 * chunks (task k of n after chunk ceil(k*N/(n+1)), or after discovery when
 * there is no practice), the final task right before the boss units.
 */
export function deriveUnits(atoms: LearningAtoms, bossFight: BossQuestion[], tasks: TaskRef[] = []): LessonUnit[] {
  const units: LessonUnit[] = [];
  const sorted = [...tasks].sort((a, b) => a.order - b.order || (a.id < b.id ? -1 : a.id > b.id ? 1 : 0));
  const practiceTasks = sorted.filter((t) => t.kind !== 'final');
  const finalTasks = sorted.filter((t) => t.kind === 'final');

  for (const atom of atoms.atoms) {
    if (atom.discovery && atom.discovery.length > 0) {
      units.push({
        id: `d:${atom.id}`,
        kind: 'discovery',
        exerciseIds: atom.discovery.map((e) => e.id),
      });
    }
  }

  const regular = atoms.atoms.filter((a) => !a.capstone);
  const capstones = atoms.atoms.filter((a) => a.capstone);

  const chunks = chunk(roundRobinPractice(regular));
  const n = practiceTasks.length;
  if (chunks.length === 0) practiceTasks.forEach((t) => units.push(taskUnit(t)));
  chunks.forEach((ids, i) => {
    units.push({ id: `p${i + 1}`, kind: 'practice', exerciseIds: ids });
    for (let k = 1; k <= n; k++) {
      if (slot(k, n, chunks.length) === i + 1) units.push(taskUnit(practiceTasks[k - 1]));
    }
  });

  // Capstone synthesis: the capstone atoms' practice, in file order, as a
  // dedicated final block right before the mistakes/boss units.
  const capFlat: string[] = [];
  for (const atom of capstones) {
    for (const e of atom.practice ?? []) capFlat.push(e.id);
  }
  chunk(capFlat).forEach((ids, i) => {
    units.push({ id: `c${i + 1}`, kind: 'capstone', exerciseIds: ids });
  });

  // Single "fix your mistakes" unit; its contents are derived from the
  // learner's wrong answers at render time (see lessonStore).
  units.push({ id: 'mistakes', kind: 'mistakes', exerciseIds: [] });

  // The final task covers the whole topic: the last step before the Boss Fight.
  finalTasks.forEach((t) => units.push(taskUnit(t)));

  for (const q of bossFight) {
    units.push({ id: `b:${q.id}`, kind: 'boss', exerciseIds: [] });
  }
  return units;
}
