import { useEffect } from 'react';
import { buildAllCatalogs, findCatalogEntry } from './catalog';
import { useAi } from './engine/aiStore';
import { useGeneration } from './engine/generationStore';
import { navigate, routeForQuestion, useRoute } from './engine/router';
import { useStore } from './engine/store';
import { useStyle } from './engine/styleStore';
import { useSystem } from './engine/systemStore';
import { useUsage } from './engine/usageStore';
import { HomeScreen } from './screens/HomeScreen';
import { ReviewScreen } from './screens/ReviewScreen';
import { WorkspaceScreen } from './screens/WorkspaceScreen';
import { UpdatingOverlay } from './shell/UpdatingOverlay';

export function App() {
  const route = useRoute();
  const loadTopics = useStore((s) => s.loadTopics);
  const loadQuestions = useStore((s) => s.loadQuestions);
  const topics = useStore((s) => s.topics);
  const manualQuestions = useStore((s) => s.manualQuestions);
  const topic = useStore((s) => s.topic);
  const selectTopic = useStore((s) => s.selectTopic);
  const refreshActiveGenerations = useGeneration((s) => s.refreshActive);
  const startUsagePolling = useUsage((s) => s.start);
  const loadStyles = useStyle((s) => s.load);
  const loadAiProviders = useAi((s) => s.load);
  const startSystemPolling = useSystem((s) => s.start);

  useEffect(() => {
    loadTopics();
    // Hand-added catalog questions.
    loadQuestions();
    // Reattach to any generation still running from before a reload.
    refreshActiveGenerations();
    // Begin polling selected-provider usage/status for the header meter (idempotent).
    startUsagePolling();
    // Poll app self-update status for the settings gear badge (idempotent).
    startSystemPolling();
    // Load the user's saved generation styles into the dropdown.
    loadStyles();
    // Detect available AI CLIs and choose a provider before AI actions run.
    loadAiProviders().then(() => useUsage.getState().refresh());
  }, [loadTopics, loadQuestions, refreshActiveGenerations, startUsagePolling, startSystemPolling, loadStyles, loadAiProviders]);

  // Load the topic referenced by the URL once topics are known (also after a
  // reload or when following a deep link / cross-link).
  useEffect(() => {
    if (!route.id) return;
    const found = findCatalogEntry(buildAllCatalogs(topics, manualQuestions), route.id);
    const topicId = found?.entry.topicId;
    if (topicId && topic?.id !== topicId) selectTopic(topicId);
  }, [route.id, topics, manualQuestions, topic?.id, selectTopic]);

  // The practice screen is only valid for a question with a non-theory topic;
  // otherwise fall back to the question page (or home). Theory topics have no
  // practice — their Boss Fight lives on the home/theory screen.
  useEffect(() => {
    if (route.view !== 'workspace' || topics.length === 0) return;
    const found = route.id ? findCatalogEntry(buildAllCatalogs(topics, manualQuestions), route.id) : null;
    const topicId = found?.entry.topicId;
    const summary = topicId ? topics.find((t) => t.id === topicId) : undefined;
    if (!topicId || summary?.mode === 'theory') {
      navigate(found && route.id ? routeForQuestion(route.id) : '/');
    }
  }, [route, topics, manualQuestions]);

  const screen =
    route.view === 'workspace' ? (
      <WorkspaceScreen />
    ) : route.view === 'review' ? (
      <ReviewScreen />
    ) : (
      <HomeScreen />
    );

  return (
    <>
      {screen}
      <UpdatingOverlay />
    </>
  );
}
