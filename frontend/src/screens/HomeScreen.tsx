import { useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import type { CatalogEntry } from '@app/catalog';
import { useStore } from '@app/engine/store';
import { tl, ui, useLang } from '@app/i18n';
import { AddTopicDialog, type GenerateContext } from '@app/shell/AddTopicDialog';
import { CategoryTree } from '@app/shell/CategoryTree';
import { LangSwitcher } from '@app/shell/LangSwitcher';

interface Selection {
  entry: CatalogEntry;
  categoryId: string;
}

interface AddState {
  question: string;
  context?: GenerateContext;
}

/**
 * Home / catalog screen: a tree of interview questions on the left, theory (or a
 * "generate theory" action) on the right. Existing topics open into the code
 * workspace; questions without theory hand off to the generation flow, passing
 * the question's category id and difficulty so the new topic lands in place.
 */
export function HomeScreen() {
  const lang = useLang((s) => s.lang);
  const topic = useStore((s) => s.topic);
  const loadingTopic = useStore((s) => s.loadingTopic);
  const selectTopic = useStore((s) => s.selectTopic);
  const setView = useStore((s) => s.setView);

  const [selected, setSelected] = useState<Selection | null>(null);
  const [add, setAdd] = useState<AddState | null>(null);

  function handleSelect(entry: CatalogEntry, categoryId: string) {
    setSelected({ entry, categoryId });
    if (entry.topicId) selectTopic(entry.topicId);
  }

  const entry = selected?.entry ?? null;
  const theoryReady = entry?.topicId && topic?.id === entry.topicId && !loadingTopic;

  function generateForSelected() {
    if (!selected) return;
    setAdd({
      question: tl(selected.entry.question, lang),
      context: {
        catalogId: selected.entry.id,
        categoryId: selected.categoryId,
        difficulty: selected.entry.difficulty,
      },
    });
  }

  return (
    <div className="app">
      <header className="header">
        <h1>🗡️ Java Interview Dungeon</h1>
        <div className="spacer" />
        <LangSwitcher />
        <button className="accent" onClick={() => setAdd({ question: '' })}>
          {ui('addTopic', lang)}
        </button>
      </header>

      <div className="home-main">
        {/* Left: question catalog tree */}
        <section className="panel home-tree-panel">
          <div className="panel-title">{ui('catalogTitle', lang)}</div>
          <div className="panel-body tree-body">
            <CategoryTree selectedId={entry?.id ?? null} onSelect={handleSelect} />
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
                <p className="home-hint">{ui('noTheoryYet', lang)}</p>
                <button className="primary" onClick={generateForSelected}>
                  {ui('generateTheory', lang)}
                </button>
              </div>
            )}

            {entry?.topicId && !theoryReady && <p className="home-hint">{ui('openingTheory', lang)}</p>}

            {theoryReady && (
              <div className="home-theory">
                <div className="home-theory-actions">
                  <button className="primary" onClick={() => setView('workspace')}>
                    {ui('goToPractice', lang)}
                  </button>
                </div>
                <div className="markdown">
                  <ReactMarkdown remarkPlugins={[remarkGfm]}>
                    {tl(topic!.explanation, lang)}
                  </ReactMarkdown>
                </div>
              </div>
            )}
          </div>
        </section>
      </div>

      {add !== null && (
        <AddTopicDialog
          initialQuestion={add.question}
          context={add.context}
          onClose={() => setAdd(null)}
        />
      )}
    </div>
  );
}
