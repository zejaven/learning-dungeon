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

export function deriveUnits(atoms: LearningAtoms, bossFight: BossQuestion[]): LessonUnit[] {
  const units: LessonUnit[] = [];

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

  chunk(roundRobinPractice(regular)).forEach((ids, i) => {
    units.push({ id: `p${i + 1}`, kind: 'practice', exerciseIds: ids });
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

  for (const q of bossFight) {
    units.push({ id: `b:${q.id}`, kind: 'boss', exerciseIds: [] });
  }
  return units;
}
