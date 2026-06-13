import { useEffect, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { useStore } from './engine/store';
import { EditorPanel } from './shell/EditorPanel';
import { VisualizationCanvas } from './shell/VisualizationCanvas';
import { MissionPanel } from './shell/MissionPanel';
import { AssistantDialog } from './shell/AssistantDialog';
import { AddTopicDialog } from './shell/AddTopicDialog';

export function App() {
  const topics = useStore((s) => s.topics);
  const topic = useStore((s) => s.topic);
  const code = useStore((s) => s.code);
  const running = useStore((s) => s.running);
  const output = useStore((s) => s.output);
  const runError = useStore((s) => s.runError);
  const completed = useStore((s) => s.completedMissions);

  const loadTopics = useStore((s) => s.loadTopics);
  const selectTopic = useStore((s) => s.selectTopic);
  const setCode = useStore((s) => s.setCode);
  const loadExample = useStore((s) => s.loadExample);
  const resetCode = useStore((s) => s.resetCode);
  const run = useStore((s) => s.run);

  const [showAssistant, setShowAssistant] = useState(false);
  const [showAddTopic, setShowAddTopic] = useState(false);

  useEffect(() => {
    loadTopics();
  }, [loadTopics]);

  return (
    <div className="app">
      <header className="header">
        <h1>🗡️ Java Interview Dungeon</h1>
        <select
          value={topic?.id ?? ''}
          onChange={(e) => selectTopic(e.target.value)}
          disabled={topics.length === 0}
        >
          {topics.length === 0 && <option value="">No topics yet</option>}
          {topics.map((t) => (
            <option key={t.id} value={t.id}>
              {t.title}
            </option>
          ))}
        </select>
        <div className="spacer" />
        <button onClick={() => setShowAssistant(true)} disabled={!topic}>
          💬 Ask AI
        </button>
        <button className="accent" onClick={() => setShowAddTopic(true)}>
          ＋ Add topic
        </button>
      </header>

      <div className="main">
        {/* Left: explanation */}
        <section className="panel">
          <div className="panel-title">{topic ? topic.category : 'Explanation'}</div>
          <div className="panel-body markdown">
            {topic ? (
              <ReactMarkdown remarkPlugins={[remarkGfm]}>{topic.explanation}</ReactMarkdown>
            ) : (
              <p style={{ opacity: 0.6 }}>Loading…</p>
            )}
          </div>
        </section>

        {/* Center: editor */}
        <section className="panel">
          <div className="toolbar">
            <button className="primary" onClick={run} disabled={running || !topic}>
              {running ? 'running…' : '▶ Run'}
            </button>
            <button onClick={resetCode} disabled={!topic}>
              ↺ Reset
            </button>
            <span style={{ width: 8 }} />
            {topic?.examples.map((ex) => (
              <button key={ex.id} onClick={() => loadExample(ex.id)} title={ex.explanation}>
                {ex.title}
              </button>
            ))}
          </div>
          <EditorPanel code={code} onChange={setCode} />
          {(output || runError) && (
            <div className={`output${runError ? ' error' : ''}`}>
              {output}
              {runError ? `\n${runError}` : ''}
            </div>
          )}
        </section>

        {/* Right: visualization + missions */}
        <section className="panel">
          <div className="panel-title">Visualization</div>
          <div className="panel-body">
            <VisualizationCanvas />
            {topic && topic.missions.length > 0 && (
              <>
                <div className="panel-title" style={{ border: 'none', padding: '14px 0 4px' }}>
                  Missions
                </div>
                <MissionPanel missions={topic.missions} completed={completed} />
              </>
            )}
          </div>
        </section>
      </div>

      {showAssistant && <AssistantDialog onClose={() => setShowAssistant(false)} />}
      {showAddTopic && <AddTopicDialog onClose={() => setShowAddTopic(false)} />}
    </div>
  );
}
