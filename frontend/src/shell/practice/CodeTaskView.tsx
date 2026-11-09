import { useState } from 'react';
import { clearDraft, readDraft, useCodeTasks, writeDraft } from '@app/engine/codeTaskStore';
import type { TaskListItem } from '@app/engine/codeTaskTypes';
import { langsOf, tl, tlStrict, ui, useLang } from '@app/i18n';
import { EditorPanel } from '@app/shell/EditorPanel';
import { Markdown } from '@app/shell/Markdown';
import { MissingLanguage } from '@app/shell/MissingLanguage';
import { TestResults } from '@app/shell/TestResults';

/**
 * One task: attempt first, then a hint per failed run, then the debrief and the
 * reference once it is solved. The reference also opens after the last hint, or
 * a learner who is stuck would have no way out.
 */
export function CodeTaskView({ topicId, item }: { topicId: string; item: TaskListItem }) {
  const { task, atomSummary, progress } = item;
  const lang = useLang((s) => s.lang);
  const running = useCodeTasks((s) => s.running);
  const run = useCodeTasks((s) => s.run);
  const lastRun = useCodeTasks((s) => s.runs[task.id]);
  const runError = useCodeTasks((s) => s.runError);

  const [code, setCode] = useState(() => readDraft(topicId, task.id) ?? task.starter);
  const [gaveUp, setGaveUp] = useState(false);

  const statement = tlStrict(task.statement, lang);
  if (!statement) return <MissingLanguage available={langsOf(task.statement)} />;

  const edit = (next: string) => {
    setCode(next);
    writeDraft(topicId, task.id, next);
  };
  const reset = () => {
    setCode(task.starter);
    clearDraft(topicId, task.id);
  };

  const hintsOpen = progress.solved ? 0 : Math.min(progress.attempts, task.hints.length);
  const stuck = !progress.solved && task.hints.length > 0 && progress.attempts >= task.hints.length;
  const showAnswer = progress.solved || gaveUp;

  return (
    <div className="code-task">
      <h3>{tl(task.title, lang)}</h3>
      <Markdown className="markdown">{statement}</Markdown>

      <div className="code-task-editor">
        <div className="toolbar">
          <button className="primary" onClick={() => void run(task.id, code)} disabled={running}>
            {running ? ui('running', lang) : ui('runTests', lang)}
          </button>
          <button onClick={reset}>{ui('reset', lang)}</button>
          <span className="active-file">Solution.java</span>
        </div>
        <EditorPanel code={code} onChange={edit} path={`${topicId}/${task.id}/Solution.java`} />
      </div>

      {runError && <div className="output error">{runError}</div>}
      <div className="sql-result-area">
        <TestResults
          tests={lastRun?.tests ?? null}
          notRun={lastRun?.notRun}
          error={lastRun?.error}
          hint={ui('taskHint', lang)}
        />
      </div>
      {lastRun?.output && (
        <details className="code-task-output">
          <summary>{ui('programOutput', lang)}</summary>
          <div className="output">{lastRun.output}</div>
        </details>
      )}

      {task.hints.slice(0, hintsOpen).map((hint, i) => (
        <div key={i} className="code-task-hint">
          <strong>
            {ui('hint', lang)} {i + 1}
          </strong>
          <Markdown className="markdown">{tl(hint, lang)}</Markdown>
        </div>
      ))}

      {stuck && !gaveUp && <button onClick={() => setGaveUp(true)}>{ui('showReference', lang)}</button>}

      {showAnswer && (
        <div className="code-task-answer">
          <h4>{ui('debrief', lang)}</h4>
          <Markdown className="markdown">{tl(atomSummary, lang)}</Markdown>
          <Markdown className="markdown">{tl(task.debrief, lang)}</Markdown>
          <h4>{ui('referenceSolution', lang)}</h4>
          <Markdown className="markdown">{`\`\`\`java\n${task.reference}\n\`\`\``}</Markdown>
        </div>
      )}
    </div>
  );
}
