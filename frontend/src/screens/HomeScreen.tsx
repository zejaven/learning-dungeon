import { useState } from 'react';
import { buildCatalog, findCatalogEntry } from '@app/catalog';
import { useGeneration } from '@app/engine/generationStore';
import { navigate, routeForPractice, routeForQuestion, useRoute } from '@app/engine/router';
import { useStore } from '@app/engine/store';
import { useStyle } from '@app/engine/styleStore';
import { tl, ui, useLang } from '@app/i18n';
import { AddQuestionDialog } from '@app/shell/AddQuestionDialog';
import { AddTopicDialog } from '@app/shell/AddTopicDialog';
import { AiProviderSelector } from '@app/shell/AiProviderSelector';
import { AssistantDialog } from '@app/shell/AssistantDialog';
import { BossFightDialog } from '@app/shell/BossFightDialog';
import { CategoryTree } from '@app/shell/CategoryTree';
import { Celebration } from '@app/shell/Celebration';
import { GenerationView } from '@app/shell/GenerationView';
import { LangSwitcher } from '@app/shell/LangSwitcher';
import { Markdown } from '@app/shell/Markdown';
import { StyleSelector } from '@app/shell/StyleSelector';
import { ThemeSwitcher } from '@app/shell/ThemeSwitcher';
import { UsageBar } from '@app/shell/UsageBar';

/**
 * Home / catalog screen: a tree of interview questions on the left, theory (or a
 * "generate theory" action) on the right. The selected question lives in the URL
 * (`#/q/<id>`), so a refresh restores it; selecting a tree entry just navigates.
 * Generation is server-tracked, so a run started here keeps going and is shown
 * again the moment you return to the question (or reopen "Add topic").
 */
