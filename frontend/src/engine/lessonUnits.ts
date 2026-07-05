import type { BossQuestion } from './traceTypes';
import type { LearningAtoms, LessonUnit } from './lessonTypes';

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

  // Practice exercises are flattened round-robin across atoms (1st of each
  // atom, then 2nd, ...) so consecutive exercises mix ideas.
  const flat: string[] = [];
  const longest = Math.max(0, ...atoms.atoms.map((a) => a.practice?.length ?? 0));
  for (let round = 0; round < longest; round++) {
    for (const atom of atoms.atoms) {
      const practice = atom.practice ?? [];
      if (round < practice.length) flat.push(practice[round].id);
    }
  }

  const chunks: string[][] = [];
  for (let i = 0; i < flat.length; i += PRACTICE_CHUNK) {
    chunks.push(flat.slice(i, i + PRACTICE_CHUNK));
  }
  if (chunks.length > 1 && chunks[chunks.length - 1].length < MIN_LAST_CHUNK) {
    const tail = chunks.pop()!;
    chunks[chunks.length - 1].push(...tail);
  }
  chunks.forEach((ids, i) => {
    units.push({ id: `p${i + 1}`, kind: 'practice', exerciseIds: ids });
  });

  for (const q of bossFight) {
    units.push({ id: `b:${q.id}`, kind: 'boss', exerciseIds: [] });
  }
  return units;
}
