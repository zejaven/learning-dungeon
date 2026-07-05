import { useLesson } from '@app/engine/lessonStore';
import type { LessonUnit } from '@app/engine/lessonTypes';
import { useStore } from '@app/engine/store';
import { ui, useLang } from '@app/i18n';
import { BossQuestionForm } from '@app/shell/BossQuestionForm';

interface Props {
  unit: LessonUnit;
}

/**
 * A boss question embedded as the lesson's final units. Reuses the shared
 * grading form; a passing grade completes the unit server-side (which also
 * recomputes lesson completion) and unlocks Continue to the next circle.
 */
export function BossFightUnit({ unit }: Props) {
  const lang = useLang((s) => s.lang);
  const topic = useStore((s) => s.topic);
  const results = useStore((s) => s.bossFightResults);
  const completedUnits = useLesson((s) => s.completedUnits);
  const units = useLesson((s) => s.units);
  const bossUnitPassed = useLesson((s) => s.bossUnitPassed);
  const goToUnit = useLesson((s) => s.goToUnit);

  const qid = unit.id.slice(2); // 'b:<questionId>'
  const question = topic?.bossFight.find((q) => q.id === qid);
  if (!topic || !question) return null;

  const passed = !!results[qid]?.passed || !!completedUnits[unit.id];
  const index = units.findIndex((u) => u.id === unit.id);
  const next = units[index + 1] ?? null;

  return (
    <div className="boss-unit">
      <BossQuestionForm
        topicId={topic.id}
        question={question}
        onPassed={() => void bossUnitPassed(unit.id)}
      />
      {passed && next && (
        <div className="ex-actions">
          <button className="primary" onClick={() => goToUnit(next.id)}>
            {ui('continueBtn', lang)}
          </button>
        </div>
      )}
    </div>
  );
}
