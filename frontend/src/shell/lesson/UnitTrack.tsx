import {
  frontierIndex,
  isUnitDone,
  mistakesCleared,
  unitHasMistake,
  type ExerciseResult,
} from '@app/engine/lessonStore';
import type { LessonUnit } from '@app/engine/lessonTypes';
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
 * frontier is gray and inert. Boss units carry a sword.
 */
export function UnitTrack({ units, results, currentUnitId, onSelect }: Props) {
  const lang = useLang((s) => s.lang);
  const bossResults = useStore((s) => s.bossFightResults);
  const passedBoss: Record<string, boolean> = {};
  for (const [qid, r] of Object.entries(bossResults)) {
    if (r.passed) passedBoss[qid] = true;
  }
  const cleared = mistakesCleared(units, results);
  const frontier = frontierIndex(units, results, passedBoss, cleared);

  return (
    <div className="unit-track">
      {units.map((unit, i) => {
        const done = isUnitDone(unit, results, passedBoss, cleared);
        const mistake = done && unitHasMistake(unit, results);
        const locked = i > frontier;
        const current = unit.id === currentUnitId;
        let cls = 'unit-circle';
        if (done) cls += mistake ? ' done mistake' : ' done';
        if (current) cls += ' current';
        if (locked) cls += ' locked';
        cls += ` ${unit.kind}`;
        return (
          <button
            key={unit.id}
            className={cls}
            disabled={locked}
            title={locked ? ui('unitLocked', lang) : unitLabel(unit, lang)}
            onClick={() => onSelect(unit.id)}
          >
            {circleIcon(unit, done, mistake)}
          </button>
        );
      })}
    </div>
  );
}

function circleIcon(unit: LessonUnit, done: boolean, mistake: boolean): string {
  if (unit.kind === 'boss') return '⚔';
  if (unit.kind === 'mistakes') return done ? '✓' : '🔁';
  if (mistake) return '✗';
  if (done) return '✓';
  if (unit.kind === 'capstone') return '★';
  return unit.kind === 'discovery' ? '◆' : '●';
}

function unitLabel(unit: LessonUnit, lang: Lang): string {
  const phase =
    unit.kind === 'discovery'
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
