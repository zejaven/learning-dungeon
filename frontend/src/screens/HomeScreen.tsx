import { useEffect, useMemo, useRef, useState } from 'react';
import { buildAllCatalogs, buildCatalog, compareEntries, findCatalogEntry } from '@app/catalog';
import { DOMAINS, domainById, domainOf } from '@app/domains';
import { useAi } from '@app/engine/aiStore';
import { useBulk } from '@app/engine/bulkStore';
import { useDomain } from '@app/engine/domainStore';
import { startAtomsGeneration, type BulkKind } from '@app/engine/api';
import { useGeneration } from '@app/engine/generationStore';
import { useReview } from '@app/engine/reviewStore';
import {
  navigate,
  routeForPractice,
  routeForQuestion,
  routeForReview,
  useRoute,
} from '@app/engine/router';
import { useStore } from '@app/engine/store';
import { useStyle } from '@app/engine/styleStore';
import { tl, ui, useLang } from '@app/i18n';
import { AddQuestionDialog } from '@app/shell/AddQuestionDialog';
import { AddTopicDialog } from '@app/shell/AddTopicDialog';
import { AiProviderSelector } from '@app/shell/AiProviderSelector';
import { AssistantDialog } from '@app/shell/AssistantDialog';
import { BossFightDialog } from '@app/shell/BossFightDialog';
import { BulkGenBar } from '@app/shell/BulkGenBar';
import { BulkGenDialog } from '@app/shell/BulkGenDialog';
import { CategoryTree } from '@app/shell/CategoryTree';
import { Celebration } from '@app/shell/Celebration';
import { GenerationView } from '@app/shell/GenerationView';
import { LangSwitcher } from '@app/shell/LangSwitcher';
import { LessonPanel } from '@app/shell/lesson/LessonPanel';
import { Markdown } from '@app/shell/Markdown';
import { SelectionAsk } from '@app/shell/SelectionAsk';
import { SettingsButton } from '@app/shell/SettingsButton';
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
  const topicsError = useStore((s) => s.topicsError);
  const runError = useStore((s) => s.runError);
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
  const [bulkKind, setBulkKind] = useState<BulkKind | null>(null);
  const [showBossFight, setShowBossFight] = useState(false);
  const [showAssistant, setShowAssistant] = useState(false);
  // Text selected in the reference/lesson to pre-quote in the assistant.
  const [assistantQuote, setAssistantQuote] = useState<string | null>(null);
  const contentRef = useRef<HTMLDivElement>(null);

  function askAboutSelection(text: string) {
    setAssistantQuote(text);
    setShowAssistant(true);
  }

  function closeAssistant() {
    setShowAssistant(false);
    setAssistantQuote(null);
  }

  const manualQuestions = useStore((s) => s.manualQuestions);
  const domainId = useDomain((s) => s.domainId);
  const setDomain = useDomain((s) => s.setDomain);
  const domain = domainById(domainId);

  // Bulk generation: the backend runs the loop; here we only know what is
  // missing in the active domain and whether a run exists (drives the strip).
  const selectedProvider = useAi((s) => s.selectedProvider);
  const bulkActive = useBulk((s) => !!s.status?.active);
  const bulkVisible = useBulk((s) => !!s.status?.run);
  const startBulkRun = useBulk((s) => s.start);
  const bulkMissing = useMemo(() => {
    // Walk the catalog in the exact order the tree renders it (categories in
    // catalog order, entries sorted like CategoryTree), so the generation loop
    // processes topics top to bottom as the user sees them.
    const catalog = buildCatalog(topics, manualQuestions, domainId);
    const ordered = catalog.flatMap((c) =>
      [...c.entries].sort(compareEntries).map((entry) => ({ categoryId: c.id, entry })),
    );
    const theory = ordered.filter(({ entry }) => !entry.topicId);
    const byId = new Map(topics.map((t) => [t.id, t]));
    const atoms = ordered
      .map(({ entry }) => (entry.topicId ? byId.get(entry.topicId) : undefined))
      .filter((t): t is NonNullable<typeof t> => !!t && !t.hasAtoms);
    return { theory, atoms };
  }, [topics, manualQuestions, domainId]);

  async function confirmBulk(
    kind: BulkKind,
    endTime: string,
    maxPercent: number,
    maxWeeklyPercent: number,
  ) {
    const items =
      kind === 'theory'
        ? bulkMissing.theory.map(({ categoryId: catId, entry: e }) => ({
            id: e.id,
            label: e.question,
            question: tl(e.question, lang),
            catalogId: e.id,
            categoryId: catId,
            difficulty: e.difficulty,
            style: useStyle.getState().instruction(),
            styleName: useStyle.getState().currentName(),
          }))
        : bulkMissing.atoms.map((t) => ({ id: t.id, label: t.title }));
    const ok = await startBulkRun({
      kind,
      provider: 'claude',
      domainId,
      endTime,
      maxPercent,
      maxWeeklyPercent,
      items,
    });
    if (ok) setBulkKind(null);
  }
  // Resolve the route against every domain's catalog so deep links and
  // cross-links to another domain's topics keep working.
  const found = route.id
    ? findCatalogEntry(buildAllCatalogs(topics, manualQuestions), route.id)
    : null;
  const entry = found?.entry ?? null;
  const categoryId = found?.categoryId ?? '';

  // A deep link into another domain switches the app to that domain.
  const entryTopic = entry?.topicId ? topics.find((t) => t.id === entry.topicId) : undefined;
  const entryDomain = entryTopic ? domainOf(entryTopic) : null;
  useEffect(() => {
    if (entryDomain && entryDomain !== domainId) setDomain(entryDomain);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [entryDomain]);

  const theoryReady = entry?.topicId && topic?.id === entry.topicId && !loadingTopic;
  const catalogKey = entry ? `catalog:${entry.id}` : '';
  const genTask = useGeneration((s) => (catalogKey ? s.tasks[catalogKey] : undefined));

  // "Learn by micro-actions" lesson: default view of a topic that has one.
  const atomsKey = topic ? `atoms:${topic.id}` : '';
  const atomsTask = useGeneration((s) => (atomsKey ? s.tasks[atomsKey] : undefined));
  const lessonReady = !!(theoryReady && topic!.hasAtoms);
  const showLesson = lessonReady && route.sub !== 'theory';
  const selectTopic = useStore((s) => s.selectTopic);

  // When a lesson generation finishes, re-fetch the topic so hasAtoms flips
  // and the lesson replaces the theory view.
  useEffect(() => {
    if (atomsTask?.status === 'done' && topic && !topic.hasAtoms) void selectTopic(topic.id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [atomsTask?.status]);

  // Review-pool badge for the header button.
  const reviewSummary = useReview((s) => s.summary);
  const loadReviewSummary = useReview((s) => s.loadSummary);
  useEffect(() => {
    void loadReviewSummary();
  }, [loadReviewSummary]);

  async function generateLesson() {
    if (!topic) return;
    try {
      const ref = await startAtomsGeneration(
        topic.id,
        useAi.getState().selectedProvider,
        useStore.getState().activeVersionNo,
      );
      useGeneration.getState().attach(ref.taskId, ref.key);
    } catch {
      /* the button stays; retry is cheap */
    }
  }

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
    <div className={`app${bulkVisible ? ' app-bulk' : ''}`}>
      <header className="header">
        <h1>
          {domain.icon} {tl(domain.title, lang)}
        </h1>
        {DOMAINS.length > 1 && (
          <div className="domain-tabs">
            {DOMAINS.map((d) => (
              <button
                key={d.id}
                className={`domain-tab${d.id === domainId ? ' active' : ''}`}
                title={tl(d.title, lang)}
                onClick={() => {
                  if (d.id !== domainId) {
                    setDomain(d.id);
                    navigate('/');
                  }
                }}
              >
                {d.icon}
              </button>
            ))}
          </div>
        )}
        <SettingsButton />
        <div className="spacer" />
        <AiProviderSelector />
        <UsageBar />
        <ThemeSwitcher />
        <LangSwitcher />
        <button onClick={() => navigate(routeForReview())}>
          {ui('review', lang)}
          {reviewSummary && reviewSummary.poolSize > 0 && (
            <span className="review-badge">{reviewSummary.poolSize}</span>
          )}
        </button>
        {domainId === 'java' && (
          <button className="accent" onClick={() => setShowAdd(true)}>
            {addTask?.status === 'running' ? ui('generating', lang) : ui('addTopic', lang)}
          </button>
        )}
      </header>

      <BulkGenBar />

      <div className="home-main">
        {/* Left: question catalog tree */}
        <section className="panel home-tree-panel">
          <div className="panel-title tree-panel-title">
            <span>{ui('catalogTitle', lang)}</span>
            <div className="tree-title-actions">
              {selectedProvider === 'claude' && bulkMissing.theory.length > 0 && (
                <button
                  className="tree-add-btn"
                  title={`${ui('bulkDialogTitleTheory', lang)} (${bulkMissing.theory.length})`}
                  disabled={bulkActive}
                  onClick={() => setBulkKind('theory')}
                >
                  📖
                </button>
              )}
              {selectedProvider === 'claude' && bulkMissing.atoms.length > 0 && (
                <button
                  className="tree-add-btn"
                  title={`${ui('bulkDialogTitleAtoms', lang)} (${bulkMissing.atoms.length})`}
                  disabled={bulkActive}
                  onClick={() => setBulkKind('atoms')}
                >
                  ✨
                </button>
              )}
              {domainId === 'java' && (
                <button
                  className="tree-add-btn"
                  title={ui('addQuestion', lang)}
                  onClick={() => setShowAddQuestion(true)}
                >
                  ＋
                </button>
              )}
            </div>
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
              <button
                className="theory-ask-btn"
                onClick={() => {
                  setAssistantQuote(null);
                  setShowAssistant(true);
                }}
              >
                {ui('askAI', lang)}
              </button>
            )}
          </div>
          <div className="panel-body" ref={contentRef}>
            {topicsError && <div className="output error">{topicsError}</div>}
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

            {entry?.topicId && !theoryReady && !runError && (
              <p className="home-hint">{ui('openingTheory', lang)}</p>
            )}
            {entry?.topicId && !theoryReady && runError && <div className="output error">{runError}</div>}

            {theoryReady && showLesson && <LessonPanel />}

            {theoryReady && !showLesson && (
              <div className="home-theory">
                <div className="home-theory-actions">
                  {lessonReady && (
                    <button className="primary" onClick={() => navigate(routeForQuestion(entry!.id))}>
                      {ui('backToLesson', lang)}
                    </button>
                  )}
                  {!lessonReady
                    && (atomsTask?.status === 'running' ? (
                      <span className="home-hint">{ui('generatingLesson', lang)}</span>
                    ) : (
                      <button className="accent" title={ui('lessonGenHint', lang)} onClick={generateLesson}>
                        {ui('generateLesson', lang)}
                      </button>
                    ))}
                  {/* Boss Fight moves inside the lesson once one exists. */}
                  {topic!.mode === 'theory' ? (
                    !lessonReady
                    && topic!.bossFight.length > 0 && (
                      <button className="accent" onClick={() => setShowBossFight(true)}>
                        {ui('bossFight', lang)}
                      </button>
                    )
                  ) : (
                    // Trace missions are visual toy tasks the lesson replaces;
                    // sql/challenge/structural practice is real code and keeps
                    // its entry point. Trace workspaces stay reachable by the
                    // direct #/q/<id>/practice URL.
                    topic!.mode !== 'trace' && (
                      <button className="primary" onClick={() => navigate(routeForPractice(entry!.id))}>
                        {ui('goToPractice', lang)}
                      </button>
                    )
                  )}
                </div>

                {!lessonReady && atomsTask?.status === 'running' && <GenerationView taskKey={atomsKey} />}

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

                <Markdown className="markdown" assetBase={`/api/topics/${topic!.id}/assets`}>
                  {tl(activeExplanation, lang)}
                </Markdown>
              </div>
            )}
          </div>
        </section>
      </div>

      {bulkKind && (
        <BulkGenDialog
          kind={bulkKind}
          count={bulkKind === 'theory' ? bulkMissing.theory.length : bulkMissing.atoms.length}
          onConfirm={(endTime, maxPercent, maxWeeklyPercent) =>
            void confirmBulk(bulkKind, endTime, maxPercent, maxWeeklyPercent)
          }
          onClose={() => setBulkKind(null)}
        />
      )}
      {showAdd && <AddTopicDialog onClose={() => setShowAdd(false)} />}
      {showAddQuestion && <AddQuestionDialog onClose={() => setShowAddQuestion(false)} />}
      {showBossFight && <BossFightDialog onClose={() => setShowBossFight(false)} />}
      {theoryReady && <SelectionAsk containerRef={contentRef} onAsk={askAboutSelection} />}
      {showAssistant && <AssistantDialog onClose={closeAssistant} quote={assistantQuote} />}
      <Celebration />
    </div>
  );
}
