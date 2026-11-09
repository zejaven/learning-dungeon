import { useState } from 'react';
import { useAi } from '@app/engine/aiStore';
import { startAtomsGeneration } from '@app/engine/api';
import { genLanguages } from '@app/engine/genLangStore';
import { useGeneration } from '@app/engine/generationStore';
import { useStore } from '@app/engine/store';
import { ui, useLang } from '@app/i18n';
import { RegenerateLessonDialog, type RegenerateOptions } from './RegenerateLessonDialog';

/**
 * The rarely used "regenerate this lesson" action, kept in the topic tab bar
 * rather than next to the exercise. LessonPanel watches the same generation
 * task to show its progress and reload the lesson when it finishes.
 */
export function RegenerateLessonButton({ topicId }: { topicId: string }) {
  const lang = useLang((s) => s.lang);
  const activeVersionNo = useStore((s) => s.activeVersionNo);
  const generating = useGeneration((s) => s.tasks[`atoms:${topicId}`]?.status === 'running');
  const [open, setOpen] = useState(false);

  async function regenerate(options: RegenerateOptions) {
    setOpen(false);
    try {
      const ref = await startAtomsGeneration(
        topicId,
        useAi.getState().selectedProvider,
        activeVersionNo,
        options.mode,
        options.comment,
        genLanguages(),
      );
      useGeneration.getState().attach(ref.taskId, ref.key);
    } catch {
      /* the button stays; the learner can retry */
    }
  }

  return (
    <>
      <button
        className="topic-tabs-regen"
        onClick={() => setOpen(true)}
        disabled={generating}
        title={ui('regenerateLesson', lang)}
      >
        {generating ? ui('generating', lang) : '↻'}
      </button>
      {open && <RegenerateLessonDialog onConfirm={regenerate} onClose={() => setOpen(false)} />}
    </>
  );
}
