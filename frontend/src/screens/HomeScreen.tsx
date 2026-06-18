import { useState } from 'react';
import { buildCatalog, findCatalogEntry } from '@app/catalog';
import { useGeneration } from '@app/engine/generationStore';
import { navigate, routeForPractice, routeForQuestion, useRoute } from '@app/engine/router';
import { useStore } from '@app/engine/store';
import { useStyle } from '@app/engine/styleStore';
import { tl, ui, useLang } from '@app/i18n';
import { AddTopicDialog } from '@app/shell/AddTopicDialog';
import { BossFightDialog } from '@app/shell/BossFightDialog';
import { CategoryTree } from '@app/shell/CategoryTree';
import { Celebration } from '@app/shell/Celebration';
import { GenerationView } from '@app/shell/GenerationView';
import { LangSwitcher } from '@app/shell/LangSwitcher';
import { Markdown } from '@app/shell/Markdown';
import { StyleSelector } from '@app/shell/StyleSelector';
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

  const route = useRoute();
  const startGen = useGeneration((s) => s.start);
  const addTask = useGeneration((s) => s.tasks['add-topic']);

  const [showAdd, setShowAdd] = useState(false);
  const [showBossFight, setShowBossFight] = useState(false);

  const found = route.id ? findCatalogEntry(buildCatalog(topics), route.id) : null;
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
    });
  }

  return (
    <div className="app">
      <header className="header">
        <h1>🗡️ Java Interview Dungeon</h1>
        <div className="spacer" />
        <UsageBar />
        <LangSwitcher />
        <button className="accent" onClick={() => setShowAdd(true)}>
          {addTask?.status === 'running' ? ui('generating', lang) : ui('addTopic', lang)}
        </button>
      </header>

      <div className="home-main">
        {/* Left: question catalog tree */}
        <section className="panel home-tree-panel">
          <div className="panel-title">{ui('catalogTitle', lang)}</div>
          <div className="panel-body tree-body">
            <CategoryTree
              selectedId={entry?.id ?? null}
              onSelect={(e) => navigate(routeForQuestion(e.id))}
            />
          </div>
        </section>

        {/* Right: theory or generate action */}
        <section className="panel home-theory-panel">
          <div className="panel-title">
            {theoryReady ? tl(topic!.category, lang) : ui('theory', lang)}
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
                <Markdown className="markdown">{tl(topic!.explanation, lang)}</Markdown>
              </div>
            )}
          </div>
        </section>
      </div>

      {showAdd && <AddTopicDialog onClose={() => setShowAdd(false)} />}
      {showBossFight && <BossFightDialog onClose={() => setShowBossFight(false)} />}
      <Celebration />
    </div>
  );
}
