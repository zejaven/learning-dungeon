import { useEffect, useRef } from 'react';
import {
  frontierIndex,
  isUnitDone,
  mistakesCleared,
  unitHasMistake,
  type ExerciseResult,
} from '@app/engine/lessonStore';
import type { LessonUnit } from '@app/engine/lessonTypes';
import { useCodeTasks } from '@app/engine/codeTaskStore';
import { useStore } from '@app/engine/store';
import { ui, useLang, type Lang } from '@app/i18n';

interface Props {
  units: LessonUnit[];
  results: Record<string, ExerciseResult>;
  currentUnitId: string | null;
  onSelect: (unitId: string) => void;
}

/**
 * The horizontal Duolingo-style circle row: completed units are filled and
 * clickable, the current one is highlighted, everything past the unlock
 * frontier is gray and inert. Boss units carry a sword; code task units a
 * keyboard (the final one a flag) and are never locked, see frontierIndex.
 */
export function UnitTrack({ units, results, currentUnitId, onSelect }: Props) {
  const lang = useLang((s) => s.lang);
  const bossResults = useStore((s) => s.bossFightResults);
  const passedBoss: Record<string, boolean> = {};
  for (const [qid, r] of Object.entries(bossResults)) {
    if (r.passed) passedBoss[qid] = true;
  }
  const taskItems = useCodeTasks((s) => s.items);
  const solvedTasks: Record<string, boolean> = {};
  const finalTasks: Record<string, boolean> = {};
  for (const { task, progress } of taskItems ?? []) {
    if (progress.solved) solvedTasks[task.id] = true;
    if (task.kind === 'final') finalTasks[task.id] = true;
  }
  const cleared = mistakesCleared(units, results);
  const frontier = frontierIndex(units, results, passedBoss, cleared);

  // Keep the current unit visible: a long lesson overflows the track, and on a
  // phone only a handful of circles fit, so it would otherwise sit off-screen.
  const trackRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const circle = trackRef.current?.querySelector('.unit-circle.current');
    circle?.scrollIntoView({ block: 'nearest', inline: 'center', behavior: 'smooth' });
  }, [currentUnitId]);

  return (
    <div className="unit-track" ref={trackRef}>
      {units.map((unit, i) => {
        const done = isUnitDone(unit, results, passedBoss, cleared, solvedTasks);
        const isFinal = unit.kind === 'task' && !!finalTasks[unit.id.slice(2)];
        const mistake = done && unitHasMistake(unit, results);
        const locked = i > frontier;
        const current = unit.id === currentUnitId;
        let cls = 'unit-circle';
        if (done) cls += mistake ? ' done mistake' : ' done';
        if (current) cls += ' current';
        if (locked) cls += ' locked';
        cls += ` ${unit.kind}`;
        if (isFinal) cls += ' final';
        return (
          <button
            key={unit.id}
            className={cls}
            disabled={locked}
            title={locked ? ui('unitLocked', lang) : unitLabel(unit, lang, isFinal)}
            onClick={() => onSelect(unit.id)}
          >
            {circleIcon(unit, done, mistake, isFinal)}
          </button>
        );
      })}
    </div>
  );
}

function circleIcon(unit: LessonUnit, done: boolean, mistake: boolean, isFinal: boolean): string {
  if (unit.kind === 'boss') return '⚔';
  if (unit.kind === 'task') return done ? '✓' : isFinal ? '🏁' : '⌨';
  if (unit.kind === 'mistakes') return done ? '✓' : '🔁';
  if (mistake) return '✗';
  if (done) return '✓';
  if (unit.kind === 'capstone') return '★';
  return unit.kind === 'discovery' ? '◆' : '●';
}

function unitLabel(unit: LessonUnit, lang: Lang, isFinal: boolean): string {
  const phase =
    unit.kind === 'task'
      ? ui(isFinal ? 'finalTaskPhase' : 'taskPhase', lang)
      : unit.kind === 'discovery'
      ? ui('discoveryPhase', lang)
      : unit.kind === 'practice'
        ? ui('practicePhase', lang)
        : unit.kind === 'capstone'
          ? ui('capstonePhase', lang)
          : unit.kind === 'mistakes'
            ? ui('mistakesTitle', lang)
            : ui('bossPhase', lang);
  return `${phase} — ${unit.id}`;
}
