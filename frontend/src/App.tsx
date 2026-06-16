import { useEffect } from 'react';
import { buildCatalog, findCatalogEntry } from './catalog';
import { useGeneration } from './engine/generationStore';
import { navigate, routeForQuestion, useRoute } from './engine/router';
import { useStore } from './engine/store';
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

  useEffect(() => {
    loadTopics();
    // Reattach to any generation still running from before a reload.
    refreshActiveGenerations();
    // Begin polling Claude usage for the header meter (idempotent).
    startUsagePolling();
  }, [loadTopics, refreshActiveGenerations, startUsagePolling]);

  // Load the topic referenced by the URL once topics are known (also after a
  // reload or when following a deep link / cross-link).
  useEffect(() => {
    if (!route.id) return;
    const found = findCatalogEntry(buildCatalog(topics), route.id);
    const topicId = found?.entry.topicId;
    if (topicId && topic?.id !== topicId) selectTopic(topicId);
  }, [route.id, topics, topic?.id, selectTopic]);

  // The practice screen is only valid for a question that has a topic; otherwise
  // fall back to the question page (or home).
  useEffect(() => {
    if (route.view !== 'workspace' || topics.length === 0) return;
    const found = route.id ? findCatalogEntry(buildCatalog(topics), route.id) : null;
    if (!found?.entry.topicId) navigate(found && route.id ? routeForQuestion(route.id) : '/');
  }, [route, topics]);

  return route.view === 'workspace' ? <WorkspaceScreen /> : <HomeScreen />;
}
