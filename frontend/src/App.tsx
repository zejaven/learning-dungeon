import { useEffect } from 'react';
import { buildCatalog, findCatalogEntry } from './catalog';
import { useGeneration } from './engine/generationStore';
import { navigate, routeForQuestion, useRoute } from './engine/router';
import { useStore } from './engine/store';
import { useStyle } from './engine/styleStore';
import { useUsage } from './engine/usageStore';
import { HomeScreen } from './screens/HomeScreen';
import { WorkspaceScreen } from './screens/WorkspaceScreen';

export function App() {
  const route = useRoute();
  const loadTopics = useStore((s) => s.loadTopics);
  const topics = useStore((s) => s.topics);
  const topic = useStore((s) => s.topic);
  const selectTopic = useStore((s) => s.selectTopic);
  const refreshActiveGenerations = useGeneration((s) => s.refreshActive);
  const startUsagePolling = useUsage((s) => s.start);
  const loadStyles = useStyle((s) => s.load);

  useEffect(() => {
    loadTopics();
    // Reattach to any generation still running from before a reload.
    refreshActiveGenerations();
    // Begin polling Claude usage for the header meter (idempotent).
    startUsagePolling();
    // Load the user's saved generation styles into the dropdown.
    loadStyles();
  }, [loadTopics, refreshActiveGenerations, startUsagePolling, loadStyles]);

  // Load the topic referenced by the URL once topics are known (also after a
  // reload or when following a deep link / cross-link).
  useEffect(() => {
    if (!route.id) return;
    const found = findCatalogEntry(buildCatalog(topics), route.id);
    const topicId = found?.entry.topicId;
    if (topicId && topic?.id !== topicId) selectTopic(topicId);
  }, [route.id, topics, topic?.id, selectTopic]);

  // The practice screen is only valid for a question with a non-theory topic;
  // otherwise fall back to the question page (or home). Theory topics have no
  // practice — their Boss Fight lives on the home/theory screen.
  useEffect(() => {
    if (route.view !== 'workspace' || topics.length === 0) return;
    const found = route.id ? findCatalogEntry(buildCatalog(topics), route.id) : null;
    const topicId = found?.entry.topicId;
    const summary = topicId ? topics.find((t) => t.id === topicId) : undefined;
    if (!topicId || summary?.mode === 'theory') {
      navigate(found && route.id ? routeForQuestion(route.id) : '/');
    }
  }, [route, topics]);

  return route.view === 'workspace' ? <WorkspaceScreen /> : <HomeScreen />;
}
