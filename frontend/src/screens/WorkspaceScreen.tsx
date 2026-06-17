import { useState } from 'react';
import { navigate, routeForQuestion, useRoute } from '@app/engine/router';
import { useStore } from '@app/engine/store';
import { tl, ui, useLang } from '@app/i18n';
import { EditorPanel } from '@app/shell/EditorPanel';
import { VisualizationCanvas } from '@app/shell/VisualizationCanvas';
import { ClassDiagram } from '@app/shell/ClassDiagram';
import { MissionPanel } from '@app/shell/MissionPanel';
import { AssistantDialog } from '@app/shell/AssistantDialog';
import { BossFightDialog } from '@app/shell/BossFightDialog';
import { FileTree } from '@app/shell/FileTree';
import { LangSwitcher } from '@app/shell/LangSwitcher';
import { UsageBar } from '@app/shell/UsageBar';
import { Fireworks } from '@app/shell/Fireworks';

/**
 * Code workspace. Two engines, chosen by `topic.mode`:
 *  - 'trace' (default): single editor → Run → trace-driven visualization + missions.
 *  - 'structural' (design patterns): multi-file project → Analyze → class diagram +
 *    structure missions (no execution).
 */
export function WorkspaceScreen() {
  const topic = useStore((s) => s.topic);
  const completed = useStore((s) => s.completedMissions);
  const topicCompleted = useStore((s) => s.topicCompleted);
  const celebrating = useStore((s) => s.celebrating);
  const setCelebrating = useStore((s) => s.setCelebrating);

  const lang = useLang((s) => s.lang);
  const route = useRoute();

  const [showAssistant, setShowAssistant] = useState(false);
  const [showBossFight, setShowBossFight] = useState(false);

  const structural = topic?.mode === 'structural';

  return (
    <div className="app">
      <header className="header">
        <button onClick={() => navigate(route.id ? routeForQuestion(route.id) : '/')}>
          {ui('backToCatalog', lang)}
        </button>
        <h1 className="workspace-title">{topic ? tl(topic.title, lang) : 'Java Interview Dungeon'}</h1>
        {topicCompleted && <span className="completed-badge">{ui('topicCompleted', lang)}</span>}
        <div className="spacer" />
        <UsageBar />
        <LangSwitcher />
        <button onClick={() => setShowAssistant(true)} disabled={!topic}>
          {ui('askAI', lang)}
        </button>
      </header>

      <div className="main">
        {/* Left: file tree */}
        <section className="panel">
          <div className="panel-title">{ui('files', lang)}</div>
          <div className="panel-body">
            {structural ? (
              <FileTree />
            ) : (
              <div className="filetree">
                <div className="filetree-folder">📂 src</div>
                <div className="filetree-file selected">📄 Playground.java</div>
              </div>
            )}
          </div>
        </section>

        {/* Center: editor */}
        {structural ? <StructuralEditor /> : <TraceEditor />}

        {/* Right: visualization / class diagram + missions + boss fight */}
        <section className="panel">
          <div className="panel-title">{ui(structural ? 'classDiagram' : 'visualization', lang)}</div>
          <div className="panel-body">
            {structural ? <ClassDiagram /> : <VisualizationCanvas />}
            {topic && topic.missions.length > 0 && (
              <>
                <div className="panel-title" style={{ border: 'none', padding: '14px 0 4px' }}>
                  {ui('missions', lang)}
                </div>
                <MissionPanel missions={topic.missions} completed={completed} />
              </>
            )}
            {topic && topic.bossFight.length > 0 && (
              <button className="accent boss-fight-btn" onClick={() => setShowBossFight(true)}>
                {ui('bossFight', lang)}
              </button>
            )}
          </div>
        </section>
      </div>

      {showAssistant && <AssistantDialog onClose={() => setShowAssistant(false)} />}
      {showBossFight && <BossFightDialog onClose={() => setShowBossFight(false)} />}

      {celebrating && (
        <>
          <Fireworks />
          <div className="celebrate-card">
            <div className="celebrate-emoji">🏆</div>
            <h2>{ui('congratsTitle', lang)}</h2>
            <p>{ui('congratsBody', lang)}</p>
            <button className="primary" onClick={() => setCelebrating(false)}>
              {ui('celebrateClose', lang)}
            </button>
          </div>
        </>
      )}
    </div>
  );
}

/** Center panel for trace topics: single editor + Run + examples. */
function TraceEditor() {
  const topic = useStore((s) => s.topic);
  const code = useStore((s) => s.code);
  const running = useStore((s) => s.running);
  const output = useStore((s) => s.output);
  const runError = useStore((s) => s.runError);
  const setCode = useStore((s) => s.setCode);
  const loadExample = useStore((s) => s.loadExample);
  const resetCode = useStore((s) => s.resetCode);
  const run = useStore((s) => s.run);
  const lang = useLang((s) => s.lang);

  return (
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
  );
}

/** Center panel for structural topics: active-file editor + Analyze. */
function StructuralEditor() {
  const files = useStore((s) => s.files);
  const activePath = useStore((s) => s.activePath);
  const setFileContent = useStore((s) => s.setFileContent);
  const analyze = useStore((s) => s.analyze);
  const analyzing = useStore((s) => s.analyzing);
  const analyzeError = useStore((s) => s.analyzeError);
  const lang = useLang((s) => s.lang);

  return (
    <section className="panel">
      <div className="toolbar">
        <button className="primary" onClick={analyze} disabled={analyzing}>
          {analyzing ? ui('analyzing', lang) : ui('analyze', lang)}
        </button>
        {activePath && <span className="active-file">{activePath}</span>}
      </div>
      {activePath ? (
        <EditorPanel
          key={activePath}
          code={files[activePath] ?? ''}
          onChange={(v) => setFileContent(activePath, v)}
        />
      ) : (
        <div className="panel-body">
          <p className="home-hint">{ui('noActiveFile', lang)}</p>
        </div>
      )}
      {analyzeError && <div className="output error">{analyzeError}</div>}
    </section>
  );
}