export function HomeScreen() {
  const lang = useLang((s) => s.lang);
  const topics = useStore((s) => s.topics);
  const topic = useStore((s) => s.topic);
  const loadingTopic = useStore((s) => s.loadingTopic);
  const theoryVersions = useStore((s) => s.theoryVersions);
  const activeVersionNo = useStore((s) => s.activeVersionNo);
  const generatingVersion = useStore((s) => s.generatingVersion);
  const setActiveVersion = useStore((s) => s.setActiveVersion);
  const regenerateTheory = useStore((s) => s.regenerateTheory);

  const route = useRoute();
  const startGen = useGeneration((s) => s.start);
  const addTask = useGeneration((s) => s.tasks['add-topic']);

  const [showAdd, setShowAdd] = useState(false);
  const [showAddQuestion, setShowAddQuestion] = useState(false);
  const [showBossFight, setShowBossFight] = useState(false);
  const [showAssistant, setShowAssistant] = useState(false);

  const manualQuestions = useStore((s) => s.manualQuestions);
  const found = route.id ? findCatalogEntry(buildCatalog(topics, manualQuestions), route.id) : null;
  const entry = found?.entry ?? null;
  const categoryId = found?.categoryId ?? '';

  const theoryReady = entry?.topicId && topic?.id === entry.topicId && !loadingTopic;
  const catalogKey = entry ? `catalog:${entry.id}` : '';
  const genTask = useGeneration((s) => (catalogKey ? s.tasks[catalogKey] : undefined));

  function generateForSelected() {
    if (!entry) return;
    startGen(`catalog:${entry.id}`, {
      question: tl(entry.question, lang),
      catalogId: entry.id,
      categoryId,
      difficulty: entry.difficulty,
      style: useStyle.getState().instruction(),
      styleName: useStyle.getState().currentName(),
    });
  }

  const activeVersion = theoryVersions.find((v) => v.versionNo === activeVersionNo);
  const activeExplanation = activeVersion
    ? { en: activeVersion.en, ru: activeVersion.ru }
    : topic?.explanation;

  return (
    <div className="app">
      <header className="header">
        <h1>🗡️ Java Interview Dungeon</h1>
        <div className="spacer" />
        <AiProviderSelector />
        <UsageBar />
        <ThemeSwitcher />
        <LangSwitcher />
        <button className="accent" onClick={() => setShowAdd(true)}>
          {addTask?.status === 'running' ? ui('generating', lang) : ui('addTopic', lang)}
        </button>
      </header>

      <div className="home-main">
        {/* Left: question catalog tree */}
        <section className="panel home-tree-panel">
          <div className="panel-title tree-panel-title">
            <span>{ui('catalogTitle', lang)}</span>
            <button
              className="tree-add-btn"
              title={ui('addQuestion', lang)}
              onClick={() => setShowAddQuestion(true)}
            >
              ＋
            </button>
          </div>
          <div className="panel-body tree-body">
            <CategoryTree
              selectedId={entry?.id ?? null}
              onSelect={(e) => navigate(routeForQuestion(e.id))}
            />
          </div>
        </section>

        {/* Right: theory or generate action */}
        <section className="panel home-theory-panel">
          <div className="panel-title tree-panel-title">
            <span>{theoryReady ? tl(topic!.category, lang) : ui('theory', lang)}</span>
            {theoryReady && (
              <button className="theory-ask-btn" onClick={() => setShowAssistant(true)}>
                {ui('askAI', lang)}
              </button>
            )}
          </div>
          <div className="panel-body">
            {!entry && <p className="home-hint">{ui('selectQuestion', lang)}</p>}

            {entry && !entry.topicId && (
              <div className="home-generate">
                <div className="home-question">{tl(entry.question, lang)}</div>
                {genTask ? (
                  <GenerationView taskKey={catalogKey} />
                ) : (
                  <>
                    <p className="home-hint">{ui('noTheoryYet', lang)}</p>
                    <StyleSelector />
                    <button className="primary" onClick={generateForSelected}>
                      {ui('generateTheory', lang)}
                    </button>
                  </>
                )}
              </div>
            )}

            {entry?.topicId && !theoryReady && <p className="home-hint">{ui('openingTheory', lang)}</p>}

            {theoryReady && (
              <div className="home-theory">
                <div className="home-theory-actions">
                  {topic!.mode === 'theory' ? (
                    topic!.bossFight.length > 0 && (
                      <button className="accent" onClick={() => setShowBossFight(true)}>
                        {ui('bossFight', lang)}
                      </button>
                    )
                  ) : (
                    <button className="primary" onClick={() => navigate(routeForPractice(entry!.id))}>
                      {ui('goToPractice', lang)}
                    </button>
                  )}
                </div>

                {theoryVersions.length > 0 && (
                  <div className="version-bar">
                    <span className="style-label">{ui('version', lang)}</span>
                    <select
                      value={activeVersionNo}
                      onChange={(e) => setActiveVersion(Number(e.target.value))}
                    >
                      {theoryVersions.map((v) => (
                        <option key={v.versionNo} value={v.versionNo}>
                          v{v.versionNo} - {v.style} - {v.aiProvider || 'claude'}
                        </option>
                      ))}
                    </select>
                    <span
                      className="version-provider"
                      title={activeVersion?.aiModel || topic!.aiModel || ''}
                    >
                      {activeVersion?.aiProvider || topic!.aiProvider || 'claude'}
                    </span>
                    <StyleSelector />
                    <button
                      onClick={() =>
                        regenerateTheory(useStyle.getState().instruction(), useStyle.getState().currentName())
                      }
                      disabled={generatingVersion}
                    >
                      {generatingVersion ? ui('generating', lang) : ui('generateVersion', lang)}
                    </button>
                  </div>
                )}

                <Markdown className="markdown">{tl(activeExplanation, lang)}</Markdown>
              </div>
            )}
          </div>
        </section>
      </div>

      {showAdd && <AddTopicDialog onClose={() => setShowAdd(false)} />}
      {showAddQuestion && <AddQuestionDialog onClose={() => setShowAddQuestion(false)} />}
      {showBossFight && <BossFightDialog onClose={() => setShowBossFight(false)} />}
      {showAssistant && <AssistantDialog onClose={() => setShowAssistant(false)} />}
      <Celebration />
    </div>
  );
}
