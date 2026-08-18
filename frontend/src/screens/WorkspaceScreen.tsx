import { useState } from 'react';
import { domainById } from '@app/domains';
import { useDomain } from '@app/engine/domainStore';
import { navigate, routeForQuestion, useRoute } from '@app/engine/router';
import { useStore } from '@app/engine/store';
import { tl, ui, useLang } from '@app/i18n';
import { EditorPanel } from '@app/shell/EditorPanel';
import { VisualizationCanvas } from '@app/shell/VisualizationCanvas';
import { ClassDiagram } from '@app/shell/ClassDiagram';
import { SchemaPanel } from '@app/shell/SchemaPanel';
import { SqlResultTable } from '@app/shell/SqlResultTable';
import { TestResults } from '@app/shell/TestResults';
import { MissionPanel } from '@app/shell/MissionPanel';
import { AssistantDialog } from '@app/shell/AssistantDialog';
import { AiProviderSelector } from '@app/shell/AiProviderSelector';
import { BossFightDialog } from '@app/shell/BossFightDialog';
import { FileTree } from '@app/shell/FileTree';
import { LangSwitcher } from '@app/shell/LangSwitcher';
import { OfflineBadge } from '@app/shell/OfflineBadge';
import { SettingsButton } from '@app/shell/SettingsButton';
import { ThemeSwitcher } from '@app/shell/ThemeSwitcher';
import { UsageBar } from '@app/shell/UsageBar';
import { Celebration } from '@app/shell/Celebration';

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

  const lang = useLang((s) => s.lang);
  const route = useRoute();
  const domain = domainById(useDomain((s) => s.domainId));

  const [showAssistant, setShowAssistant] = useState(false);
  const [showBossFight, setShowBossFight] = useState(false);

  const structural = topic?.mode === 'structural';
  const sqlMode = topic?.mode === 'sql';
  const challengeMode = topic?.mode === 'challenge';
  const hasViz = !sqlMode && !challengeMode; // only trace/structural have a right-panel visual

  return (
    <div className="app">
      <header className="header">
        <button onClick={() => navigate(route.id ? routeForQuestion(route.id) : '/')}>
          {ui('backToCatalog', lang)}
        </button>
        <h1 className="workspace-title">{topic ? tl(topic.title, lang) : tl(domain.title, lang)}</h1>
        <SettingsButton />
        <OfflineBadge />
        {topicCompleted && <span className="completed-badge">{ui('topicCompleted', lang)}</span>}
        <div className="spacer" />
        <AiProviderSelector />
        <UsageBar />
        <ThemeSwitcher />
        <LangSwitcher />
        <button onClick={() => setShowAssistant(true)} disabled={!topic}>
          {ui('askAI', lang)}
        </button>
      </header>

      <div className="main">
        {/* Left: schema (sql) or file tree */}
        <section className="panel">
          <div className="panel-title">{ui(sqlMode ? 'schema' : 'files', lang)}</div>
          <div className="panel-body">
            {sqlMode ? (
              <SchemaPanel />
            ) : structural ? (
              <FileTree />
            ) : (
              <div className="filetree">
                <div className="filetree-folder">📂 src</div>
                <div className="filetree-file selected">
                  📄 {challengeMode ? 'Solution.java' : 'Playground.java'}
                </div>
              </div>
            )}
          </div>
        </section>

        {/* Center: editor */}
        {sqlMode ? (
          <SqlEditor />
        ) : challengeMode ? (
          <ChallengeEditor />
        ) : structural ? (
          <StructuralEditor />
        ) : (
          <TraceEditor />
        )}

        {/* Right: visualization / class diagram (none for sql/challenge) + missions + boss fight */}
        <section className="panel">
          <div className="panel-title">
            {ui(hasViz ? (structural ? 'classDiagram' : 'visualization') : 'missions', lang)}
          </div>
          <div className="panel-body">
            {hasViz && (structural ? <ClassDiagram /> : <VisualizationCanvas />)}
            {topic && topic.missions.length > 0 && (
              <>
                {hasViz && (
                  <div className="panel-title" style={{ border: 'none', padding: '14px 0 4px' }}>
                    {ui('missions', lang)}
                  </div>
                )}
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

      <Celebration />
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
          path={activePath}
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

/** Center panel for SQL topics: query editor + Run query + result table. */
function SqlEditor() {
  const sqlQuery = useStore((s) => s.sqlQuery);
  const setSqlQuery = useStore((s) => s.setSqlQuery);
  const runSql = useStore((s) => s.runSql);
  const runningSql = useStore((s) => s.runningSql);
  const lang = useLang((s) => s.lang);

  return (
    <section className="panel">
      <div className="toolbar">
        <button className="primary" onClick={runSql} disabled={runningSql}>
          {runningSql ? ui('running', lang) : ui('runQuery', lang)}
        </button>
      </div>
      <EditorPanel code={sqlQuery} onChange={setSqlQuery} language="sql" />
      <div className="sql-result-area">
        <SqlResultTable />
      </div>
    </section>
  );
}

/** Center panel for challenge topics: solution editor + Run tests + test results. */
function ChallengeEditor() {
  const code = useStore((s) => s.code);
  const setCode = useStore((s) => s.setCode);
  const runTests = useStore((s) => s.runTests);
  const runningTests = useStore((s) => s.runningTests);
  const lang = useLang((s) => s.lang);

  return (
    <section className="panel">
      <div className="toolbar">
        <button className="primary" onClick={runTests} disabled={runningTests}>
          {runningTests ? ui('running', lang) : ui('runTests', lang)}
        </button>
        <span className="active-file">Solution.java</span>
      </div>
      <EditorPanel code={code} onChange={setCode} />
      <div className="sql-result-area">
        <TestResults />
      </div>
    </section>
  );
}
