import { useEffect, useRef } from 'react';
import { useGeneration } from '@app/engine/generationStore';
import { statusLabel, ui, useLang } from '@app/i18n';

/**
 * Renders the live (or finished) state of a generation task by key, reading from
 * the generation store. Because the store outlives any dialog, this shows the
 * same continuing stream whether it is mounted in the Add-topic modal or inline
 * in the home theory panel, before or after closing/reopening.
 */
export function GenerationView({ taskKey }: { taskKey: string }) {
  const task = useGeneration((s) => s.tasks[taskKey]);
  const lang = useLang((s) => s.lang);
  const logRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    logRef.current?.scrollTo({ top: logRef.current.scrollHeight });
  }, [task?.log.length]);

  if (!task) return null;

  return (
    <div className="gen-view">
      {task.status && (
        <span className={`status-pill ${task.status}`} title={task.message}>
          {statusLabel(lang, task.status)}
        </span>
      )}
      {task.log.length > 0 && (
        <div className="stream" ref={logRef}>
          {task.log.join('\n')}
        </div>
      )}
      {task.status === 'done' && (
        <button className="accent" onClick={() => window.location.reload()}>
          {ui('reloadNew', lang)}
        </button>
      )}
    </div>
  );
}
