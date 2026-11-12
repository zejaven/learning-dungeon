import { useEffect, useState } from 'react';
import { useAi } from '@app/engine/aiStore';
import { fetchTasksNoneReason, startTasksGeneration } from '@app/engine/api';
import { useCodeTasks } from '@app/engine/codeTaskStore';
import { useGeneration } from '@app/engine/generationStore';
import { useStore } from '@app/engine/store';
import { ui, useLang } from '@app/i18n';
import { useBackdropClose } from '../backdropClose';

/**
 * Generates code tasks for the topic, from the lesson header. Tasks are only
 * ever added (existing ones and their progress stay), so this is "generate" or
 * "more", never "regenerate". Desktop-only, like running them. LessonPanel
 * watches the same `tasks:<id>` generation to show progress and reload.
 */
export function GenerateTasksButton({ topicId }: { topicId: string }) {
  const lang = useLang((s) => s.lang);
  const hasTasks = useCodeTasks((s) => s.topicId === topicId && (s.items?.length ?? 0) > 0);
  const generating = useGeneration((s) => s.tasks[`tasks:${topicId}`]?.status === 'running');
  const [open, setOpen] = useState(false);

  return (
    <>
      <button
        className="desktop-only"
        onClick={() => setOpen(true)}
        disabled={generating}
        title={ui(hasTasks ? 'generateMoreTasks' : 'generateTasks', lang)}
      >
        {generating ? ui('generating', lang) : '⌨ ✨'}
      </button>
      {open && <GenerateTasksDialog topicId={topicId} hasTasks={hasTasks} onClose={() => setOpen(false)} />}
    </>
  );
}

function GenerateTasksDialog({
  topicId,
  hasTasks,
  onClose,
}: {
  topicId: string;
  hasTasks: boolean;
  onClose: () => void;
}) {
  const lang = useLang((s) => s.lang);
  const decided = useStore((s) => !!s.topic?.tasksDecided);
  const [comment, setComment] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [noneReason, setNoneReason] = useState<string | null>(null);

  useEffect(() => {
    if (!hasTasks && decided) void fetchTasksNoneReason(topicId).then(setNoneReason).catch(() => {});
  }, [topicId, hasTasks, decided]);

  async function generate() {
    setError(null);
    try {
      const ref = await startTasksGeneration(topicId, useAi.getState().selectedProvider, comment.trim());
      useGeneration.getState().attach(ref.taskId, ref.key);
      onClose();
    } catch (e) {
      setError((e as Error).message);
    }
  }

  const backdrop = useBackdropClose(onClose);
  return (
    <div className="dialog-backdrop" {...backdrop}>
      <div className="dialog" onClick={(e) => e.stopPropagation()}>
        <div className="dialog-head">
          <h2>{ui(hasTasks ? 'generateMoreTasks' : 'generateTasks', lang)}</h2>
          <button onClick={onClose}>✕</button>
        </div>
        <div className="dialog-body">
          <p style={{ color: 'var(--muted)', fontSize: 13 }}>{ui('tasksGenerateHint', lang)}</p>
          {noneReason && (
            <p style={{ fontSize: 13 }}>
              {ui('tasksNone', lang)} {noneReason}
            </p>
          )}
          <textarea
            rows={3}
            placeholder={ui('tasksCommentPlaceholder', lang)}
            value={comment}
            onChange={(e) => setComment(e.target.value)}
          />
          {error && <div className="output error">{error}</div>}
        </div>
        <div className="dialog-foot">
          <button onClick={onClose}>{ui('close', lang)}</button>
          <button className="primary" onClick={() => void generate()}>
            {ui(hasTasks ? 'generateMoreTasks' : 'generateTasks', lang)}
          </button>
        </div>
      </div>
    </div>
  );
}
