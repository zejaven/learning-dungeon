import { frontierIndex, isUnitDone, unitHasMistake, type ExerciseResult } from '@app/engine/lessonStore';
import type { LessonUnit } from '@app/engine/lessonTypes';
import { useStore } from '@app/engine/store';
import { ui, useLang } from '@app/i18n';

interface Props {
  units: LessonUnit[];
  completedUnits: Record<string, boolean>;
  results: Record<string, ExerciseResult>;
  currentUnitId: string | null;
  onSelect: (unitId: string) => void;
}

/**
 * The horizontal Duolingo-style circle row: completed units are filled and
 * clickable, the current one is highlighted, everything past the unlock
 * frontier is gray and inert. Boss units carry a sword.
 */
export function UnitTrack({ units, completedUnits, results, currentUnitId, onSelect }: Props) {
  const lang = useLang((s) => s.lang);
  const bossResults = useStore((s) => s.bossFightResults);
  const passedBoss: Record<string, boolean> = {};
  for (const [qid, r] of Object.entries(bossResults)) {
    if (r.passed) passedBoss[qid] = true;
  }
  const frontier = frontierIndex(units, completedUnits, passedBoss);

  return (
    <div className="unit-track">
      {units.map((unit, i) => {
        const done = isUnitDone(unit, completedUnits, passedBoss);
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
            {unit.kind === 'boss' ? '⚔' : mistake ? '✗' : done ? '✓' : circleLabel(unit)}
          </button>
        );
      })}
    </div>
  );
}

function circleLabel(unit: LessonUnit): string {
  return unit.kind === 'discovery' ? '◆' : '●';
}

function unitLabel(unit: LessonUnit, lang: 'en' | 'ru'): string {
  const phase =
    unit.kind === 'discovery'
      ? ui('discoveryPhase', lang)
      : unit.kind === 'practice'
        ? ui('practicePhase', lang)
        : ui('bossPhase', lang);
  return `${phase} — ${unit.id}`;
}
