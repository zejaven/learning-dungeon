import { useEffect, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { useStore } from './engine/store';
import { LANGS, tl, ui, useLang } from './i18n';
import { EditorPanel } from './shell/EditorPanel';
import { VisualizationCanvas } from './shell/VisualizationCanvas';
import { MissionPanel } from './shell/MissionPanel';
import { AssistantDialog } from './shell/AssistantDialog';
import { AddTopicDialog } from './shell/AddTopicDialog';
import { BossFightDialog } from './shell/BossFightDialog';

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

  const lang = useLang((s) => s.lang);
  const setLang = useLang((s) => s.setLang);

  const [showAssistant, setShowAssistant] = useState(false);
  const [showAddTopic, setShowAddTopic] = useState(false);
  const [showBossFight, setShowBossFight] = useState(false);

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
          {topics.length === 0 && <option value="">{ui('noTopics', lang)}</option>}
          {topics.map((t) => (
            <option key={t.id} value={t.id}>
              {tl(t.title, lang)}
            </option>
          ))}
        </select>
        <div className="spacer" />
        <select value={lang} onChange={(e) => setLang(e.target.value as (typeof LANGS)[number])}>
          {LANGS.map((l) => (
            <option key={l} value={l}>
              {l.toUpperCase()}
            </option>
          ))}
        </select>
        <button onClick={() => setShowAssistant(true)} disabled={!topic}>
          {ui('askAI', lang)}
        </button>
        <button className="accent" onClick={() => setShowAddTopic(true)}>
          {ui('addTopic', lang)}
        </button>
      </header>

      <div className="main">
        {/* Left: explanation */}
        <section className="panel">
          <div className="panel-title">
            {topic ? tl(topic.category, lang) : ui('explanation', lang)}
          </div>
          <div className="panel-body markdown">
            {topic ? (
              <ReactMarkdown remarkPlugins={[remarkGfm]}>
                {tl(topic.explanation, lang)}
              </ReactMarkdown>
            ) : (
              <p style={{ opacity: 0.6 }}>{ui('loading', lang)}</p>
            )}
          </div>
        </section>

        {/* Center: editor */}
        <section className="panel">
          <div className="toolbar">
            <button className="primary" onClick={run} disabled={running || !topic}>
              {running ? ui('running', lang) : ui('run', lang)}
            </button>
            <button onClick={resetCode} disabled={!topic}>
              {ui('reset', lang)}
            </button>
            <span style={{ width: 8 }} />
            {topic?.examples.map((ex) => (
              <button key={ex.id} onClick={() => loadExample(ex.id)} title={tl(ex.explanation, lang)}>
                {tl(ex.title, lang)}
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
          <div className="panel-title">{ui('visualization', lang)}</div>
          <div className="panel-body">
            <VisualizationCanvas />
            {topic && topic.missions.length > 0 && (
              <>
                <div className="panel-title" style={{ border: 'none', padding: '14px 0 4px' }}>
                  {ui('missions', lang)}
                </div>
                <MissionPanel missions={topic.missions} completed={completed} />
              </>
            )}
            {topic && topic.bossFight.length > 0 && (
              <button
                className="accent boss-fight-btn"
                onClick={() => setShowBossFight(true)}
              >
                {ui('bossFight', lang)}
              </button>
            )}
          </div>
        </section>
      </div>

      {showAssistant && <AssistantDialog onClose={() => setShowAssistant(false)} />}
      {showAddTopic && <AddTopicDialog onClose={() => setShowAddTopic(false)} />}
      {showBossFight && <BossFightDialog onClose={() => setShowBossFight(false)} />}
    </div>
  );
}
